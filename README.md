# Connexa

Connexa is an Android-first event discovery and coordination platform for connected communities. It will consolidate trusted event announcements, attendance actions, updates, reminders, and practical event guidance into one clear member experience.

**Arbisoft Internship Final Project**  
**Prepared by:** Fazal Rehman  
**Mentored by:** Waleed Khalid

## Milestone 1: foundation

This milestone establishes the technical baseline on which later features will be built. It contains no live third-party accounts, API keys, or service credentials.

- Native Java Android application shell with a verified empty/loading foundation state.
- Java 17 Spring Boot API with health, status, request IDs, structured errors, and an empty event catalog boundary.
- OpenAPI 3.1 contract for the initial public API surface.
- Event and user contracts with validation rules for time, status, pagination, and versioning.
- Empty environment templates and integration boundaries for Firebase, AI, mail, and notifications.
- Unit tests, API tests, an Android smoke test, static repository checks, and continuous-integration workflow definitions.

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

No Firebase, AI, mail, or notification account is required for Milestone 1.

## Run the checks

From PowerShell:

```powershell
python scripts/verify_foundation.py

Set-Location services/connexa-api
.\gradlew.bat test

Set-Location ../../android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

To run the Android instrumented smoke test, start an emulator and run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Configuration and credentials

The configuration templates intentionally contain blank values. Use `services/connexa-api/.env.example` as a reference when an integration is ready, then provide real values through your local environment or deployment secret manager. The foundation does not load a `.env` file automatically.

Do not commit `.env` files, service-account files, keystores, private tokens, or production endpoints. Connexa keeps all privileged integration work on the server side.

## Current API surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/actuator/health` | Process health for local checks and deployment probes. |
| `GET` | `/api/v1/platform/status` | Non-sensitive service status with request ID. |
| `GET` | `/api/v1/events` | Contract-first empty event collection with pagination. |
| `GET` | `/api/v1/events/{eventId}` | Event lookup boundary; unknown IDs return a standard problem response. |

The complete schema is maintained in [contracts/openapi/connexa-v1.yaml](contracts/openapi/connexa-v1.yaml).

## Engineering rules

- Treat the OpenAPI contract as the public interface source of truth.
- Store all timestamps in UTC and preserve each event's IANA time zone.
- Use opaque UUID identifiers and optimistic revision numbers.
- Generate a request ID for every API response and preserve it in logs.
- Validate event data before any later publishing or notification workflow.
- Keep unavailable integrations disabled rather than replacing them with hard-coded credentials.
