# Connexa — AI-assisted development log

**Arbisoft Internship · Fazal Rehman · Mentored by Waleed Khalid**

Every line of Connexa was written inside an agentic AI coding environment. This log records
the significant AI interactions across all three phases: what the work item was, what the
model produced, where it was wrong, and what correction was applied.

It is written as an engineering record rather than a transcript. What matters for review is
not the wording of a request but the decision that came out of it — what was accepted, what
was rejected, and the reason. Where AI output was wrong, the entry says so plainly and gives
the fix, because those are the entries with something to teach.

---

## How to read this log

Each entry follows the same shape:

| Field | Meaning |
|---|---|
| **Work item** | The task put to the model |
| **Produced** | What came back |
| **Problem** | What was wrong with it, if anything |
| **Correction** | What was actually shipped, and why |

A ✅ marks output accepted close to as-generated. A ⚠️ marks output that was wrong in a way
worth remembering.

---

# Phase 1 — Fundamentals

The fundamentals phase established the shape of the system: a native Android client, a
Spring Boot API, and a contract between them written before either side grew features.

### ✅ Contract-first API definition

**Work item.** Define the client/server agreement as an OpenAPI 3.1 document before writing
controllers, so the two halves could be built against one source of truth.

**Produced.** A versioned contract at `contracts/openapi/connexa-v1.yaml` covering the event
catalogue, pagination, and a shared RFC 7807 problem response.

**Correction.** Accepted, with one rule added by hand: the contract is authoritative, and
validation limits in code must match it exactly. Event title and summary are capped at 160
and 500 characters in both the contract and the Java validation annotations. The Android
assistant field counts UTF-16 `String.length()` specifically because that is what the server
counts — a client that measures differently rejects text the server would accept, or worse,
accepts text the server will reject after the user has typed it.

### ⚠️ Layered architecture with adapter boundaries

**Work item.** Structure the API so that no privileged integration is reachable from the
client, and so an unconfigured deployment cannot silently do the wrong thing.

**Produced.** A `domain` / `application` / `infrastructure` split with interfaces at the
boundary — `IdentityVerifier`, `AssistantGateway`, `EventCatalog` — and in-memory
implementations behind them.

**Problem.** The first version registered the real adapter and the fallback as ordinary beans
of the same type. Spring cannot choose between two candidates, and the whole context fails to
start. The failure was at least loud; a quieter version of the same mistake would have been a
default silently winning over a configured adapter.

**Correction.** Every adapter pair is now selected by an explicit configuration mode, and the
default is the one that refuses. `CONNEXA_IDENTITY_MODE` defaults to `rejecting`,
`CONNEXA_ASSISTANT_MODE` to `disabled`, `CONNEXA_PERSISTENCE_MODE` to `in-memory`. An
unconfigured deployment fails closed with a `401` rather than serving an empty success. This
is the single most reused idea in the codebase — every capability added later followed it.

---

# Phase 2 — Agentic AI

Phase 2 established the primitives that the assistant and the announcement pipeline are built
from: tool boundaries, structured output, memory, and the discipline of treating a model
reply as untrusted input.

### ⚠️ Where the model call is allowed to live

**Work item.** Add an event assistant that answers questions about what is on.

**Produced.** A first design that called the AI provider from the Android app with the API
key supplied by the client.

**Problem.** This is the most serious error the AI made in the project, and it looked
completely ordinary. An Android package is readable by anyone who installs it. A key shipped
inside one is a published key — extractable in minutes, billable to the project, and
impossible to rotate without shipping a new release to every device.

**Correction.** Android never holds a provider key and never calls a provider. It calls
`POST /api/v1/assistant/messages` on the Connexa API, and the server holds the key and makes
the call. The rule is enforced by structure, not by discipline: no provider SDK is on the
Android dependency list, so a well-meaning change cannot quietly reintroduce the call.

### ✅ Grounding the assistant so it cannot invent events

**Work item.** Stop the assistant from answering with events that do not exist.

**Produced.** A retrieval step that puts real candidate events in the model's context, and a
citation format for referring to them.

**Problem addressed.** A model asked to return event IDs will produce well-formed IDs for
events that were never in its context. A confidently cited event that does not exist is worse
than no answer, because a user has no way to tell the difference.

