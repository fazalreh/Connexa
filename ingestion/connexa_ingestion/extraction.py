"""Turns announcement text into structured event fields.

Extraction is deliberately split in two. :func:`parse_extraction` is pure and
holds every decision about what a usable result looks like; the network call is
a thin wrapper around it. A model's output is untrusted input, so the rules that
decide whether to trust a result are the part worth testing exhaustively, and
they must be testable without a provider.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
import re
from typing import Any, Mapping, Protocol
from urllib import error, request


_MAX_EVENT_DURATION = timedelta(days=30)
_CODE_FENCE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)


class ExtractionError(RuntimeError):
    """Raised when the provider could not be reached or returned nothing usable."""


@dataclass(frozen=True, slots=True)
class EventExtraction:
    """What an announcement was understood to describe.

    ``is_event`` false means the text was not an event announcement at all, which
    is a normal outcome rather than a failure: mailboxes receive newsletters,
    receipts and replies alongside real announcements.
    """

    is_event: bool
    title: str | None = None
    starts_at: datetime | None = None
    ends_at: datetime | None = None
    location_text: str | None = None
    organizer_text: str | None = None
    category: str | None = None

    @property
    def is_usable(self) -> bool:
        """A result worth storing: an event with a title and a start time."""
        return self.is_event and bool(self.title) and self.starts_at is not None


class TextModel(Protocol):
    """The provider call, kept behind an interface so tests need no network."""

    def generate(self, instruction: str, content: str) -> str: ...


EXTRACTION_INSTRUCTION = """\
You read announcement emails and decide whether each one announces a real event \
that people can attend.

Reply with a single JSON object and nothing else. Use exactly these keys:
  "is_event"      boolean
  "title"         short event name, or null
  "starts_at"     ISO 8601 with timezone offset, or null
  "ends_at"       ISO 8601 with timezone offset, or null
  "location"      venue or address as written, or null
  "organizer"     organising body as written, or null
  "category"      one short word such as Technology, Careers, Sports, Arts, or null

Rules:
- Set is_event false for newsletters, receipts, password resets, replies, and \
anything with no attendable occasion. Set every other field to null in that case.
- Never invent a date, a venue or an organiser. If the text does not state it, use null.
- If a time is given without a zone, assume Asia/Karachi (+05:00).
- If only a start time is given, set ends_at to null rather than guessing a duration.
"""


def parse_extraction(payload: str, *, now: datetime | None = None) -> EventExtraction:
    """Validates a model reply into an :class:`EventExtraction`.

    Anything malformed degrades to "not an event" rather than raising. A single
    unparseable reply must not stop a run, and a half-understood announcement is
    more damaging in the catalog than an ignored one.
    """
    document = _json_object(payload)
    if document is None or not bool(document.get("is_event")):
        return EventExtraction(is_event=False)

    title = _clean_text(document.get("title"), maximum=160)
    starts_at = _parse_moment(document.get("starts_at"))
    ends_at = _parse_moment(document.get("ends_at"))

    if starts_at is None:
        # A start time is what makes an event actionable; without one there is
        # nothing to show on a calendar or to order a listing by.
        return EventExtraction(is_event=False)

    if ends_at is not None and ends_at <= starts_at:
        ends_at = None
    if ends_at is not None and ends_at - starts_at > _MAX_EVENT_DURATION:
        # A multi-month span is a misread date far more often than a real event.
        ends_at = None

    reference = now or datetime.now(timezone.utc)
    if starts_at < reference - timedelta(days=365):
        # Announcements about the distant past are almost always a parsed
        # footer date rather than the occasion itself.
        return EventExtraction(is_event=False)

    return EventExtraction(
        is_event=True,
        title=title,
        starts_at=starts_at,
        ends_at=ends_at,
        location_text=_clean_text(document.get("location"), maximum=160),
        organizer_text=_clean_text(document.get("organizer"), maximum=160),
        category=_clean_text(document.get("category"), maximum=80),
    )


def _json_object(payload: str) -> Mapping[str, Any] | None:
    """Finds the JSON object in a reply that may be fenced or prefixed with prose."""
    if not payload:
        return None
    candidates = [payload]
    fenced = _CODE_FENCE.search(payload)
    if fenced:
        candidates.insert(0, fenced.group(1))
    start, end = payload.find("{"), payload.rfind("}")
    if start != -1 and end > start:
        candidates.append(payload[start : end + 1])
    for candidate in candidates:
        try:
            document = json.loads(candidate)
        except (ValueError, TypeError):
            continue
        if isinstance(document, dict):
            return document
    return None


def _clean_text(value: Any, *, maximum: int) -> str | None:
    if not isinstance(value, str):
        return None
    text = " ".join(value.split()).strip()
    if not text or text.casefold() in {"null", "none", "n/a", "unknown"}:
        return None
    return text[:maximum]


def _parse_moment(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip().replace("Z", "+00:00")
    try:
        moment = datetime.fromisoformat(text)
    except ValueError:
        return None
    # A naive timestamp is treated as the configured local zone, matching the
    # instruction given to the model.
    return moment if moment.tzinfo else moment.replace(tzinfo=timezone(timedelta(hours=5)))


class GeminiTextModel:
    """Calls the provider's text endpoint over the standard library only."""

    _ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

    def __init__(self, api_key: str, model: str, timeout_seconds: int = 45) -> None:
        if not api_key or not model:
            raise ValueError("api_key and model are required")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds

    def generate(self, instruction: str, content: str) -> str:
        body = json.dumps(
            {
                "systemInstruction": {"parts": [{"text": instruction}]},
                "contents": [{"role": "user", "parts": [{"text": content}]}],
                # Extraction should be repeatable: the same email must not yield a
                # different date on a second run.
                "generationConfig": {"temperature": 0.0, "maxOutputTokens": 1024},
            }
        ).encode("utf-8")
        # The key travels as a header so it cannot be captured in a proxy log.
        message = request.Request(
            self._ENDPOINT.format(model=self._model),
            data=body,
            headers={"Content-Type": "application/json", "x-goog-api-key": self._api_key},
        )
        try:
            with request.urlopen(message, timeout=self._timeout) as response:
                document = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exception:
            raise ExtractionError(f"provider returned status {exception.code}") from None
        except (error.URLError, TimeoutError, ValueError) as exception:
            raise ExtractionError("provider could not be reached") from exception

        parts = (
            document.get("candidates", [{}])[0]
            .get("content", {})
            .get("parts", [])
        )
        return "".join(part.get("text", "") for part in parts).strip()


class EventExtractor:
    """Applies the model to one announcement."""

    def __init__(self, text_model: TextModel) -> None:
        self._text_model = text_model

    def extract(self, title: str, body_text: str) -> EventExtraction:
        content = f"SUBJECT\n{title}\n\nBODY\n{body_text}"
        try:
            reply = self._text_model.generate(EXTRACTION_INSTRUCTION, content)
        except ExtractionError:
            # Treated as "not an event" so one provider hiccup skips a message
            # instead of ending the run.
            return EventExtraction(is_event=False)
        return parse_extraction(reply)
