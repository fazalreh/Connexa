"""Command-line entry point, for a cron job or scheduler.

    python -m connexa_ingestion

Configuration comes from the environment only, so no secret is ever passed on a
command line where it would land in shell history and process listings.
"""

from __future__ import annotations

import logging
import sys

from .config import ConfigurationError, IngestionSettings
from .runner import RunnerSettings, run_once


def main() -> int:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s"
    )
    try:
        settings = IngestionSettings.from_environment()
        runner_settings = RunnerSettings.from_environment()
    except ConfigurationError as error:
        # Written to stderr without the offending value, which may be a secret.
        print(f"Ingestion is not configured: {error}", file=sys.stderr)
        return 2

    report = run_once(settings, runner_settings)
    if not report.lock_acquired:
        print("Another run is in progress; nothing was read.")
        return 0

    print(
        f"run {report.run_id}: received={report.received} accepted={report.accepted} "
        f"duplicates={report.duplicates} rejected={report.rejected} "
        f"conflicts={report.conflicts}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