**Correction.** Citations are **index-based, not identifier-based**. The context is a numbered
list, the model may only cite `[1]`, `[2]`, and each index is resolved back to a real event
server-side. An index outside the supplied range is dropped. The model is structurally unable
to name an event it was not shown — the guarantee comes from the format, not from asking it
nicely.

### ⚠️ Treating a model reply as untrusted input

**Work item.** Extract structured event fields — title, start time, location — from the free
text of an announcement email.

**Produced.** A single function that called the model and returned parsed JSON.

**Problem.** Two failures were bundled together. The model wraps JSON in code fences
sometimes and not others; it returns dates in whatever format the source used; and it will
happily produce a start time for an announcement that never stated one. Testing any of this
required a live provider, so in practice it would not have been tested at all.

**Correction.** `extraction.py` splits the concern in two. `parse_extraction()` is pure — it
takes a string and applies every rule about what a usable result looks like: strip code
fences, require a JSON object, validate each field, reject a date that is impossible or
absent. The network call is a thin wrapper around it. The rules that decide whether to trust
a result are exhaustively unit tested with no provider involved. **A model's output is input,
and input gets validated.**

### ✅ Vector search and a defect only arithmetic would reveal

**Work item.** Add semantic search so "quiet evening outdoors" finds relevant events without
sharing a keyword with them.

**Produced.** An embedding pipeline using `gemini-embedding-001` at 768 dimensions, with
cosine ranking over stored vectors.

**Problem.** `gemini-embedding-001` returns vectors with a norm of roughly 0.59, not 1. Cosine
similarity computed without normalising follows vector *magnitude* as much as direction, so
ranking silently degrades — results are plausible, just wrong, which is the hardest kind of
bug to notice.

**Correction.** Vectors are normalised before storage and comparison. A second finding came
out of the same work: real scores cluster between 0.70 and 0.78, so ordering is meaningful but
the absolute number is not. **Raw similarity percentages are never shown in the UI** — "78%
match" reads as precision the number does not have.

Verified against live data: *"quiet evening outdoors"* returned **Rooftop Film Night** and
**Open Mic Poetry Evening**. The top result shares no word at all with the query — its
relevance is carried entirely by meaning.

---

# Phase 3 — Build

### ⚠️ Migrations that never ran

**Work item.** Add PostgreSQL persistence with Flyway migrations.

**Produced.** Migration files and datasource configuration.

**Problem.** Spring Boot 4 moved Flyway autoconfiguration out of `spring-boot-autoconfigure`.
Without `spring-boot-starter-flyway` on the classpath, migrations do not run and nothing
complains — the application starts cleanly and meets an empty database.

**Correction.** Added the starter explicitly. The schema is now ten migrations, V1 to V10.
The lesson generalises: a framework major version moves things, and AI training data reflects
the previous arrangement more often than the current one.

### ⚠️ Tests that reached the live database

**Work item.** Run the API test suite locally with real configuration available.

**Produced.** Standard Spring Boot test configuration.

**Problem.** `application.yml` imports `.env.local`, and the test context inherited it — so
the suite connected to the live Neon database. Tests that mutate real data while reporting
green are worse than no tests.

**Correction.** The test task points `CONNEXA_LOCAL_ENV` at a path that does not exist, and
`HermeticTestEnvironmentTest` fails the build if a test context ever sees live credentials.
The guard is itself a test, so the protection cannot rot silently.

### ✅ Capacity that cannot oversell

**Work item.** RSVP with a capacity limit.

**Produced.** A check-then-insert: read the current count, compare to capacity, insert if there
is room.

**Problem.** That is a race. Two requests both read 29 of 30 and both insert.

**Correction.** Three levels, because one is not enough. A conditional insert that only
succeeds while capacity remains, a database constraint as the final authority, and a waitlist
that catches the overflow. The database is the arbiter — application-level checks are an
optimisation, never the guarantee.

### ⚠️ Uploads: three separate corrections

**Work item.** Let organizers attach a cover image to an event.

**Produced.** A direct-to-storage upload with a signature issued by the server.

**Problems and corrections.**

1. **The client proposed the storage name.** A name the client chooses is a name it can aim —
   including at another organizer's existing asset. The storage name is now generated
   server-side and never read from the request.
