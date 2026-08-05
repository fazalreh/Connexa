# Local development runbook

## 1. Install prerequisites

- JDK 17
- Android Studio with Android SDK Platform 37 and Build Tools 36.0.0
- A supported Android emulator for instrumented tests
- Python 3.10 or later for repository checks

## 2. Verify the repository configuration

From the repository root:

```powershell
python scripts/verify_foundation.py
```

The command checks required foundation files, blank integration placeholders, and obvious secret-bearing files. It does not contact external services.

## 3. Run the API

```powershell
Set-Location services/connexa-api
.\gradlew.bat test
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Check `http://localhost:8080/actuator/health` and `http://localhost:8080/api/v1/platform/status`.

## 4. Run Android checks

```powershell
Set-Location ../../android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

For the instrumented smoke test, start an emulator and run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 5. Configure later integrations

Do not add credentials until the corresponding milestone starts. When credentials are available, use `.env.example` as a variable-name reference, supply values through ignored local configuration or the deployment secret manager, verify access with a narrow integration test, and rotate any value that was exposed accidentally.
