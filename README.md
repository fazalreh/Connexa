# Connexa

Connexa is an Android-first event discovery and coordination platform for connected communities. It will consolidate trusted event announcements, attendance actions, updates, reminders, and practical event guidance into one clear member experience.

**Arbisoft Internship &middot; Fazal Rehman &middot; Mentored by Waleed Khalid**

## Product scope

Connexa brings trusted event discovery and coordination into one member experience:

- Account access for attendees and organizers.
- Event discovery, search, details, calendar views, attendance actions, updates, and reminders.
- Organizer event creation, media, event management, and attendance analytics.
- A server-side event assistant and trusted announcement ingestion workflow.

## Current implementation

The repository provides a Java 17 Android and Spring Boot baseline with a contract-first API. Android feature modules cover event discovery and details, calendar presentation, account-form validation, attendance controls, assistant conversation, organizer draft preparation, and notification presentation. Protected server routes define the identity-scoped attendance, organizer, assistant, and notification workflows. All privileged integrations remain disabled until their Connexa configuration is supplied.

- Native Android application with XML layouts, Material Components, ViewBinding, and feature-isolated Java packages.
- Java 17 Spring Boot API with health, status, request IDs, structured errors, and a versioned event catalog boundary.
- OpenAPI 3.1 contract, event/user validation rules, and environment templates with empty values.
- Unit tests, API tests, Android smoke coverage, static repository checks, and continuous-integration workflow definitions.

## Technology decisions

| Area | Decision | Reason |
| --- | --- | --- |
| Android | Java 17, Android SDK, XML layouts, AndroidX, Material Components | Native, accessible Android experience with a focused Java codebase. |
| API | Java 17, Spring Boot 4.1, Spring MVC, Validation, Actuator | Production-grade HTTP services, externalized configuration, health checks, and test support. |
| Contract | OpenAPI 3.1 | Makes the client/server agreement explicit before feature work grows. |
| Integration boundary | Interfaces and local-safe implementations | Keeps credentials and vendor-specific calls out of Android and core business logic. |
| Quality | JUnit, MockMvc, Espresso, Gradle Wrapper, CI | Provides repeatable checks without relying on private accounts. |

## Repository layout

```text
Connexa/
|-- android/                     Native Android application
|-- services/connexa-api/        Spring Boot API
|-- contracts/openapi/           Versioned API contract
|-- contracts/examples/          Example event and user payloads
|-- docs/                        Architecture decisions and runbooks
|-- infrastructure/              Local integration guidance
|-- scripts/                     Repository verification scripts
`-- .github/workflows/           Continuous integration
```

## Local prerequisites

Install the following before building locally:

- JDK 17
- Android Studio with Android SDK Platform 37 and Build Tools 36.0.0
- An Android emulator or physical device for instrumented tests

No Firebase, AI, mail, or notification account is required to build the baseline. Those capabilities are activated only through the Connexa configuration path when their new service settings are available.

## Run the checks

From PowerShell:

```powershell
python scripts/verify_foundation.py

Set-Location services/connexa-api
.\gradlew.bat test

Set-Location ../../android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug

Set-Location ..
$env:PYTHONPATH = "$PWD\ingestion"
python -m unittest discover -s ingestion/connexa_ingestion/tests -v
```

To run the Android instrumented smoke test, start an emulator and run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Configuration and credentials

The configuration templates intentionally contain blank values. Use `services/connexa-api/.env.example` as a reference when an integration is ready, then provide real values through your local environment or deployment secret manager. The foundation does not load a `.env` file automatically. The API defaults to rejecting identity requests, disabling the assistant gateway, and using in-memory stores; switch a mode only when its replacement server-side adapter and fresh configuration are both ready.

Do not commit `.env` files, service-account files, keystores, private tokens, or production endpoints. Connexa keeps all privileged integration work on the server side.

## Current API surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/actuator/health` | Process health for local checks and deployment probes. |
| `GET` | `/api/v1/platform/status` | Non-sensitive service status with request ID. |
| `GET` | `/api/v1/events` | Contract-first empty event collection with pagination. |
| `GET` | `/api/v1/events/{eventId}` | Event lookup boundary; unknown IDs return a standard problem response. |
| `GET` | `/api/v1/me` | Verified identity and server-recognized roles for the current caller. |
| `GET` | `/api/v1/me/attendance` | The caller's saved-event and RSVP state. |
| `GET` | `/api/v1/events/{eventId}/attendance` | Attendance state for one event and the current caller. |
| `PUT` / `DELETE` | `/api/v1/events/{eventId}/saved` | Save or remove a saved-event marker. |
| `PUT` / `DELETE` | `/api/v1/events/{eventId}/rsvp` | Set or clear an RSVP. |
| `GET` / `POST` | `/api/v1/organizer/events` | List or create private organizer event drafts. |
| `POST` | `/api/v1/assistant/messages` | Server-side event-assistant message boundary. |
| `GET` | `/api/v1/notifications` | Identity-scoped notification inbox. |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | Mark a notification as read. |

All identity-scoped endpoints use the documented bearer-authentication scheme. The complete schema, including shared problem responses and request/response validation rules, is maintained in [contracts/openapi/connexa-v1.yaml](contracts/openapi/connexa-v1.yaml).

## Engineering rules

- Treat the OpenAPI contract as the public interface source of truth.
- Store all timestamps in UTC and preserve each event's IANA time zone.
- Use opaque UUID identifiers and optimistic revision numbers.
- Generate a request ID for every API response and preserve it in logs.
- Validate event data before any later publishing or notification workflow.
- Keep unavailable integrations disabled rather than replacing them with hard-coded credentials.
