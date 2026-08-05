"""Secure, scheduler-safe primitives for Connexa announcement ingestion.

The package intentionally contains no mailbox client, credential, or network
implementation. Adapters are supplied by the deployment layer after its access
controls are configured.
"""

from .attachments import AttachmentDecision, AttachmentPolicy
from .config import ConfigurationError, IngestionSettings
from .idempotency import candidate_content_hash, candidate_idempotency_key
from .models import AttachmentMetadata, EventCandidate
from .workflow import (
    CandidateEnvelope,
    InMemoryCandidateStore,
    RunReport,
    SchedulerSafeRunner,
    SubmissionStatus,
)

__all__ = [
    "AttachmentDecision",
    "AttachmentMetadata",
    "AttachmentPolicy",
    "CandidateEnvelope",
    "ConfigurationError",
    "EventCandidate",
    "IngestionSettings",
    "InMemoryCandidateStore",
    "RunReport",
    "SchedulerSafeRunner",
    "SubmissionStatus",
    "candidate_content_hash",
    "candidate_idempotency_key",
]
