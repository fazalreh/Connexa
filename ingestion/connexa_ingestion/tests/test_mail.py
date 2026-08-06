"""Tests for the IMAP announcement source."""

from __future__ import annotations

from datetime import timezone
from email.message import EmailMessage
import unittest

from ..config import IngestionSettings
from ..mail import ImapCandidateSource


def _settings(domains: str = "lums.edu.pk", senders: str = "") -> IngestionSettings:
    return IngestionSettings.from_environment(
        {
            "CONNEXA_ANNOUNCEMENT_MAILBOX": "announce@example.com",
            "CONNEXA_MAIL_USERNAME": "announce@example.com",
            "CONNEXA_MAIL_PASSWORD": "app-password",
            "CONNEXA_INGESTION_ALLOWED_DOMAINS": domains,
            "CONNEXA_INGESTION_ALLOWED_SENDERS": senders,
        }
    )


def _message(
    sender: str = "events@lums.edu.pk",
    subject: str = "Robotics Workshop on Friday",
    body: str = "Join us in the Main Auditorium at 5pm.",
    date: str | None = "Tue, 1 Jul 2026 09:00:00 +0500",
    message_id: str | None = "<abc123@lums.edu.pk>",
    html_only: bool = False,
) -> bytes:
    message = EmailMessage()
    message["From"] = sender
    message["Subject"] = subject
    if date:
        message["Date"] = date
    if message_id:
        message["Message-ID"] = message_id
    if html_only:
        message.set_content(
            f"<html><body><style>p{{color:red}}</style>"
            f"<p>{body}</p><p>Tickets &amp; passes at the desk.</p></body></html>",
            subtype="html",
        )
    else:
        message.set_content(body)
    return message.as_bytes()


class FakeMailbox:
    """Minimal stand-in for an IMAP connection."""

    def __init__(self, messages: list[bytes]) -> None:
        self._messages = messages
        self.selected_readonly: bool | None = None

    def select(self, mailbox, readonly=False):
        self.selected_readonly = readonly
        return "OK", [str(len(self._messages)).encode()]

    def search(self, charset, *criteria):
        ids = b" ".join(str(i + 1).encode() for i in range(len(self._messages)))
        return "OK", [ids]

    def fetch(self, message_set, message_parts):
        index = int(message_set) - 1
        if index < 0 or index >= len(self._messages):
            return "NO", []
        return "OK", [(b"header", self._messages[index])]

    def logout(self):
        return "OK", [b""]


