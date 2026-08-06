# Connexa

Connexa is an Android-first event discovery and coordination platform for connected
communities. It brings trusted event announcements, attendance, live seat availability,
door check-in, and an event assistant into one member experience.

**Arbisoft Internship &middot; Fazal Rehman &middot; Mentored by Waleed Khalid**

---

## What it does

**For attendees.** Discover events in a feed with search and recommendations, read event
detail with live seat counts, RSVP or join a waitlist when an event is full, keep a calendar,
ask an assistant what is on, receive notifications, and present a signed QR pass at the door.

**For organizers.** Draft an event, attach a cover image, publish it, and scan attendee passes
at the door.

**Automatically.** An ingestion worker reads an approved announcements mailbox, extracts
structured event fields from the message text, and submits them to the API for review.

---

## Architecture

Three deployable pieces around one contract:

```text
Android app  ──►  Connexa API  ──►  PostgreSQL
(Java, XML)       (Spring Boot)     (Flyway V1–V10)
                       ▲
                       │
Ingestion worker ──────┘        AI provider ◄── (server-side only)
(Python, cron)
```

| Component | Stack | Role |
|---|---|---|
| `android/` | Java 17, XML, Material 3, ViewBinding | Native client |
| `services/connexa-api/` | Java 17, Spring Boot 4.1, JDBC + Flyway | All business logic and every privileged call |
| `ingestion/` | Python 3, standard library only | Mailbox → extraction → API |
| `contracts/openapi/` | OpenAPI 3.1 | The client/server agreement |

### Two rules the whole codebase rests on

**1. Every privileged call is server-side.** Android holds no AI provider key, no storage
secret, and no database credential. An application package is readable by anyone who installs
it, so a secret shipped inside one is a published secret. The rule is structural: no provider
SDK is on the Android dependency list.

**2. Every adapter fails closed by default.** Each capability has a real adapter and a
refusing one, selected by an explicit configuration mode. The default is always the refusing
one, so an unconfigured deployment returns `401` rather than silently serving nothing.

| Mode | Default | Configured alternative |
|---|---|---|
| `CONNEXA_IDENTITY_MODE` | `rejecting` | `firebase` |
| `CONNEXA_ASSISTANT_MODE` | `disabled` | `gemini` |
| `CONNEXA_PERSISTENCE_MODE` | `in-memory` | `postgres` |

---

## AI features

### Event assistant — grounded, with citations that cannot be invented

`POST /api/v1/assistant/messages` answers questions about what is on. Relevant events are
retrieved first and supplied to the model as a **numbered list**; the model may cite only
`[1]`, `[2]`, and each index is resolved back to a real event server-side. An index outside
the supplied range is dropped.

This is why it matters: a model asked to return event *identifiers* will produce well-formed
identifiers for events that do not exist, and a confident citation of a non-existent event is
worse than no answer. Index-based citation makes that structurally impossible rather than
merely unlikely.

Requests are rate limited and output-token capped.

### Semantic search and recommendations

`GET /api/v1/events/search` ranks by meaning, not keywords — *"quiet evening outdoors"*
returns **Rooftop Film Night** and **Open Mic Poetry Evening**, neither sharing a word with the
query. Events are embedded with `gemini-embedding-001` at 768 dimensions and compared by cosine
similarity.

Two things worth knowing. The model returns vectors with a norm of about 0.59, so **vectors are
normalised before storage and comparison** — without that, ranking follows magnitude as much as
direction. And real scores cluster between 0.70 and 0.78, so ordering is meaningful while the
absolute number is not: **raw similarity is never shown in the UI.**

`GET /api/v1/me/recommendations` uses the same index against a member's RSVP history.

### Announcement extraction

The ingestion worker turns announcement prose into structured event fields. `extraction.py` is
deliberately split: `parse_extraction()` is pure and holds every rule about what a usable result
looks like — strip code fences, require a JSON object, validate each field, reject an impossible
or absent date — while the network call is a thin wrapper around it.

**A model's reply is untrusted input.** The rules that decide whether to trust it are unit
tested exhaustively with no provider involved.

### Models used

| Model | Purpose |
|---|---|
| `gemini-2.5-flash` | Assistant replies, announcement extraction |
| `gemini-embedding-001` | Event embeddings for search and recommendations |

---

## API surface

All identity-scoped routes use bearer authentication. The full schema, including shared
problem responses and validation rules, is in
[contracts/openapi/connexa-v1.yaml](contracts/openapi/connexa-v1.yaml).

### Public

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Process health |
| `GET` | `/api/v1/platform/status` | Service status with request ID |
| `GET` | `/api/v1/events` | Published event feed, paginated |
| `GET` | `/api/v1/events/{eventId}` | Event detail |
| `GET` | `/api/v1/events/search` | Semantic search |
| `GET` | `/api/v1/events/{eventId}/capacity` | Seat count — readable without signing in |

Capacity is public because the count is about the *event*. An RSVP, a waitlist place and a
saved event are about a *person*, and stay behind the boundary. The live stream stays closed
too, being a connection an anonymous caller could hold open indefinitely.

