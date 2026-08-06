"""Joins the announcement reader into one runnable pass.

The pieces are deliberately separate — a mailbox source, an extractor, an API
sink — because each is useful and testable on its own. This module is the only
place that knows they belong together, and it is what a scheduler invokes.

A run is bounded and safe to repeat: the lock prevents two overlapping runs, and
the server rejects a message it has already ingested, so re-reading a mailbox
cannot create duplicates.
"""

from __future__ import annotations

from dataclasses import dataclass
import logging
import os
from typing import Mapping
from uuid import uuid4

from .config import ConfigurationError, IngestionSettings
from .extraction import EventExtractor, GeminiTextModel
from .idempotency import candidate_content_hash, candidate_idempotency_key
from .mail import ImapCandidateSource, connect
from .store import ConnexaApiCandidateStore, SubmissionError
from .workflow import CandidateEnvelope, FileRunLock, RunReport, SubmissionStatus

log = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class RunnerSettings:
    """Values the runner needs that are not part of mailbox configuration."""

    api_base_url: str
    api_token: str
    ai_api_key: str
    ai_model: str
    mail_host: str
    mail_port: int
    mail_folder: str

    @classmethod
    def from_environment(
        cls, environment: Mapping[str, str] | None = None
    ) -> "RunnerSettings":
        env = os.environ if environment is None else environment

        def required(name: str) -> str:
            value = (env.get(name) or "").strip()
            if not value:
                raise ConfigurationError(f"{name} must be set to run ingestion")
            return value

        return cls(
            api_base_url=required("CONNEXA_API_BASE_URL"),
            api_token=required("CONNEXA_INGESTION_API_TOKEN"),
            ai_api_key=required("CONNEXA_AI_API_KEY"),
            # The extractor generates text, so only the text model belongs here. Reading the
            # embedding variable would hand it a model that has no generateContent endpoint,
            # and the run would fail against the provider rather than in configuration.
            ai_model=(env.get("CONNEXA_AI_MODEL") or "").strip() or "gemini-2.5-flash",
            mail_host=(env.get("CONNEXA_MAIL_HOST") or "imap.gmail.com").strip(),
            mail_port=int((env.get("CONNEXA_MAIL_PORT") or "993").strip()),
            mail_folder=(env.get("CONNEXA_MAIL_FOLDER") or "INBOX").strip(),
        )


def run_once(
    settings: IngestionSettings,
    runner_settings: RunnerSettings,
) -> RunReport:
    """Reads the mailbox once and submits whatever it understood.

    Returns a report rather than raising on individual failures: one unreadable
    announcement must not prevent the rest of the batch from being ingested.
    """
    settings.validate_for_execution()
    run_id = str(uuid4())
    lock = FileRunLock(settings.lock_file, run_id)
    if not lock.acquire():
        # Failing closed here is deliberate: two readers running at once would
        # both call the extraction provider for the same messages.
        log.warning("Another ingestion run holds the lock; skipping this one")
        return RunReport(run_id=run_id, lock_acquired=False)

    received = accepted = duplicates = rejected = conflicts = 0
    try:
        extractor = EventExtractor(
            GeminiTextModel(runner_settings.ai_api_key, runner_settings.ai_model)
        )
        store = ConnexaApiCandidateStore(
            runner_settings.api_base_url, runner_settings.api_token
        )

        client = connect(settings, runner_settings.mail_host, runner_settings.mail_port)
        try:
            source = ImapCandidateSource(client, settings, runner_settings.mail_folder)
            candidates = list(source.collect(limit=settings.max_messages_per_run))
        finally:
            client.logout()

        for candidate in candidates:
            received += 1
            envelope = CandidateEnvelope(
                candidate=candidate,
                idempotency_key=candidate_idempotency_key(candidate),
                content_hash=candidate_content_hash(candidate),
            )
            extraction = extractor.extract(candidate.title, candidate.body_text)
            if not extraction.is_usable:
                # Not an event, or missing the start time that makes it actionable.
                rejected += 1
                continue
            try:
                status = store.submit_extracted(envelope, extraction)
            except SubmissionError:
                # The API is unreachable or refused the credentials. Later messages
                # would fail the same way, so the run stops rather than burning
                # provider quota on extractions it cannot store.
                log.exception("Stopping the run: the Connexa API is unavailable")
                break
            if status is SubmissionStatus.ACCEPTED:
                accepted += 1
            elif status is SubmissionStatus.DUPLICATE:
                duplicates += 1
            else:
                conflicts += 1
    finally:
        lock.release()

    return RunReport(
        run_id=run_id,
        lock_acquired=True,
        received=received,
        accepted=accepted,
        duplicates=duplicates,
        rejected=rejected,
        conflicts=conflicts,
    )
