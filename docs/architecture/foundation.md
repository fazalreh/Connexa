# Foundation architecture

## Purpose

The foundation creates one stable boundary between the Android application and server-side work before discovery, organizer operations, ingestion, AI, or notifications are introduced.

```text
Android application
        |
        | HTTPS / JSON / OpenAPI 3.1
        v
Connexa API
  |-- API layer: versioned routes, request IDs, problem responses
  |-- Application layer: use-case orchestration
  |-- Domain layer: event and user rules
  `-- Infrastructure layer: local-safe adapters
```

## Boundaries

| Boundary | Rule |
| --- | --- |
| Android to API | The app uses only the versioned HTTP contract. It does not embed administrative credentials or service-account data. |
| API to integrations | Firebase, AI, mail, and notification vendors are accessed only through server-side adapters added in later milestones. |
| Domain to infrastructure | Event and user rules do not depend on a database, mailbox, or vendor SDK. |
| Public API | Every response includes an `X-Request-Id`; known failures use `application/problem+json`. |

## Foundation behavior

1. The Android shell resolves a non-sensitive local API endpoint.
2. The API creates or preserves a safe request ID.
3. The API exposes health and platform-status responses.
4. The event route returns a stable empty page through an in-memory catalog boundary.
5. Requests with invalid time ranges and unknown event IDs receive traceable problem responses.

## Data rules

- IDs are UUIDs.
- API timestamps are UTC instants.
- `timeZone` carries an IANA zone such as `Asia/Karachi` for human display and calendar behavior.
- An event's `endsAt` value must be after its `startsAt` value.
- `revision` is non-negative and reserved for optimistic concurrency in later write operations.
- Public event states are defined before persistence is introduced, preventing schema drift.

## Deliberate exclusions

The foundation does not activate authentication providers, Firebase, mail collection, AI processing, push notifications, or external storage. Their configuration keys are blank by design, and the API remains local-safe until those capabilities are introduced deliberately.
