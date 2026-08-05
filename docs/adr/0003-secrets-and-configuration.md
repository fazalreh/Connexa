# ADR 0003: Keep credentials out of source and mobile builds

## Status

Accepted

## Context

The platform will later connect to protected services. Committing credentials or embedding privileged values in Android binaries would expose them to users and source history.

## Decision

Use blank `.env.example` templates as configuration documentation. Real values live only in ignored local or deployment-specific secret stores. Android receives only a non-sensitive API base URL. Privileged integration calls remain server-side.

## Consequences

- The foundation runs without private accounts.
- Missing optional integrations stay disabled rather than causing hidden fallbacks.
- Credential rotation does not require a mobile-app release.
- CI performs a repository-level configuration and secret-pattern check.
