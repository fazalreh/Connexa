"""IMAP announcement source.

The mailbox is opened read-only. Marking messages as seen would make the
mailbox itself the record of what has been processed, which breaks the moment a
run fails part-way through or a person opens the inbox. Repeat delivery is
handled by the idempotency key instead, so re-reading a message is harmless.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from datetime import datetime, timezone
from email import policy
from email.message import EmailMessage
from email.parser import BytesParser
from email.utils import parseaddr, parsedate_to_datetime
from html import unescape
import imaplib
import re
from typing import Protocol

from .config import IngestionSettings
from .models import CandidateValidationError, EventCandidate


SOURCE_SYSTEM = "imap"
_MAX_BODY_CHARS = 20_000


class MailboxClient(Protocol):
    """The slice of IMAP this source needs, so tests need no live server."""

    def select(self, mailbox: str, readonly: bool = ...) -> tuple[str, list[bytes]]: ...

    def search(self, charset: str | None, *criteria: str) -> tuple[str, list[bytes]]: ...

    def fetch(self, message_set: str, message_parts: str) -> tuple[str, list]: ...

    def logout(self) -> tuple[str, list[bytes]]: ...


class ImapCandidateSource:
    """Reads recent announcements from an approved mailbox."""

    def __init__(
        self,
        client: MailboxClient,
        settings: IngestionSettings,
        folder: str = "INBOX",
    ) -> None:
        self._client = client
        self._settings = settings
        self._folder = folder

    def collect(self, *, limit: int) -> Iterable[EventCandidate]:
        if limit <= 0:
            return []
        return list(self._collect(limit))

    def _collect(self, limit: int) -> Iterator[EventCandidate]:
        self._client.select(self._folder, readonly=True)
        status, payload = self._client.search(None, "ALL")
        if status != "OK" or not payload:
            return

        identifiers = payload[0].split()
        # Newest first: a bounded run should surface the most recent
        # announcements rather than whatever happens to be oldest.
        for identifier in reversed(identifiers):
            if limit <= 0:
                return
            candidate = self._candidate_for(identifier)
            if candidate is not None:
                limit -= 1
                yield candidate

    def _candidate_for(self, identifier: bytes) -> EventCandidate | None:
        status, payload = self._client.fetch(identifier.decode("ascii"), "(RFC822)")
        if status != "OK" or not payload:
            return None
        raw = _first_message_bytes(payload)
        if raw is None:
            return None

        message = BytesParser(policy=policy.default).parsebytes(raw)
        sender = parseaddr(message.get("From", ""))[1].strip().casefold()
        # The allowlist is the trust boundary. Anyone who learns the address can
        # send to it, so an unapproved sender is discarded before extraction.
        if not self._settings.is_allowed_sender(sender):
            return None

        subject = (message.get("Subject") or "").strip()
        body = _plain_text_body(message)
        if not subject or not body:
            return None

        try:
            return EventCandidate(
                source_system=SOURCE_SYSTEM,
                source_record_id=_message_id(message, identifier),
                sender_address=sender,
                received_at=_received_at(message),
                title=subject[:180],
                body_text=body[:_MAX_BODY_CHARS],
            )
        except CandidateValidationError:
            # A malformed announcement is skipped, never allowed to end a run:
            # one bad message must not block every message behind it.
            return None


def _first_message_bytes(payload: list) -> bytes | None:
    for part in payload:
        if isinstance(part, tuple) and len(part) >= 2 and isinstance(part[1], (bytes, bytearray)):
            return bytes(part[1])
    return None


def _message_id(message: EmailMessage, fallback: bytes) -> str:
    """Prefer the RFC 5322 Message-ID: sequence numbers are reassigned."""
    header = (message.get("Message-ID") or "").strip()
    return header if header else f"seq-{fallback.decode('ascii', 'replace')}"


def _received_at(message: EmailMessage) -> datetime:
    raw = message.get("Date")
    if raw:
        try:
            parsed = parsedate_to_datetime(raw)
        except (TypeError, ValueError):
            parsed = None
        if parsed is not None:
            return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    return datetime.now(timezone.utc)


def _plain_text_body(message: EmailMessage) -> str:
    """Extracts readable text, preferring text/plain over an HTML alternative.

    An HTML-only announcement is reduced to text before it leaves this module.
    Passing markup downstream would put tag soup in the extractor's input and in
    anything that later displays the body.
    """
    is_html = False
    if message.is_multipart():
        best = message.get_body(preferencelist=("plain",))
        if best is None:
            best = message.get_body(preferencelist=("html",))
            is_html = best is not None
        if best is None:
            return ""
        content = best.get_content()
    else:
        content = message.get_content()
        is_html = (message.get_content_subtype() or "").casefold() == "html"
    if not isinstance(content, str):
        return ""
    if is_html:
        content = _text_from_html(content)
    else:
        # A text/plain part is not necessarily clean. Senders commonly generate it
        # from the HTML and leave conditional-comment markup behind, which is never
        # content a reader or an extractor should see.
        content = _strip_comments(content)
    return "\n".join(line.rstrip() for line in content.splitlines()).strip()


def _strip_comments(text: str) -> str:
    """Removes HTML comments and the conditional-comment fragments around them.

    Order matters. Balanced comments go first, because a downlevel-hidden block
    such as ``<!--[if mso]>...<![endif]-->`` is one comment whose content is not
    meant to be read: clearing the trailing ``<![endif]-->`` first would destroy
    the terminator the comment pattern needs and reveal the body instead.

    Downlevel-revealed forms like ``<!--[if !mso]><!-->`` are deliberately
    unbalanced, so whatever survives the first pass is swept up afterwards.
    """
    without_comments = re.sub(r"<!--.*?-->", " ", text, flags=re.DOTALL)
    without_endif = re.sub(r"<!\[endif]\s*-*>", " ", without_comments, flags=re.IGNORECASE)
    without_downlevel = re.sub(r"<!\[if\b[^\]]*]\s*>", " ", without_endif, flags=re.IGNORECASE)
    # Any remaining bare delimiter is markup noise, never content.
    return without_downlevel.replace("<!--", " ").replace("-->", " ")


def _text_from_html(html: str) -> str:
    """Strips tags and unescapes entities, keeping block breaks as newlines."""
    # Comments must go before tag removal. A comment body may itself contain '>',
    # so the tag pattern would end one early and leak the remainder as text.
    without_comments = _strip_comments(html)
    without_invisible = re.sub(
        r"<(script|style)\b.*?</\1>", " ", without_comments, flags=re.IGNORECASE | re.DOTALL
    )
    with_breaks = re.sub(
        r"(?i)<\s*(br\s*/?|/p|/div|/li|/tr|/h[1-6])\s*>", "\n", without_invisible
    )
    text = re.sub(r"<[^>]+>", "", with_breaks)
    text = unescape(text)
    text = re.sub(r"[ \t ]+", " ", text)
    return re.sub(r"\n{3,}", "\n\n", text)


def connect(settings: IngestionSettings, host: str, port: int = 993) -> imaplib.IMAP4_SSL:
    """Opens an authenticated TLS connection using configured credentials."""
    if not settings.mail_username or not settings.mail_password:
        raise ValueError("mail credentials are not configured")
    client = imaplib.IMAP4_SSL(host, port)
    client.login(settings.mail_username, settings.mail_password)
    return client
