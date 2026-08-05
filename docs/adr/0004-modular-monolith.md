# ADR 0004: Start with a modular monolith

## Status

Accepted

## Context

The first release needs a small number of clear business boundaries, not independently deployed services with operational overhead.

## Decision

Use one Spring Boot API with package boundaries for API, application, domain, configuration, and infrastructure. Introduce an interface at each integration boundary before a vendor adapter is added.

## Consequences

- The system can be tested and deployed as one unit during early delivery.
- Event, identity, ingestion, AI, and notification capabilities can evolve behind interfaces.
- A future service split must be justified by independent scaling, ownership, or reliability needs.