2. **SVG was in the accepted format list.** SVG is a document that can carry script, and
   anything served from the delivery host runs in that host's origin. It is refused.
3. **The returned address was trusted.** The upload result comes back *through the client*, so
   without verification any organizer could point an event cover at any URL. `MediaDeliveryUrl`
   now checks it really is the upload that was authorised. Its tests cover another site,
   another account, another asset in the same account, an injected transformation, plain HTTP,
   a lookalike hostname, and path traversal.

The through-line: **anything that arrives via the client is a claim, not a fact.**

### ✅ A public read that was accidentally private

**Work item.** Let a signed-out visitor see whether an event still has seats.

**Problem.** Seat counts sat behind the identity filter, so a browsing visitor could not tell a
full event from an open one — the exact thing that motivates signing up.

**Correction.** The gate is in `IdentityVerificationFilter`, not the controller. It now also
allows `/events/{id}/capacity`, matched as a whole path segment so `capacity/stream` stays
closed. The boundary drawn: **the count is about the event; an RSVP, a waitlist place, and a
saved event are about a person.** The live stream stays closed too, being a connection an
anonymous caller could hold open indefinitely. Verified live: `capacity` 200 anonymous,
`capacity/stream` 401, `attendance` 401, `waitlist/me` 401.

---

## Bugs AI could not have caught — found by running the app

Unit tests pass on code that is broken in ways only a device reveals. These three were found
in a guided emulator pass, and none was catchable by a test.

### ⚠️ Every RSVP control was permanently disabled

`EventDetailsActivity` used a single-thread executor. `onResume` submitted the live capacity
stream to it — a read that blocks until the server speaks, which may be never. `loadAttendance()`
was submitted afterwards and queued behind it forever. The status stayed on "Loading your event
plan…" and the controls it enables never returned.

**This was the worst defect in the project: RSVP is the core action of the app and it was dead
on every event.** The generated code was concurrent-looking and entirely reasonable line by
line. The stream now runs on its own daemon thread.

### ⚠️ The splash mark was clipped into a wedge

The system masks that slot to a circle of radius 96 about the centre. The lower-right node sat
at distance 91 with radius 21 — an extent of 112. The file's own comment claimed the artwork
stayed inside the mask; the comment was aspirational and the geometry had never been checked.

**Correction.** Worst extent is now 76, and the artwork is *computed rather than placed*. The
generator at `tools_mark_generator.py` prints the worst extent against each safe radius on
every run, so the same bug cannot recur silently.

### ⚠️ Empty and error states were invisible

All three state views on the notification inbox were constrained to `notification_list`, which
is `GONE` exactly when a state is shown — and a constraint to a `GONE` view collapses to a
point. `dumpsys` showed the error state `VISIBLE` at bounds `540,115-540,2316`: zero width. A
screen reporting an error that no one can see.

**Correction.** They anchor to the toolbar, the navigation bar and the parent edges instead.

### ⚠️ An API level that would have failed at the door

Lint, not tests, caught `Duration.toSeconds()` — API 31, on an application that supports 26.
It would have thrown on any Android 8 to 11 device, at a door, in a queue. Now `getSeconds()`.

---

## Where AI needed the most correction

Four patterns account for nearly every wrong answer:

1. **Security defaults are permissive.** Generated code puts keys where they are convenient,
   trusts values the client returned, and accepts formats it should refuse. Every boundary in
   Connexa was tightened after the fact, never before.
2. **Concurrency reads correctly and behaves wrongly.** The RSVP deadlock and the RSVP race
   were both plausible line by line. Neither is visible without asking what happens when two
   things arrive at once, or when one of them never finishes.
3. **Framework knowledge lags the current major version.** Boot 4's Flyway move, and API-level
   ceilings on newer JDK methods, both came from training data describing an earlier world.
4. **Model output is treated as trustworthy.** Left alone, generated code parses a reply
   straight into a domain object. Every such point in Connexa now has a validation layer that
   is tested without a provider.

---

## Ground rules followed throughout

- Every AI-generated plan was reviewed before implementation.
- Small, verifiable steps were preferred to large one-shot generations.
- Where output was wrong, the correction and its reason were recorded — that is what this
  document is.
- No secret was ever placed in a tracked file, and no configuration template contains a real
  value. A repository check enforces both.
