# Connexa API

This module provides the contract-first HTTP foundation for Connexa. It runs without any live Firebase, AI, mail, or notification account.

## Run locally

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

The local profile exposes a safe in-memory event catalog. It is intentionally empty until a later milestone adds approved data sources and persistence adapters. The `.env.example` file documents future environment-variable names; it intentionally contains no values and is not loaded automatically.

## Verify

```powershell
.\gradlew.bat test
```

The API contract is located at `../../contracts/openapi/connexa-v1.yaml`.
