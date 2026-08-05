from __future__ import annotations

from datetime import datetime, timezone
import unittest

from connexa_ingestion.idempotency import (
    candidate_content_hash,
    candidate_idempotency_key,
)
from connexa_ingestion.models import EventCandidate


def _candidate(*, body_text: str = "Details\n", title: str = "Community event") -> EventCandidate:
    return EventCandidate(
        source_system="approved-mailbox",
        source_record_id="message-123",
        sender_address="notice@example.org",
        received_at=datetime(2026, 8, 5, 8, 30, tzinfo=timezone.utc),
        title=title,
        body_text=body_text,
    )


class IdempotencyTests(unittest.TestCase):
    def test_whitespace_equivalent_content_has_same_hash(self) -> None:
        first = _candidate(body_text="Details\r\n")
        second = _candidate(body_text="Details\n")

        self.assertEqual(candidate_content_hash(first), candidate_content_hash(second))
        self.assertEqual(candidate_idempotency_key(first), candidate_idempotency_key(second))

    def test_content_change_retains_source_identity_but_changes_hash(self) -> None:
        original = _candidate(title="Community event")
        changed = _candidate(title="Updated community event")

        self.assertEqual(
            candidate_idempotency_key(original), candidate_idempotency_key(changed)
        )
        self.assertNotEqual(candidate_content_hash(original), candidate_content_hash(changed))


if __name__ == "__main__":
    unittest.main()
