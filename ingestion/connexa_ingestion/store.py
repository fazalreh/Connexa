"""Submits extracted events to the Connexa API.

The reader authenticates with a shared token rather than a user identity: it is
a scheduled process with nobody to sign in. Deduplication is deliberately not
attempted here. Two workers could each believe they were seeing a message for
the first time, so the authority is the unique constraint on the server; this
module only reports what the server decided.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
from typing import Any
from urllib import error, parse, request

from .extraction import EventExtraction
from .models import EventCandidate
from .workflow import CandidateEnvelope, SubmissionStatus


_DEFAULT_DURATION = timedelta(hours=2)
_DEFAULT_TIME_ZONE = "Asia/Karachi"
_MIN_SUMMARY = 3
_MAX_SUMMARY = 500


class SubmissionError(RuntimeError):
    """Raised when the API could not be reached or refused the credentials."""


@dataclass(frozen=True, slots=True)
class ConnexaApiCandidateStore:
    """Posts one extracted event per source message."""

    base_url: str
    api_token: str
    timeout_seconds: int = 30

    def submit_extracted(
        self,
        envelope: CandidateEnvelope,
        extraction: EventExtraction,
        *,
        default_time_zone: str = _DEFAULT_TIME_ZONE,
    ) -> SubmissionStatus:
        if not extraction.is_usable:
            return SubmissionStatus.CONFLICT

        payload = _payload(envelope, extraction, default_time_zone)
        message = request.Request(
            parse.urljoin(self.base_url, "api/v1/ingestion/events"),
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "X-Connexa-Ingestion-Token": self.api_token,
            },
            method="POST",
        )
        try:
            with request.urlopen(message, timeout=self.timeout_seconds) as response:
                document = json.loads(response.read().decode("utf-8") or "{}")
                status_code = response.status
        except error.HTTPError as exception:
            if exception.code in (400, 422):
                # The server rejected this record's shape. Retrying will not help,
                # so it is counted as rejected rather than failing the whole run.
                return SubmissionStatus.CONFLICT
            raise SubmissionError(f"API returned status {exception.code}") from None
        except (error.URLError, TimeoutError, ValueError) as exception:
            raise SubmissionError("API could not be reached") from exception

        created = bool(document.get("created")) if document else status_code == 201
        return SubmissionStatus.ACCEPTED if created else SubmissionStatus.DUPLICATE


def _payload(
    envelope: CandidateEnvelope, extraction: EventExtraction, default_time_zone: str
) -> dict[str, Any]:
    candidate: EventCandidate = envelope.candidate
    starts_at = extraction.starts_at
    ends_at = extraction.ends_at or (starts_at + _DEFAULT_DURATION)

    return {
        "sourceSystem": candidate.source_system,
        "sourceRecordId": candidate.source_record_id,
        "contentHash": envelope.content_hash,
        "title": extraction.title,
        "summary": _summary_for(candidate, extraction),
        "startsAt": _iso(starts_at),
        "endsAt": _iso(ends_at),
        "timeZone": default_time_zone,
        "venueName": extraction.location_text or "To be announced",
        "category": extraction.category or "General",
        "organizerName": extraction.organizer_text,
        "totalCapacity": None,
    }


def _summary_for(candidate: EventCandidate, extraction: EventExtraction) -> str:
    """Builds a listing summary within the API's length bounds.

    The announcement body is the best description available, but it is arbitrary
    length and the server rejects anything over the limit. Trimming here keeps a
    long announcement from being refused outright.
    """
    text = " ".join((candidate.body_text or "").split()).strip()
    if len(text) < _MIN_SUMMARY:
        text = extraction.title or candidate.title
    if len(text) <= _MAX_SUMMARY:
        return text
    cut = text[: _MAX_SUMMARY - 1]
    boundary = cut.rfind(" ")
    if boundary > _MAX_SUMMARY // 2:
        cut = cut[:boundary]
    return cut.rstrip() + "…"


def _iso(moment: datetime) -> str:
    """Serialises as UTC; the API stores instants and carries the zone separately."""
    return moment.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
