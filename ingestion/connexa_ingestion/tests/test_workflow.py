from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from connexa_ingestion.config import IngestionSettings
from connexa_ingestion.models import EventCandidate
from connexa_ingestion.workflow import (
    FileRunLock,
    InMemoryCandidateStore,
    SchedulerSafeRunner,
)


class StaticSource:
    def __init__(self, candidates: list[EventCandidate]) -> None:
        self._candidates = candidates

    def collect(self, *, limit: int):
        return iter(self._candidates[:limit])


def _settings(lock_file: Path) -> IngestionSettings:
    return IngestionSettings.from_environment(
        {
            "CONNEXA_ANNOUNCEMENT_MAILBOX": "announcements@example.org",
            "CONNEXA_MAIL_USERNAME": "worker@example.org",
            "CONNEXA_MAIL_PASSWORD": "test-only-value",
            "CONNEXA_INGESTION_ALLOWED_SENDERS": "notice@example.org",
            "CONNEXA_INGESTION_LOCK_FILE": str(lock_file),
        }
    )


def _candidate() -> EventCandidate:
    return EventCandidate(
        source_system="approved-mailbox",
        source_record_id="message-123",
        sender_address="notice@example.org",
        received_at=datetime(2026, 8, 5, 8, 30, tzinfo=timezone.utc),
        title="Community event",
        body_text="Details",
    )


class WorkflowTests(unittest.TestCase):
    def test_repeat_runs_are_idempotent(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            settings = _settings(Path(temporary_directory) / "worker.lock")
            store = InMemoryCandidateStore()
            runner = SchedulerSafeRunner(settings, StaticSource([_candidate()]), store)

            first = runner.run_once()
            second = runner.run_once()

        self.assertEqual((first.received, first.accepted, first.duplicates), (1, 1, 0))
        self.assertEqual((second.received, second.accepted, second.duplicates), (1, 0, 1))
        self.assertEqual(len(store.records), 1)

    def test_existing_lock_skips_overlapping_run(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            lock_file = Path(temporary_directory) / "worker.lock"
            settings = _settings(lock_file)
            held_lock = FileRunLock(lock_file, "held-run")
            self.assertTrue(held_lock.acquire())
            try:
                report = SchedulerSafeRunner(
                    settings, StaticSource([_candidate()]), InMemoryCandidateStore()
                ).run_once()
            finally:
                held_lock.release()

        self.assertFalse(report.lock_acquired)
        self.assertEqual(report.received, 0)


if __name__ == "__main__":
    unittest.main()
