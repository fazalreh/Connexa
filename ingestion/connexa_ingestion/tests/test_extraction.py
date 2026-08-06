"""Tests for announcement extraction.

The model's reply is untrusted input, so these exercise the parsing rules
directly rather than through a provider.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
import unittest

from ..extraction import (
    EventExtraction,
    EventExtractor,
    ExtractionError,
    parse_extraction,
)


NOW = datetime(2026, 6, 1, tzinfo=timezone.utc)


def _reply(**overrides) -> str:
    document = {
        "is_event": True,
        "title": "Robotics Workshop",
        "starts_at": "2026-07-01T17:00:00+05:00",
        "ends_at": "2026-07-01T19:00:00+05:00",
        "location": "Main Auditorium",
        "organizer": "Robotics Society",
        "category": "Technology",
    }
    document.update(overrides)
    return json.dumps(document)


class ParseExtractionTests(unittest.TestCase):
    def test_reads_a_well_formed_announcement(self) -> None:
        result = parse_extraction(_reply(), now=NOW)

        self.assertTrue(result.is_event)
        self.assertTrue(result.is_usable)
        self.assertEqual("Robotics Workshop", result.title)
        self.assertEqual("Main Auditorium", result.location_text)
        self.assertEqual("Robotics Society", result.organizer_text)
        self.assertEqual("Technology", result.category)
        self.assertEqual(
            "2026-07-01T12:00:00+00:00",
            result.starts_at.astimezone(timezone.utc).isoformat(),
        )

    def test_non_event_is_a_normal_outcome(self) -> None:
        result = parse_extraction(json.dumps({"is_event": False}), now=NOW)

        self.assertFalse(result.is_event)
        self.assertFalse(result.is_usable)

    def test_json_wrapped_in_a_code_fence_is_read(self) -> None:
        payload = "Here you go:\n```json\n" + _reply() + "\n```"

        self.assertTrue(parse_extraction(payload, now=NOW).is_usable)

    def test_json_surrounded_by_prose_is_read(self) -> None:
        payload = "Sure! " + _reply() + " Hope that helps."

        self.assertTrue(parse_extraction(payload, now=NOW).is_usable)

    def test_unparseable_reply_degrades_to_not_an_event(self) -> None:
        # One bad reply must never end a run.
        for payload in ("", "not json at all", "{broken", "[]", "null"):
            self.assertFalse(parse_extraction(payload, now=NOW).is_event, payload)

    def test_event_without_a_start_time_is_rejected(self) -> None:
        # Without a start there is nothing to place on a calendar.
        result = parse_extraction(_reply(starts_at=None), now=NOW)

        self.assertFalse(result.is_event)

    def test_event_without_a_title_is_not_usable(self) -> None:
        result = parse_extraction(_reply(title=None), now=NOW)

        self.assertFalse(result.is_usable)

    def test_placeholder_strings_are_treated_as_missing(self) -> None:
        result = parse_extraction(
            _reply(location="null", organizer="N/A", category="unknown"), now=NOW
        )

        self.assertIsNone(result.location_text)
        self.assertIsNone(result.organizer_text)
        self.assertIsNone(result.category)

    def test_end_before_start_is_discarded(self) -> None:
        result = parse_extraction(
            _reply(ends_at="2026-07-01T09:00:00+05:00"), now=NOW
        )

        self.assertTrue(result.is_usable)
        self.assertIsNone(result.ends_at)

    def test_implausibly_long_event_drops_the_end_time(self) -> None:
        # A multi-month span is a misread date far more often than a real event.
        result = parse_extraction(_reply(ends_at="2026-11-01T19:00:00+05:00"), now=NOW)

        self.assertTrue(result.is_usable)
        self.assertIsNone(result.ends_at)

    def test_long_past_announcement_is_rejected(self) -> None:
        # Usually a footer or copyright date rather than the occasion.
        result = parse_extraction(_reply(starts_at="2020-01-01T10:00:00+05:00"), now=NOW)

        self.assertFalse(result.is_event)

    def test_recent_past_is_kept(self) -> None:
        result = parse_extraction(_reply(starts_at="2026-05-20T10:00:00+05:00"), now=NOW)

        self.assertTrue(result.is_usable)

    def test_naive_timestamp_is_given_the_configured_zone(self) -> None:
        result = parse_extraction(_reply(starts_at="2026-07-01T17:00:00"), now=NOW)

        self.assertEqual(timedelta(hours=5), result.starts_at.utcoffset())

    def test_zulu_timestamp_is_accepted(self) -> None:
        result = parse_extraction(_reply(starts_at="2026-07-01T12:00:00Z"), now=NOW)

        self.assertEqual(timedelta(0), result.starts_at.utcoffset())

    def test_malformed_date_rejects_the_event(self) -> None:
        result = parse_extraction(_reply(starts_at="next Friday"), now=NOW)

        self.assertFalse(result.is_event)

    def test_overlong_text_is_truncated(self) -> None:
        result = parse_extraction(_reply(title="x" * 500), now=NOW)

        self.assertEqual(160, len(result.title))

    def test_whitespace_is_normalised(self) -> None:
        result = parse_extraction(_reply(title="  Robotics\n\n   Workshop  "), now=NOW)

        self.assertEqual("Robotics Workshop", result.title)


class FakeModel:
    def __init__(self, reply: str | None = None, failure: Exception | None = None) -> None:
        self._reply = reply
        self._failure = failure
        self.calls: list[tuple[str, str]] = []

    def generate(self, instruction: str, content: str) -> str:
        self.calls.append((instruction, content))
        if self._failure is not None:
            raise self._failure
        return self._reply or ""


class EventExtractorTests(unittest.TestCase):
    def test_subject_and_body_are_both_given_to_the_model(self) -> None:
        model = FakeModel(_reply())

        EventExtractor(model).extract("Robotics Workshop", "Join us at 5pm.")

        _, content = model.calls[0]
        self.assertIn("Robotics Workshop", content)
        self.assertIn("Join us at 5pm.", content)

    def test_provider_failure_skips_the_message(self) -> None:
        # A provider outage must skip one announcement, not end the run.
        extractor = EventExtractor(FakeModel(failure=ExtractionError("down")))

        result = extractor.extract("Subject", "Body")

        self.assertIsInstance(result, EventExtraction)
        self.assertFalse(result.is_event)


if __name__ == "__main__":
    unittest.main()
