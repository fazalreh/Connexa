# ADR 0001: Use Java 17 for Android and API foundation

## Status

Accepted

## Context

Connexa needs a native Android application and a server-side API that can be developed, tested, and reviewed with a coherent language baseline.

## Decision

Use Java 17 for Android source compatibility and the Spring Boot API runtime. Android uses XML layouts, AndroidX, and Material Components. The API uses Spring Boot 4.1, Spring MVC, Validation, and Actuator.

## Consequences

- The build requires JDK 17 or later compatible tooling.
- Android and server-side contributors share Java conventions and domain terminology.
- The repository uses separate Gradle wrappers so Android and API builds remain independently executable.
- Kotlin, Flutter, and JavaScript are not introduced during the foundation milestone.
