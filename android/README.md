# Connexa Android

The Android module is a native Java application shell. It holds no private integration credentials and makes no live network request in the foundation milestone.

## Configure the local API target

The default emulator target is `http://10.0.2.2:8080/`. To override it for one local command, pass this non-sensitive Gradle property:

```powershell
.\gradlew.bat -PCONNEXA_API_BASE_URL=http://10.0.2.2:8080/ :app:testDebugUnitTest
```

## Verify

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Use `:app:connectedDebugAndroidTest` with a running emulator for the UI smoke test.
