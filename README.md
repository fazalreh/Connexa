# Connexa

> An Android-first event discovery and coordination platform for connected communities.

**Arbisoft Internship Final Project**  
**Prepared by:** Fazal Rehman  
**Mentored by:** Waleed Khalid

## Product vision

Connexa will bring trusted event announcements, attendance actions, updates, reminders, and practical event guidance into one clear experience. Its purpose is to make it easier for community members to discover what is happening, understand the details, and take action without searching through scattered messages and channels.

## Current focus

The work begins with a deliberate foundation: defining the product boundaries, structuring the delivery plan, and preparing the technical areas that will be developed in sequence.

## What Connexa will deliver

- A searchable, filterable event feed for approved community events.
- Secure member and organizer access.
- Event creation, editing, publishing, cancellation, and visibility controls.
- Attendance registration, saved events, calendar views, and reminders.
- Trusted announcement ingestion from approved sources on a regular schedule.
- AI-assisted extraction of event details, with validation before publication.
- A grounded event assistant that answers from approved event records.
- Timely notifications when an event changes, is cancelled, or is approaching.

## Planned architecture

| Layer | Planned technology | Purpose |
| --- | --- | --- |
| Mobile experience | Java 11, Android SDK, XML layouts, AndroidX, Material Design | Native Android experience for members and organizers. |
| Application services | Java with Spring Boot or Java-based cloud functions | Secure APIs, business rules, validation, and scheduled work. |
| Identity and event data | Firebase Authentication and Cloud Firestore | Managed sign-in, real-time event data, and access controls. |
| Media | Firebase Storage | Event posters and supporting media. |
| Announcement ingestion | Java worker with Gmail API or IMAP | Retrieves approved announcements on a controlled schedule. |
| AI services | Gemini API through the backend | Extracts structured event details and supports grounded assistance. |
| Notifications | Firebase Cloud Messaging | Delivers reminders, updates, and cancellation notices. |
| Quality assurance | JUnit, Mockito, Espresso, Firebase Emulator Suite | Validates business logic, interface behavior, and integrations. |

## Delivery path

1. **Foundation** — establish repository structure, product rules, data model, and environment configuration.
2. **Core experience** — build authentication, member profiles, event browsing, search, filters, and event details.
3. **Organizer operations** — add event creation, media uploads, editing, publishing, attendance limits, and cancellation controls.
4. **Trusted ingestion** — connect approved announcement sources, create candidate events, and validate extracted details.
5. **Engagement** — deliver registration, saved events, calendar support, notifications, and reminders.
6. **Event intelligence** — add validated summaries and a grounded assistant using approved event records only.
7. **Quality and release readiness** — complete automated tests, security review, accessibility checks, performance testing, and release preparation.

## Core workflow

```text
Approved announcement source
        ↓
Scheduled Java ingestion worker
        ↓
Candidate event record
        ↓
AI-assisted structured extraction
        ↓
Rules, validation, and review
        ↓
Published event
        ↓
Discovery, RSVP, calendar, and notifications
```

## Working principles

- Credentials, administrative actions, and AI access remain on secure server-side services.
- Only approved sources can create ingestion candidates.
- An event is not published until required fields and validation rules are satisfied.
- Updates and cancellations produce traceable changes and timely member notifications.
- The assistant uses approved event records as its source of truth.
- Quality is verified continuously through automated and scenario-based testing.

## Repository map

```text
Connexa/
├── android/          # Android application
├── services/         # APIs, business rules, and secure integrations
├── ingestion/        # Scheduled announcement collection and normalization
├── infrastructure/   # Environment, deployment, and service configuration
├── tests/            # Automated test assets
└── docs/             # Architecture, workflows, and delivery documentation
```

## First milestone

Create the initial Android and service modules, define the event and user data contracts, configure secure environment handling, and establish the baseline test suite.
