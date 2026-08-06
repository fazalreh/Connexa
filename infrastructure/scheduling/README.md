# Scheduling announcement ingestion

The ingestion worker reads the mailbox **once per invocation and exits**. It has no loop, no
sleep and no scheduler thread of its own. Something outside it decides when it runs.

That is deliberate. A process that schedules itself has to be supervised, restarted and
monitored on its own terms; a one-shot command can be driven by whatever the platform already
provides, and a run that hangs becomes the scheduler's problem to kill rather than a stuck
process nobody is watching.

> `CONNEXA_INGESTION_POLL_INTERVAL_SECONDS` exists in configuration but nothing reads it.
> The interval is set by whichever scheduler you choose below, not by the worker.

## Why repeat runs are safe

- Each candidate carries a key derived from the source message, and the server rejects
  anything it has already ingested. Re-reading the same mailbox does not create duplicates.
- The mailbox is opened **read-only**. Messages are never marked as seen, so the mailbox
  never becomes the record of what has been processed — which would break the moment a run
  failed halfway or somebody opened the inbox.
- A lock file prevents two runs on the same host overlapping.

A missed run therefore costs a delay and nothing else.

---

## Option 1 — Render cron job (deployed)

Already defined in `render.yaml` as the `connexa-ingestion` service, scheduled `0 */2 * * *`.
Set its secrets in the dashboard alongside the API's. Nothing else is needed: the package
uses only the Python standard library, so there is no dependency install.

Each run is a fresh container, so the local lock file protects nothing across runs there.
That is acceptable because the server's idempotency check is the real guard; the lock is a
convenience for hosts that run several copies.

## Option 2 — systemd timer (a server you control)

Preferred over cron where systemd exists: it gives real logs, a timeout that actually kills a
hung run, and a record of the last result.

```bash
sudo cp connexa-ingestion.service connexa-ingestion.timer /etc/systemd/system/
sudo mkdir -p /etc/connexa && sudo chmod 750 /etc/connexa

# Configuration and secrets, readable only by root and the service user.
sudo install -o root -g connexa -m 640 /dev/null /etc/connexa/ingestion.env
sudo editor /etc/connexa/ingestion.env

sudo systemctl daemon-reload
sudo systemctl enable --now connexa-ingestion.timer
```

Check it:

```bash
systemctl list-timers connexa-ingestion.timer   # when it next fires
journalctl -u connexa-ingestion.service -n 50   # what the last run did
sudo systemctl start connexa-ingestion.service  # run once now, without waiting
```

The unit file assumes the project at `/opt/connexa` running as user `connexa`. Adjust
`WorkingDirectory`, `User` and `PYTHONPATH` if yours differ.

## Option 3 — cron (anywhere else)

```cron
0 */2 * * * cd /opt/connexa && PYTHONPATH=ingestion /usr/bin/python3 -m connexa_ingestion >> /var/log/connexa-ingestion.log 2>&1
```

Cron gives you no timeout and no structured record of failures, so redirect the output
somewhere you will actually look. Secrets must reach the process through the environment —
do not put them on the command line, where every local account can read them in the process
list.

---

## What a run reports

```
run 4f2a…: received=6 accepted=2 duplicates=3 rejected=1 conflicts=0
```

| Field | Meaning |
|---|---|
| `received` | Messages read from the mailbox this pass |
| `accepted` | New events submitted to the API |
| `duplicates` | Already ingested — expected, and the normal case for most runs |
| `rejected` | Sender not on the allow-list, or the text yielded no usable event |
| `conflicts` | Same source message, different content — surfaced for review, never overwritten |

A healthy steady state is mostly `duplicates` with an occasional `accepted`. Persistent
`rejected` counts usually mean the allow-list is too narrow rather than that extraction is
failing.

## Before enabling it

- A dedicated **app password**, never an account password.
- At least one approved sender or domain. A run refuses to start without one, because the
  address is the trust boundary — anyone who learns it can send to it.
- The API reachable from wherever the job runs, with a valid ingestion token.
- Attachments stay disabled unless there is a reason; untrusted attachments are a large risk
  for a small benefit.
