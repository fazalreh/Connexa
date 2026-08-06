# Reflection — building Connexa with agentic AI

**Arbisoft Internship · Fazal Rehman · Mentored by Waleed Khalid**

Eight weeks, one Android application, one Spring Boot API, one Python ingestion worker, and
every line written inside an agentic AI coding environment. This is an honest account of what
that was actually like: where the AI was genuinely transformative, where it was confidently
wrong, and what I would do differently.

A companion log, [`prompts.md`](prompts.md), records the individual interactions. This document
is about the pattern behind them.

---

## 1. What AI did well

### It removed the cost of starting

The expensive part of a new area was never the typing — it was the hour before the typing,
working out the shape. Scaffolding an OpenAPI contract, a Flyway migration, a RecyclerView
adapter, a JDBC store: each of these went from an afternoon to minutes. Over eight weeks that
compounded into a system with a scope I could not have reached by hand — an API with ten
migrations and a real feature surface, a native client that reaches nearly all of it, and a
separate ingestion worker.

### It was strongest where the pattern was well-established

The best output came where a correct answer is conventional: REST controllers, validation
annotations, SQL migrations, adapter interfaces, test scaffolding. In those areas I reviewed
rather than authored, and the review was usually short.

### It was a genuine teacher on unfamiliar ground

I had not used Server-Sent Events, HMAC-signed tokens, or vector similarity search before this
project. In each case the AI gave me a working example *and* an explanation I could interrogate
— and interrogating it is what turned the example into understanding. The QR error-correction
choice is a small illustration: the default level would have worked in a test, and level H was
chosen because the code is read off a phone screen at a door, through fingerprints, glare and a
cracked protector. I understood the trade-off because I asked why, not because it was generated.

### It made documentation-quality explanation cheap

Nearly every non-obvious decision in this codebase carries a comment explaining *why*, not
*what*. That is only affordable because articulating the reasoning cost almost nothing once the
reasoning existed.

---

## 2. Where AI failed

### It is optimistic about security by default

This was the clearest and most consistent failure. Left to its own defaults, generated code:

- put an AI provider key **inside the Android application**, where anyone who installs the app
  can extract it;
- **trusted a URL that came back through the client** after an upload;
- let the **client choose the storage name** for an uploaded file, which is the ability to aim
  at someone else's asset;
- accepted **SVG** as an image format, which is a document that can carry script.

None of these looked wrong. Each was ordinary, idiomatic code. The pattern is that AI optimises
for the request as stated — "let organizers upload an image" — and an attacker is never in the
request.

### Concurrency is where it is most convincingly wrong

Two of the project's worst defects were concurrency bugs that read perfectly:

The RSVP **race** — read the count, compare, insert — is the textbook check-then-act error, and
it is exactly what gets generated when you ask for a capacity limit.

The RSVP **deadlock** was worse. A single-thread executor received a blocking network read
first, so everything queued behind it forever. The result was that every RSVP control in the
app was permanently disabled — the core action of the product, dead on every event — and the
code that caused it was unremarkable. No test caught it because the units were all correct;
the defect lived in how they shared a thread.

### It does not know what it cannot see

The three worst UI bugs were invisible to the model *and* to the test suite:

- state views constrained to a `GONE` view, collapsing to **zero width** — an error message
  nobody could see;
- a splash mark whose geometry **overflowed the circular mask** into a wedge, while the file's
  own comment claimed it fitted;
- `Duration.toSeconds()`, an **API 31 method on a minSdk 26 app**, which lint caught and tests
  did not.

A model reasons about code. It does not see a rendered screen, and it will write a comment
asserting a geometric property it never checked.

### Its framework knowledge lags the current version

Spring Boot 4 moved Flyway autoconfiguration. The generated configuration reflected Boot 3, and
the failure mode was silence: the app started fine and met an empty database. Where a major
version is recent, training data describes the previous world.

### It optimises locally, not architecturally

Each piece was sound in isolation. Nothing in the generated output ever proposed the idea that
holds the system together — that every adapter pair should be selected by an explicit mode with
the *refusing* implementation as the default. That came from a mentor conversation, and it is
the decision the whole codebase now rests on.

---

## 3. Lessons learned

**1. Review at the seams, not the lines.** Line-by-line review found almost nothing; every
serious defect lived in an interaction — between two requests, two threads, a client and a
server, code and a screen. That is where attention belongs.

**2. Treat model output as untrusted input.** This applies twice over: to what an LLM returns at
runtime, and to what the coding assistant hands you. `extraction.py` is deliberately split so
the rules deciding whether to trust a reply are pure and exhaustively tested without a provider.

**3. Make the safe path structural.** "Remember not to call the provider from Android" is a rule
that decays. No provider SDK on the Android dependency list is a rule that holds. Likewise,
citations that are *index-based* make an invented event impossible rather than unlikely.

**4. Default to refusing.** Every capability has an `Unavailable*` or `Rejecting*` adapter
selected by default. An unconfigured deployment returns 401 instead of quietly serving nothing.
A system that fails closed tells you it is unconfigured; one that fails open does not.

**5. Run it. Then run it again on a device.** The single emulator pass found three real bugs in
code with a green test suite, including the one that disabled the product's core action. If I
had one process change to make, it would be this: **verify on a device continuously, not once at
the end.** Batching it was the largest avoidable risk I carried.

**6. Ask why, every time.** The difference between code I can defend and code I merely have is
entirely down to whether I asked for the reasoning and pushed back on it. Where I did, I can
explain the choice — normalising embedding vectors, error-correction level H, the capacity
boundary between public and private. Where I did not, I was carrying a liability I had not read.

---

## 4. What I would do differently

- **Device verification every few days**, not one pass at the end.
- **A security review checklist per feature**, applied at the point of writing. The same four
  failures recurred — a secret in the wrong place, a client-supplied value trusted, a format
  accepted too broadly, an identifier the client chose. A five-item list would have caught most
  of them at the point of generation.
- **Check framework version assumptions first** when a major version is recent, rather than
  after a silent failure.
- **Write the concurrency question down explicitly**: what happens if two of these arrive at
  once, and what happens if one never finishes? Both concurrency defects would have surfaced.

---

## 5. Honest summary

AI made me faster at everything and correct at nothing. Speed was real and large — Connexa's
scope is not reachable by one person in eight weeks otherwise. But every guarantee the system
now has, I put there after reviewing something that looked finished: the fail-closed defaults,
the three-level capacity guard, the verified delivery URL, the index-based citations, the
hermetic test boundary.

The skill this internship actually built was not writing code with AI. It was reading code
critically enough to find what is wrong with something plausible — and knowing which questions
(what if two arrive at once? what does the client control? what does this look like on a
screen?) reliably expose it.
