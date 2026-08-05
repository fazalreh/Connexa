"""Stable source-message identity and content-fingerprint helpers."""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import unicodedata

from .models import EventCandidate


IDEMPOTENCY_VERSION = "connexa.ingestion.idempotency.v1"
CONTENT_HASH_VERSION = "connexa.ingestion.content.v1"


def candidate_idempotency_key(candidate: EventCandidate) -> str:
    """Return a stable key for the same source record across scheduler runs.

    Content is intentionally excluded: if a source record changes, the durable
    store should surface a conflict for review instead of silently replacing an
    already accepted candidate.
    """

    material = "\x00".join(
        (
            IDEMPOTENCY_VERSION,
            _normalized_text(candidate.source_system),
            _normalized_text(candidate.source_record_id),
        )
    )
    return "ci1_" + _sha256(material.encode("utf-8"))


def candidate_content_hash(candidate: EventCandidate) -> str:
    """Hash canonical candidate content without including secrets or bytes."""

    canonical = {
        "version": CONTENT_HASH_VERSION,
        "source_system": _normalized_text(candidate.source_system),
        "source_record_id": _normalized_text(candidate.source_record_id),
        "sender_address": _normalized_text(candidate.sender_address).casefold(),
        "received_at": _normalized_datetime(candidate.received_at),
        "title": _normalized_text(candidate.title),
        "body_text": _normalized_body(candidate.body_text),
        "starts_at": _optional_datetime(candidate.starts_at),
        "ends_at": _optional_datetime(candidate.ends_at),
        "location_text": _optional_text(candidate.location_text),
        "organizer_text": _optional_text(candidate.organizer_text),
        "source_url": _optional_text(candidate.source_url),
        "attachments": sorted(
            (
                {
                    "filename": _normalized_text(attachment.filename),
                    "media_type": _normalized_text(attachment.media_type).casefold(),
                    "size_bytes": attachment.size_bytes,
                    "content_sha256": attachment.content_sha256,
                }
                for attachment in candidate.attachments
            ),
            key=lambda attachment: json.dumps(
                attachment, sort_keys=True, ensure_ascii=False, separators=(",", ":")
            ),
        ),
    }
    encoded = json.dumps(
        canonical,
        sort_keys=True,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return "ch1_" + _sha256(encoded)


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _normalized_text(value: str) -> str:
    return unicodedata.normalize("NFC", value).strip()


def _normalized_body(value: str) -> str:
    normalized_lines = [
        line.rstrip()
        for line in unicodedata.normalize("NFC", value).replace("\r\n", "\n").split("\n")
    ]
    return "\n".join(normalized_lines).strip()


def _optional_text(value: str | None) -> str | None:
    return None if value is None else _normalized_text(value)


def _normalized_datetime(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _optional_datetime(value: datetime | None) -> str | None:
    return None if value is None else _normalized_datetime(value)