class ImapCandidateSourceTests(unittest.TestCase):
    def test_reads_an_approved_announcement(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message()]), _settings())

        candidates = list(source.collect(limit=10))

        self.assertEqual(1, len(candidates))
        candidate = candidates[0]
        self.assertEqual("events@lums.edu.pk", candidate.sender_address)
        self.assertEqual("Robotics Workshop on Friday", candidate.title)
        self.assertIn("Main Auditorium", candidate.body_text)
        self.assertEqual("<abc123@lums.edu.pk>", candidate.source_record_id)

    def test_mailbox_is_never_modified(self) -> None:
        # Marking messages seen would make the mailbox the processing record,
        # which breaks whenever a run fails part-way through.
        mailbox = FakeMailbox([_message()])
        ImapCandidateSource(mailbox, _settings()).collect(limit=10)

        self.assertTrue(mailbox.selected_readonly)

    def test_sender_outside_the_allowlist_is_discarded(self) -> None:
        source = ImapCandidateSource(
            FakeMailbox([_message(sender="stranger@example.com")]), _settings()
        )

        self.assertEqual([], list(source.collect(limit=10)))

    def test_exact_address_allowlist_is_honoured(self) -> None:
        settings = _settings(domains="", senders="events@partner.org")
        source = ImapCandidateSource(
            FakeMailbox([_message(sender="events@partner.org")]), settings
        )

        self.assertEqual(1, len(list(source.collect(limit=10))))

    def test_empty_allowlist_ingests_nothing(self) -> None:
        # The safe default: an unconfigured allowlist must not accept anything.
        source = ImapCandidateSource(FakeMailbox([_message()]), _settings(domains=""))

        self.assertEqual([], list(source.collect(limit=10)))

    def test_limit_is_respected(self) -> None:
        source = ImapCandidateSource(
            FakeMailbox([_message(message_id=f"<{i}@lums.edu.pk>") for i in range(5)]),
            _settings(),
        )

        self.assertEqual(2, len(list(source.collect(limit=2))))

    def test_zero_limit_reads_nothing(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message()]), _settings())

        self.assertEqual([], list(source.collect(limit=0)))

    def test_message_without_subject_is_skipped(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message(subject="")]), _settings())

        self.assertEqual([], list(source.collect(limit=10)))

    def test_html_only_body_is_reduced_to_text(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message(html_only=True)]), _settings())

        candidates = list(source.collect(limit=10))

        self.assertEqual(1, len(candidates))
        body = candidates[0].body_text
        self.assertIn("Main Auditorium", body)
        # Markup, styling and escaped entities must not reach downstream consumers.
        self.assertNotIn("<p>", body)
        self.assertNotIn("color:red", body)
        self.assertIn("Tickets & passes", body)

    def test_html_comments_are_removed_entirely(self) -> None:
        # Real newsletters carry Outlook conditional comments. A comment body can
        # contain '>', so removing tags first leaks the remainder as visible text.
        message = EmailMessage()
        message["From"] = "events@lums.edu.pk"
        message["Subject"] = "Newsletter"
        message["Message-ID"] = "<news@lums.edu.pk>"
        message.set_content(
            "<html><body><!--[if !mso]><i>hidden</i><![endif]-->"
            "<p>Doors open at six.</p></body></html>",
            subtype="html",
        )
        source = ImapCandidateSource(FakeMailbox([message.as_bytes()]), _settings())

        body = list(source.collect(limit=10))[0].body_text

        self.assertIn("Doors open at six.", body)
        self.assertNotIn("mso", body)
        self.assertNotIn("endif", body)
        self.assertNotIn("hidden", body)

    def test_conditional_comment_markup_is_stripped_from_plain_text(self) -> None:
        # Observed in real mail: senders generate the text/plain part from HTML
        # and leave downlevel conditional markup in it.
        noisy = (
            "Keep track of your account\n\n<!--[if !mso]><!-->\n\n"
            "<![endif]-->\n\nDoors open at six."
        )
        source = ImapCandidateSource(FakeMailbox([_message(body=noisy)]), _settings())

        body = list(source.collect(limit=10))[0].body_text

        self.assertIn("Doors open at six.", body)
        self.assertNotIn("<!--", body)
        self.assertNotIn("mso", body)
        self.assertNotIn("endif", body)

    def test_plain_text_is_preferred_over_an_html_alternative(self) -> None:
        message = EmailMessage()
        message["From"] = "events@lums.edu.pk"
        message["Subject"] = "Multipart Announcement"
        message["Message-ID"] = "<multi@lums.edu.pk>"
        message.set_content("Plain version with the venue.")
        message.add_alternative("<html><body><p>HTML version</p></body></html>", subtype="html")
        source = ImapCandidateSource(FakeMailbox([message.as_bytes()]), _settings())

        body = list(source.collect(limit=10))[0].body_text

        self.assertEqual("Plain version with the venue.", body)

    def test_missing_date_falls_back_to_now(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message(date=None)]), _settings())

        candidate = list(source.collect(limit=10))[0]

        self.assertIsNotNone(candidate.received_at.tzinfo)

    def test_missing_message_id_falls_back_to_sequence(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message(message_id=None)]), _settings())

        candidate = list(source.collect(limit=10))[0]

        self.assertTrue(candidate.source_record_id.startswith("seq-"))

    def test_received_at_is_timezone_aware(self) -> None:
        source = ImapCandidateSource(FakeMailbox([_message()]), _settings())

        candidate = list(source.collect(limit=10))[0]

        self.assertEqual(
            "2026-07-01T04:00:00+00:00",
            candidate.received_at.astimezone(timezone.utc).isoformat(),
        )

    def test_one_bad_message_does_not_block_the_rest(self) -> None:
        messages = [
            _message(subject="", message_id="<bad@lums.edu.pk>"),
            _message(subject="Good One", message_id="<good@lums.edu.pk>"),
        ]
        source = ImapCandidateSource(FakeMailbox(messages), _settings())

        titles = [candidate.title for candidate in source.collect(limit=10)]

        self.assertEqual(["Good One"], titles)


if __name__ == "__main__":
    unittest.main()
