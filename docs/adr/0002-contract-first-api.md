# ADR 0002: Define the HTTP contract before feature expansion

## Status

Accepted

## Context

The mobile client, server-side API, future ingestion workers, and future assistant must agree on event and user data without relying on informal field names or ad hoc payloads.

## Decision

Maintain `contracts/openapi/connexa-v1.yaml` as the versioned public HTTP contract. Use `/api/v1` routes, UUID identifiers, UTC timestamps, IANA time zones, stable pagination, and RFC 9457 problem responses.

## Consequences

- A route or schema change requires a contract update and test review.
- The Android module can develop against stable response shapes before live data exists.
- Later adapters may change storage without changing the public API unnecessarily.
