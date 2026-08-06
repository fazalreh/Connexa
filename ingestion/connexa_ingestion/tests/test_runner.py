"""Tests for the ingestion run.

The run is exercised with stand-ins for the mailbox, extractor and API so the
outcomes that matter — what is counted, and what stops a run — can be asserted
without a network.
"""

from __future__ import annotations

from datetime import datetime, timezone
from email.message import EmailMessage
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from ..config import IngestionSettings
from ..extraction import EventExtraction
from ..runner import RunnerSettings, run_once
from ..store import SubmissionError
from ..workflow import SubmissionStatus


def _message(sender="events@lums.edu.pk", subject="Robotics Workshop", ident="<a@lums.edu.pk>"):
    message = EmailMessage()
    message["From"] = sender
    message["Subject"] = subject
    message["Message-ID"] = ident
    message["Date"] = "Tue, 1 Jul 2026 09:00:00 +0500"
    message.set_content("Workshop on Friday 10 July 2026 at 5pm in the Main Auditorium.")
    return message.as_bytes()


class FakeMailbox:
    def __init__(self, messages):
        self._messages = messages

    def select(self, mailbox, readonly=False):
        return "OK", [str(len(self._messages)).encode()]

    def search(self, charset, *criteria):
        return "OK", [b" ".join(str(i + 1).encode() for i in range(len(self._messages)))]

    def fetch(self, message_set, message_parts):
        return "OK", [(b"h", self._messages[int(message_set) - 1])]

    def logout(self):
        return "OK", [b""]


USABLE = EventExtraction(
    is_event=True,
    title="Robotics Workshop",
    starts_at=datetime(2026, 7, 10, 17, 0, tzinfo=timezone.utc),
    location_text="Main Auditorium",
    category="Technology",
)
NOT_AN_EVENT = EventExtraction(is_event=False)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.lock = Path(self._temp.name) / "run.lock"
        self.settings = IngestionSettings.from_environment(
            {
                "CONNEXA_ANNOUNCEMENT_MAILBOX": "announce@example.com",
                "CONNEXA_MAIL_USERNAME": "announce@example.com",
                "CONNEXA_MAIL_PASSWORD": "app-password",
                "CONNEXA_INGESTION_ALLOWED_DOMAINS": "lums.edu.pk",
                "CONNEXA_INGESTION_LOCK_FILE": str(self.lock),
            }
        )
        self.runner_settings = RunnerSettings(
            api_base_url="http://localhost:8080/",
            api_token="token",
            ai_api_key="key",
            ai_model="model",
            mail_host="imap.example.com",
            mail_port=993,
            mail_folder="INBOX",
        )

    def _run(self, messages, extractions, submit):
        with mock.patch("connexa_ingestion.runner.connect", return_value=FakeMailbox(messages)), \
             mock.patch("connexa_ingestion.runner.GeminiTextModel"), \
             mock.patch("connexa_ingestion.runner.EventExtractor") as extractor, \
             mock.patch("connexa_ingestion.runner.ConnexaApiCandidateStore") as store:
            extractor.return_value.extract.side_effect = extractions
            store.return_value.submit_extracted.side_effect = submit
            return run_once(self.settings, self.runner_settings)

    def test_accepts_an_understood_announcement(self):
        report = self._run([_message()], [USABLE], [SubmissionStatus.ACCEPTED])

        self.assertTrue(report.lock_acquired)
        self.assertEqual(1, report.received)
        self.assertEqual(1, report.accepted)

    def test_a_repeat_is_counted_as_a_duplicate(self):
        report = self._run([_message()], [USABLE], [SubmissionStatus.DUPLICATE])

        self.assertEqual(1, report.duplicates)
        self.assertEqual(0, report.accepted)

    def test_a_non_event_is_rejected_without_being_submitted(self):
        # Newsletters and receipts arrive in the same mailbox; they must not reach the API.
        report = self._run([_message()], [NOT_AN_EVENT], [])

        self.assertEqual(1, report.rejected)
        self.assertEqual(0, report.accepted)

    def test_one_unusable_message_does_not_stop_the_batch(self):
        report = self._run(
            [_message(ident="<a@lums.edu.pk>"), _message(ident="<b@lums.edu.pk>")],
            [NOT_AN_EVENT, USABLE],
            [SubmissionStatus.ACCEPTED],
        )

        self.assertEqual(2, report.received)
        self.assertEqual(1, report.rejected)
        self.assertEqual(1, report.accepted)

    def test_an_unreachable_api_stops_the_run(self):
        # Later messages would fail identically, so continuing would only burn
        # extraction quota on results that cannot be stored.
        report = self._run(
            [_message(ident="<a@lums.edu.pk>"), _message(ident="<b@lums.edu.pk>")],
            [USABLE, USABLE],
            SubmissionError("unreachable"),
        )

        self.assertEqual(0, report.accepted)
        self.assertTrue(report.lock_acquired)

    def test_senders_outside_the_allowlist_never_reach_extraction(self):
        report = self._run([_message(sender="stranger@example.com")], [], [])

        self.assertEqual(0, report.received)

    def test_an_overlapping_run_is_skipped(self):
        self.lock.parent.mkdir(parents=True, exist_ok=True)
        self.lock.write_text('{"run_id":"someone-else"}', encoding="utf-8")

        report = self._run([_message()], [USABLE], [SubmissionStatus.ACCEPTED])

        self.assertFalse(report.lock_acquired)
        self.assertEqual(0, report.received)

    def test_the_lock_is_released_for_the_next_run(self):
        self._run([_message()], [USABLE], [SubmissionStatus.ACCEPTED])

        second = self._run([_message()], [USABLE], [SubmissionStatus.DUPLICATE])

        self.assertTrue(second.lock_acquired)


class RunnerSettingsTests(unittest.TestCase):
    def test_missing_configuration_is_reported_by_name(self):
        from ..config import ConfigurationError

        with self.assertRaises(ConfigurationError) as raised:
            RunnerSettings.from_environment({})

        self.assertIn("CONNEXA_API_BASE_URL", str(raised.exception))

    def test_defaults_cover_optional_mail_settings(self):
        settings = RunnerSettings.from_environment({
            "CONNEXA_API_BASE_URL": "http://localhost:8080/",
            "CONNEXA_INGESTION_API_TOKEN": "token",
            "CONNEXA_AI_API_KEY": "key",
        })

        self.assertEqual("imap.gmail.com", settings.mail_host)
        self.assertEqual(993, settings.mail_port)
        self.assertEqual("INBOX", settings.mail_folder)

    def test_the_embedding_model_is_never_used_for_extraction(self):
        """Extraction generates text; an embedding model has no generateContent endpoint.

        Both variables are normally set together in a deployed environment, so reading the
        wrong one fails at the provider rather than in configuration, which is a much more
        confusing place to discover it.
        """
        settings = RunnerSettings.from_environment({
            "CONNEXA_API_BASE_URL": "http://localhost:8080/",
            "CONNEXA_INGESTION_API_TOKEN": "token",
            "CONNEXA_AI_API_KEY": "key",
            "CONNEXA_AI_MODEL": "gemini-3.6-flash",
            "CONNEXA_AI_EMBEDDING_MODEL": "gemini-embedding-001",
        })

        self.assertEqual("gemini-3.6-flash", settings.ai_model)


if __name__ == "__main__":
    unittest.main()
