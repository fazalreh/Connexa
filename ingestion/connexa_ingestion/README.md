# Connexa announcement ingestion

This module provides the safe boundary between approved announcements and the
Connexa event-review pipeline. It uses only Python's standard library and does
not connect to an external account by itself.

Project attribution: Fazal Rehman, Arbisoft Intern, under the mentorship of
Waleed Khalid.

## Workflow

1. An external scheduler invokes `SchedulerSafeRunner.run_once()`.
2. The runner verifies that required environment values and a sender allow-list
   are present, then acquires an exclusive local lock.
3. A source adapter yields a bounded set of typed `EventCandidate` records.
4. Each candidate is checked against the sender and attachment policies.
5. A stable source-record key and canonical content hash are generated.
6. A durable destination atomically accepts, skips, or flags the candidate.
7. The lock is released after the batch completes.

For more than one worker instance, implement `CandidateStore` with a
transaction and a unique constraint on `idempotency_key`, then replace the local
file lock with a distributed lease. Do not automatically delete an existing lock
after a crash; recover it through an operator-reviewed procedure.

## Configuration

Copy the variable names from `.env.example` into the deployment environment.
Sensitive values are intentionally blank. A run is refused until the mailbox
identity, authentication values, and at least one sender address or domain are
configured. Attachment handling is disabled by default and uses a strict
allow-list when explicitly enabled.

Do not log message content, sender addresses, credentials, or attachment bytes.
Do not store or render attachment data before malware scanning and content-type
verification in the deployment adapter.

## Local verification

From the Connexa repository root:

```powershell
$env:PYTHONPATH = "$PWD\ingestion"
python -m unittest discover -s ingestion/connexa_ingestion/tests -t ingestion -v
```

`-t ingestion` sets the discovery top-level directory to the package root. Without
it, the test modules are imported outside the package, their relative imports
fail, and the run silently covers only part of the suite.
