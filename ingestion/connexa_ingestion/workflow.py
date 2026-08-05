"""A bounded, idempotent workflow intended for external schedulers."""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from itertools import islice
import json
import os
from pathlib import Path
from threading import Lock
from typing import Protocol
from uuid import uuid4

from .config import IngestionSettings
from .idempotency import candidate_content_hash, candidate_idempotency_key
from .models import EventCandidate


class CandidateSource(Protocol):
    """Adapter boundary for an approved announcement source."""

    def collect(self, *, limit: int) -> Iterable[EventCandidate]:
        """Yield at most ``limit`` validated candidate records."""


class CandidateStore(Protocol):
    """Durable destination with atomic idempotent insertion semantics.

    Production implementations must commit the event candidate and its unique
    idempotency key in one transaction. A unique database constraint protects
    against duplicate scheduler executions across workers.
    """

    def submit_once(self, envelope: "CandidateEnvelope") -> "SubmissionStatus":
        """Persist the envelope once, returning its resulting state."""


class SubmissionStatus(str, Enum):
    ACCEPTED = "accepted"
    DUPLICATE = "duplicate"
    CONFLICT = "conflict"


@dataclass(frozen=True, slots=True)
class CandidateEnvelope:
    candidate: EventCandidate
    idempotency_key: str
    content_hash: str


@dataclass(frozen=True, slots=True)
class RunReport:
    run_id: str
    lock_acquired: bool
    received: int = 0
    accepted: int = 0
    duplicates: int = 0
    rejected: int = 0
    conflicts: int = 0


class FileRunLock:
    """A local, fail-closed lock to prevent overlapping scheduler runs.

    This lock is intentionally not auto-removed after a crash. Operators should
    use a durable distributed lease for multi-instance deployment and perform a
    reviewed recovery of an orphaned local lock. Failing closed is safer than
    accidentally running two mailbox readers at once.
    """

    def __init__(self, lock_file: Path, run_id: str) -> None:
        self._lock_file = lock_file
        self._run_id = run_id
        self._acquired = False

    def acquire(self) -> bool:
        self._lock_file.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(
            {
                "run_id": self._run_id,
                "acquired_at": datetime.now(timezone.utc).isoformat(),
            },
            separators=(",", ":"),
        ).encode("utf-8")
        try:
            descriptor = os.open(
                self._lock_file,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                0o600,
            )
        except FileExistsError:
            return False
        try:
            with os.fdopen(descriptor, "wb") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
        except BaseException:
            # The lock is intentionally retained on an incomplete write so a
            # second worker does not overlap a potentially active run.
            raise
        self._acquired = True
        return True

    def release(self) -> None:
        if not self._acquired:
            return
        try:
            stored = json.loads(self._lock_file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        if stored.get("run_id") == self._run_id:
            try:
                self._lock_file.unlink()
            except FileNotFoundError:
                pass
        self._acquired = False


class SchedulerSafeRunner:
    """Process a bounded source batch once with policy gates and deduplication."""

    def __init__(
        self,
        settings: IngestionSettings,
        source: CandidateSource,
        store: CandidateStore,
    ) -> None:
        self._settings = settings
        self._source = source
        self._store = store

    def run_once(self) -> RunReport:
        """Run one scheduler cycle without exposing source data in reports.

        A held lock returns a skipped report. Source failures intentionally
        propagate to the scheduler so its configured retry and alerting policy
        can handle them; individual rejected candidates do not stop the batch.
        """

        self._settings.validate_for_execution()
        run_id = uuid4().hex
        run_lock = FileRunLock(self._settings.lock_file, run_id)
        if not run_lock.acquire():
            return RunReport(run_id=run_id, lock_acquired=False)

        received = accepted = duplicates = rejected = conflicts = 0
        try:
            # Enforce the batch ceiling even if an adapter ignores its limit.
            for candidate in islice(
                self._source.collect(limit=self._settings.max_messages_per_run),
                self._settings.max_messages_per_run,
            ):
                received += 1
                outcome = self._process_candidate(candidate)
                if outcome is SubmissionStatus.ACCEPTED:
                    accepted += 1
                elif outcome is SubmissionStatus.DUPLICATE:
                    duplicates += 1
                elif outcome is SubmissionStatus.CONFLICT:
                    conflicts += 1
                else:
                    rejected += 1
        finally:
            run_lock.release()

        return RunReport(
            run_id=run_id,
            lock_acquired=True,
            received=received,
            accepted=accepted,
            duplicates=duplicates,
            rejected=rejected,
            conflicts=conflicts,
        )

    def _process_candidate(self, candidate: EventCandidate) -> SubmissionStatus | None:
        if not self._settings.is_allowed_sender(candidate.sender_address):
            return None
        if any(
            not self._settings.attachment_policy.evaluate(attachment).accepted
            for attachment in candidate.attachments
        ):
            return None

        envelope = CandidateEnvelope(
            candidate=candidate,
            idempotency_key=candidate_idempotency_key(candidate),
            content_hash=candidate_content_hash(candidate),
        )
        return self._store.submit_once(envelope)


class InMemoryCandidateStore:
    """Thread-safe test double; use a durable transactional store in deployment."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._records: dict[str, CandidateEnvelope] = {}

    def submit_once(self, envelope: CandidateEnvelope) -> SubmissionStatus:
        with self._lock:
            previous = self._records.get(envelope.idempotency_key)
            if previous is None:
                self._records[envelope.idempotency_key] = envelope
                return SubmissionStatus.ACCEPTED
            if previous.content_hash == envelope.content_hash:
                return SubmissionStatus.DUPLICATE
            return SubmissionStatus.CONFLICT

    @property
    def records(self) -> tuple[CandidateEnvelope, ...]:
        with self._lock:
            return tuple(self._records.values())