### Identity-scoped

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/me` | Verified identity and roles |
| `GET` | `/api/v1/me/attendance` | Saved events and RSVPs |
| `GET` | `/api/v1/me/recommendations` | Personalised suggestions |
| `PUT` / `DELETE` | `/api/v1/events/{eventId}/saved` | Save or unsave |
| `PUT` / `DELETE` | `/api/v1/events/{eventId}/rsvp` | Set or clear an RSVP |
| `GET` | `/api/v1/events/{eventId}/attendance` | Attendance for one event |
| `GET` | `/api/v1/events/{eventId}/capacity/stream` | Live capacity (SSE) |
| `POST` / `GET` / `DELETE` | `/api/v1/events/{eventId}/waitlist` | Join, check place, leave |
| `GET` | `/api/v1/events/{eventId}/check-in-pass` | Signed QR pass |
| `POST` | `/api/v1/assistant/messages` | Ask the assistant |
| `GET` | `/api/v1/notifications` | Notification inbox |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | Mark as read |
| `POST` / `DELETE` | `/api/v1/me/devices` | Push token registration |

### Organizer

| Method | Path | Purpose |
|---|---|---|
| `GET` / `POST` | `/api/v1/organizer/events` | List or create drafts |
| `POST` | `/api/v1/organizer/events/{draftId}/publish` | Publish a draft |
| `POST` | `/api/v1/media/uploads` | Request an upload signature |
| `POST` | `/api/v1/media/uploads/confirmations` | Confirm and verify an upload |
| `POST` | `/api/v1/events/{eventId}/check-in` | Redeem a pass at the door |
| `GET` | `/api/v1/events/{eventId}/check-in-count` | Arrivals so far |
| `POST` | `/api/v1/search/index` | Rebuild the embedding index |

### Service-to-service

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/ingestion/events` | Submit an extracted announcement (shared token) |

---

## Setup

### Prerequisites

- JDK 17
- Android Studio with SDK Platform 37 and Build Tools 36.0.0
- Python 3.10+ (ingestion worker and repository checks)
- PostgreSQL 14+ for durable persistence — optional, the API runs in-memory without it

### Build and run

```bash
# API
cd services/connexa-api
./gradlew bootRun          # http://localhost:8080

# Android — or open android/ in Android Studio
cd android
./gradlew :app:assembleDebug
```

The Android debug build talks to `10.0.2.2:8080`, the host machine as seen from the emulator.
Cleartext is permitted **only** to that address in debug; release builds require HTTPS.

Use JDK 17 for the API build. For the Android build, use Android Studio's bundled JetBrains
Runtime as the Gradle JDK — stable AGP 9.3.1 bundles a lint parser that calls a Java 21+ method,
so lint crashes when the Gradle daemon itself runs on JDK 17. The app's own source and bytecode
compatibility stay on Java 17 either way.

### Configuration

No account or key is needed to build, test, or run the baseline — unconfigured capabilities
report themselves unavailable rather than failing.

To enable one, supply its values through your environment or a deployment secret manager.
`services/connexa-api/.env.example` lists every variable name with **empty** values as a
reference. A repository check fails the build if a real value ever appears in a tracked file.

| Capability | Variables |
|---|---|
| Identity | `CONNEXA_FIREBASE_PROJECT_ID`, `CONNEXA_FIREBASE_SERVICE_ACCOUNT_JSON`, `CONNEXA_ORGANIZER_EMAILS` |
| Database | `CONNEXA_DB_URL`, `CONNEXA_DB_USERNAME`, `CONNEXA_DB_PASSWORD`, `CONNEXA_DB_MIGRATE` |
| Assistant & search | `CONNEXA_AI_PROVIDER`, `CONNEXA_AI_MODEL`, `CONNEXA_AI_API_KEY` |
| Media | `CONNEXA_CLOUDINARY_CLOUD_NAME`, `CONNEXA_CLOUDINARY_API_KEY`, `CONNEXA_CLOUDINARY_API_SECRET` |
| Check-in | `CONNEXA_CHECKIN_SECRET` |
| Ingestion | `CONNEXA_ANNOUNCEMENT_MAILBOX`, `CONNEXA_MAIL_USERNAME`, `CONNEXA_MAIL_PASSWORD`, `CONNEXA_INGESTION_ALLOWED_SENDERS` |

Never commit a `.env` file, service-account file, keystore, or `google-services.json`. All are
git-ignored.

### Ingestion worker

```bash
export PYTHONPATH="$PWD/ingestion"
python -m connexa_ingestion
```

Configuration comes from the environment only, so no secret lands in shell history or a process
listing. A run is refused until the mailbox, credentials, and at least one allowed sender are
configured. A file lock prevents two scheduled runs from overlapping.

---

## Run the checks

```bash
# Repository checks — secrets, templates, structure
python3 scripts/verify_foundation.py

# API
cd services/connexa-api && ./gradlew test

# Android
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug

# Ingestion
export PYTHONPATH="$PWD/ingestion"
python3 -m unittest discover -s ingestion/connexa_ingestion/tests -t ingestion -v
```

On PowerShell, use `$env:PYTHONPATH = "$PWD\ingestion"` and `.\gradlew.bat`.

`-t ingestion` sets the discovery top-level directory to the package root. Without it the test
modules import outside the package, their relative imports fail, and the run silently covers
only part of the suite.

Instrumented tests need a running emulator or attached device:

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest
```

---

## Repository layout

```text
Connexa/
├── android/                  Native Android application
├── services/connexa-api/     Spring Boot API
│   └── src/main/resources/db/migration/   Flyway V1–V10
├── ingestion/                Python announcement worker
├── contracts/openapi/        Versioned API contract
├── docs/                     Architecture decisions and runbooks
├── scripts/                  Repository verification
├── prompts.md                AI interaction log
└── REFLECTION.md             What AI did well, where it failed
```

---

## Engineering rules

- The OpenAPI contract is the source of truth for the public interface.
- Timestamps are stored in UTC; each event keeps its IANA time zone.
- Identifiers are opaque UUIDs with optimistic revision numbers.
- Every response carries a request ID, preserved in logs.
- Anything arriving via the client is a claim, not a fact — verify it server-side.
- An unconfigured integration stays disabled; it is never replaced with a hard-coded credential.
