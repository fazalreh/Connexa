"""Typed, validation-first records passed through announcement ingestion."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import re


_SHA256_PATTERN = re.compile(r"[0-9a-f]{64}\Z")


class CandidateValidationError(ValueError):
    """Raised when an untrusted source record does not meet the ingestion contract."""


@dataclass(frozen=True, slots=True)
class AttachmentMetadata:
    """Metadata only; attachment bytes never travel in an event candidate."""

    filename: str
    media_type: str
    size_bytes: int
    content_sha256: str | None = None

    def __post_init__(self) -> None:
        filename = _required_text(self.filename, "filename", maximum=180)
        media_type = _required_text(self.media_type, "media_type", maximum=100).casefold()
        if self.size_bytes < 0:
            raise CandidateValidationError("size_bytes cannot be negative")
        if self.content_sha256 is not None:
            digest = self.content_sha256.casefold()
            if not _SHA256_PATTERN.fullmatch(digest):
                raise CandidateValidationError("content_sha256 must be a SHA-256 digest")
            object.__setattr__(self, "content_sha256", digest)
        object.__setattr__(self, "filename", filename)
        object.__setattr__(self, "media_type", media_type)


@dataclass(frozen=True, slots=True)
class EventCandidate:
    """An immutable event suggestion from one approved announcement source.

    The record deliberately separates source identity from extracted event data,
    so a durable store can deduplicate source messages before publication.
    """

    source_system: str
    source_record_id: str
    sender_address: str
    received_at: datetime
    title: str
    body_text: str
    starts_at: datetime | None = None
    ends_at: datetime | None = None
    location_text: str | None = None
    organizer_text: str | None = None
    source_url: str | None = None
    attachments: tuple[AttachmentMetadata, ...] = ()

    def __post_init__(self) -> None:
        source_system = _required_text(self.source_system, "source_system", maximum=80)
        source_record_id = _required_text(
            self.source_record_id, "source_record_id", maximum=512
        )
        sender_address = _required_text(self.sender_address, "sender_address", maximum=320)
        title = _required_text(self.title, "title", maximum=500)
        body_text = _required_text(self.body_text, "body_text", maximum=100_000)
        _require_timezone(self.received_at, "received_at")
        if self.starts_at is not None:
            _require_timezone(self.starts_at, "starts_at")
        if self.ends_at is not None:
            _require_timezone(self.ends_at, "ends_at")
        if (
            self.starts_at is not None
            and self.ends_at is not None
            and self.ends_at < self.starts_at
        ):
            raise CandidateValidationError("ends_at cannot be before starts_at")

        attachments = tuple(self.attachments)
        if len(attachments) > 20:
            raise CandidateValidationError("an event candidate may contain at most 20 attachments")
        if not all(isinstance(attachment, AttachmentMetadata) for attachment in attachments):
            raise CandidateValidationError("attachments must contain AttachmentMetadata records")

        object.__setattr__(self, "source_system", source_system)
        object.__setattr__(self, "source_record_id", source_record_id)
        object.__setattr__(self, "sender_address", sender_address)
        object.__setattr__(self, "title", title)
        object.__setattr__(self, "body_text", body_text)
        object.__setattr__(
            self,
            "location_text",
            _optional_text(self.location_text, "location_text", maximum=500),
        )
        object.__setattr__(
            self,
            "organizer_text",
            _optional_text(self.organizer_text, "organizer_text", maximum=500),
        )
        object.__setattr__(
            self,
            "source_url",
            _optional_text(self.source_url, "source_url", maximum=2_000),
        )
        object.__setattr__(self, "attachments", attachments)


def _required_text(value: str, field_name: str, *, maximum: int) -> str:
    if not isinstance(value, str):
        raise CandidateValidationError(f"{field_name} must be text")
    cleaned = value.strip()
    if not cleaned:
        raise CandidateValidationError(f"{field_name} cannot be blank")
    if len(cleaned) > maximum:
        raise CandidateValidationError(f"{field_name} exceeds {maximum} characters")
    if "\x00" in cleaned:
        raise CandidateValidationError(f"{field_name} cannot contain NUL characters")
    return cleaned


def _optional_text(value: str | None, field_name: str, *, maximum: int) -> str | None:
    if value is None:
        return None
    return _required_text(value, field_name, maximum=maximum)


def _require_timezone(value: datetime, field_name: str) -> None:
    if not isinstance(value, datetime) or value.tzinfo is None or value.utcoffset() is None:
        raise CandidateValidationError(f"{field_name} must be timezone-aware")
