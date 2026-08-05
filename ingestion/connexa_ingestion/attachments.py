"""Metadata-only attachment controls for announcement ingestion."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import PurePath

from .models import AttachmentMetadata


@dataclass(frozen=True, slots=True)
class AttachmentPolicy:
    """Conservative allow-list applied before attachment content is accepted.

    The default is disabled. Even when enabled, a production adapter must scan
    downloaded bytes before persistence and must never execute or render an
    attachment from an untrusted source.
    """

    DEFAULT_MAX_BYTES = 5 * 1024 * 1024

    enabled: bool = False
    max_bytes: int = DEFAULT_MAX_BYTES
    allowed_extensions: frozenset[str] = field(
        default_factory=lambda: frozenset({".pdf", ".docx", ".txt", ".png", ".jpg", ".jpeg", ".webp"})
    )
    allowed_media_types: frozenset[str] = field(
        default_factory=lambda: frozenset(
            {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "image/jpeg",
                "image/png",
                "image/webp",
            }
        )
    )

    def __post_init__(self) -> None:
        if self.max_bytes < 1:
            raise ValueError("max_bytes must be positive")
        if not self.allowed_extensions or not self.allowed_media_types:
            raise ValueError("attachment allow-lists cannot be empty")

    def evaluate(self, attachment: AttachmentMetadata) -> "AttachmentDecision":
        """Return a safe, non-sensitive acceptance decision for metadata."""

        if not self.enabled:
            return AttachmentDecision(False, "attachment ingestion is disabled")

        filename = attachment.filename
        if not _has_safe_filename(filename):
            return AttachmentDecision(False, "attachment filename is unsafe")

        extension = PurePath(filename).suffix.casefold()
        if extension not in self.allowed_extensions:
            return AttachmentDecision(False, "attachment type is not allowed")
        if attachment.media_type not in self.allowed_media_types:
            return AttachmentDecision(False, "attachment media type is not allowed")
        if attachment.size_bytes > self.max_bytes:
            return AttachmentDecision(False, "attachment exceeds the size limit")
        if attachment.size_bytes == 0:
            return AttachmentDecision(False, "attachment is empty")
        return AttachmentDecision(True, "accepted", sanitized_filename=filename)


@dataclass(frozen=True, slots=True)
class AttachmentDecision:
    accepted: bool
    reason: str
    sanitized_filename: str | None = None


def _has_safe_filename(filename: str) -> bool:
    if not filename or len(filename) > 180 or "\x00" in filename:
        return False
    if "/" in filename or "\\" in filename or filename in {".", ".."}:
        return False
    if filename.startswith(".") or ".." in filename:
        return False
    return PurePath(filename).name == filename
