"""Environment-only configuration for the announcement-ingestion worker."""

from __future__ import annotations

from dataclasses import dataclass
from email.utils import parseaddr
import os
from pathlib import Path
import re
from typing import Mapping

from .attachments import AttachmentPolicy


DEFAULT_POLL_INTERVAL_SECONDS = 2 * 60 * 60
DEFAULT_MAX_MESSAGES_PER_RUN = 100
DEFAULT_LOCK_FILE = Path("var") / "connexa-ingestion.lock"
_DOMAIN_PATTERN = re.compile(
    r"(?=.{1,253}\Z)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}\Z"
)


class ConfigurationError(ValueError):
    """Raised when a worker is configured unsafely or incompletely."""


@dataclass(frozen=True, slots=True)
class IngestionSettings:
    """Configuration read from the process environment.

    Secret-bearing variables intentionally have no defaults. Empty values stay
    unset until the deployment environment supplies them. Calling
    :meth:`validate_for_execution` prevents a scheduler from running with a
    partial configuration.
    """

    announcement_mailbox: str | None
    mail_username: str | None
    mail_password: str | None
    allowed_sender_addresses: frozenset[str]
    allowed_sender_domains: frozenset[str]
    poll_interval_seconds: int
    max_messages_per_run: int
    lock_file: Path
    attachment_policy: AttachmentPolicy

    @classmethod
    def from_environment(
        cls, environment: Mapping[str, str] | None = None
    ) -> "IngestionSettings":
        """Read settings without printing or retaining secret values in logs."""

        env = os.environ if environment is None else environment
        return cls(
            announcement_mailbox=_optional_email(env, "CONNEXA_ANNOUNCEMENT_MAILBOX"),
            mail_username=_optional_email(env, "CONNEXA_MAIL_USERNAME"),
            mail_password=_optional_value(env, "CONNEXA_MAIL_PASSWORD"),
            allowed_sender_addresses=_parse_sender_addresses(
                _optional_value(env, "CONNEXA_INGESTION_ALLOWED_SENDERS")
            ),
            allowed_sender_domains=_parse_sender_domains(
                _optional_value(env, "CONNEXA_INGESTION_ALLOWED_DOMAINS")
            ),
            poll_interval_seconds=_positive_integer(
                env,
                "CONNEXA_INGESTION_POLL_INTERVAL_SECONDS",
                DEFAULT_POLL_INTERVAL_SECONDS,
                minimum=60,
            ),
            max_messages_per_run=_positive_integer(
                env,
                "CONNEXA_INGESTION_MAX_MESSAGES_PER_RUN",
                DEFAULT_MAX_MESSAGES_PER_RUN,
                minimum=1,
                maximum=1_000,
            ),
            lock_file=Path(
                _optional_value(env, "CONNEXA_INGESTION_LOCK_FILE")
                or DEFAULT_LOCK_FILE
            ),
            attachment_policy=AttachmentPolicy(
                enabled=_boolean(env, "CONNEXA_INGESTION_ATTACHMENTS_ENABLED", default=False),
                max_bytes=_positive_integer(
                    env,
                    "CONNEXA_INGESTION_ATTACHMENT_MAX_BYTES",
                    AttachmentPolicy.DEFAULT_MAX_BYTES,
                    minimum=1,
                    maximum=25 * 1024 * 1024,
                ),
            ),
        )

    @property
    def missing_required_environment_variables(self) -> tuple[str, ...]:
        """Return missing execution prerequisites without exposing their values."""

        missing: list[str] = []
        if not self.announcement_mailbox:
            missing.append("CONNEXA_ANNOUNCEMENT_MAILBOX")
        if not self.mail_username:
            missing.append("CONNEXA_MAIL_USERNAME")
        if not self.mail_password:
            missing.append("CONNEXA_MAIL_PASSWORD")
        if not (self.allowed_sender_addresses or self.allowed_sender_domains):
            missing.append(
                "CONNEXA_INGESTION_ALLOWED_SENDERS or "
                "CONNEXA_INGESTION_ALLOWED_DOMAINS"
            )
        return tuple(missing)

    def validate_for_execution(self) -> None:
        """Fail closed until credentials and a source allow-list are supplied."""

        missing = self.missing_required_environment_variables
        if missing:
            raise ConfigurationError(
                "Announcement ingestion is not ready; configure: " + ", ".join(missing)
            )

        if self.lock_file.is_absolute() and self.lock_file.parent == self.lock_file:
            raise ConfigurationError("CONNEXA_INGESTION_LOCK_FILE must name a file")

    def is_allowed_sender(self, sender_address: str) -> bool:
        """Allow only explicitly configured sender addresses or domains."""

        normalized = _normalize_email(sender_address)
        if normalized is None:
            return False
        domain = normalized.rsplit("@", 1)[1]
        return (
            normalized in self.allowed_sender_addresses
            or domain in self.allowed_sender_domains
        )


def _optional_value(environment: Mapping[str, str], name: str) -> str | None:
    value = environment.get(name)
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None


def _normalize_email(value: str) -> str | None:
    display_name, parsed = parseaddr(value)
    if display_name or parsed != value or parsed.count("@") != 1:
        return None
    local_part, domain = parsed.rsplit("@", 1)
    if not local_part or not _DOMAIN_PATTERN.fullmatch(domain.casefold()):
        return None
    return f"{local_part.casefold()}@{domain.casefold()}"


def _optional_email(environment: Mapping[str, str], name: str) -> str | None:
    value = _optional_value(environment, name)
    if value is None:
        return None
    normalized = _normalize_email(value)
    if normalized is None:
        raise ConfigurationError(f"{name} must be a single email address")
    return normalized


def _parse_sender_addresses(value: str | None) -> frozenset[str]:
    if value is None:
        return frozenset()
    addresses: set[str] = set()
    for item in value.split(","):
        normalized = _normalize_email(item.strip())
        if normalized is None:
            raise ConfigurationError(
                "CONNEXA_INGESTION_ALLOWED_SENDERS contains an invalid address"
            )
        addresses.add(normalized)
    return frozenset(addresses)


def _parse_sender_domains(value: str | None) -> frozenset[str]:
    if value is None:
        return frozenset()
    domains: set[str] = set()
    for item in value.split(","):
        domain = item.strip().casefold()
        if not _DOMAIN_PATTERN.fullmatch(domain):
            raise ConfigurationError(
                "CONNEXA_INGESTION_ALLOWED_DOMAINS contains an invalid domain"
            )
        domains.add(domain)
    return frozenset(domains)


def _positive_integer(
    environment: Mapping[str, str],
    name: str,
    default: int,
    *,
    minimum: int,
    maximum: int | None = None,
) -> int:
    raw_value = _optional_value(environment, name)
    if raw_value is None:
        return default
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise ConfigurationError(f"{name} must be an integer") from exc
    if value < minimum or (maximum is not None and value > maximum):
        bounds = f"at least {minimum}"
        if maximum is not None:
            bounds += f" and at most {maximum}"
        raise ConfigurationError(f"{name} must be {bounds}")
    return value


def _boolean(
    environment: Mapping[str, str], name: str, *, default: bool
) -> bool:
    raw_value = _optional_value(environment, name)
    if raw_value is None:
        return default
    normalized = raw_value.casefold()
    if normalized in {"1", "true", "yes"}:
        return True
    if normalized in {"0", "false", "no"}:
        return False
    raise ConfigurationError(f"{name} must be true or false")
