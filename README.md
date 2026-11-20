# BlueLink 2.0

Offline Android messaging, groups, live polls, document sharing, BLE SOS and Mini Arcade, retaining the lime/purple/plum Slimecore Compose UI.

## Install and connect

Install `dist/BlueLink_2.0.apk` on supported Android API 24+ phones. Enable Bluetooth and grant permissions. For private chat, A chooses Host a link and Make this phone discoverable; B chooses Find a peer and selects A. Accept Android pairing and wait for Connected.

For a group, A chooses Create a group on Link and starts hosting, then enables visibility. Up to four peers connect to A using Find a peer. Keep the host foreground. Member controls are on Link; polls and document attachments are in Chat. Each recipient accepts and selects a save destination for incoming documents.

SOS and Mini Arcade are secondary buttons on Link. SOS hosting requires BLE advertising hardware; Scan SOS works without Classic pairing. Choose Read full details for an optional GATT read. SOS is a public, unverified assistance beacon and is not guaranteed emergency communication.

## Build

The normal pinned build uses Gradle 9.6.0, AGP 9.4.1 and Kotlin/Compose compiler 2.2.10, with compileSdk 37, targetSdk 35, Build Tools 36.1.0 and minSdk 24. Point Android Studio or ANDROID_HOME/local.properties at your installed SDK. Use a compatible JDK.

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
```

If compiler artifacts are not cached or Gradle worker localhost connections are unavailable, `scripts/Build-Offline.ps1` uses an explicitly supplied JDK, Gradle distribution, Android SDK, Python executable and Android Studio Kotlin compiler home. It exports the resolved runtime artifacts, compiles protocol and app as separate modules with matching installed Kotlin/Compose compilers, compiles BuildConfig, runs Gradle packaging, verifies the APK signature and writes `dist/BlueLink_2.0.apk`. This verification fallback uses compiler 2.3.10; it does not claim that the normal pinned compiler path was exercised.

`scripts/Test-Protocol.ps1` also runs the JVM suite without Gradle worker IPC. Supply `-JdkHome` and `-GradleDistribution`. Actual evidence is in `docs/BUILD_STATUS.md`.

## Boundaries

One private conversation or one star group per app. Four group clients plus host, subject to radio support. Documents up to 10 MiB, four active transfers, no resume after disconnect. Public foreground BLE SOS, no automatic location collection. No Internet permission, account, cloud dependency, background delivery guarantee or application-level end-to-end encryption. Keep BlueLink open while communicating. Local score records persist; conversation state does not.

## Documentation

- `docs/NEW_FEATURES.md`: feature map and boundaries
- `docs/GROUP_COMMUNICATION.md`: multi-client architecture and setup
- `docs/FILE_TRANSFER_SPECIFICATION.md`: chunks, consent and verification
- `docs/SOS_BLE_SPECIFICATION.md`: advertisement and GATT format
- `docs/PROTOCOL_SPECIFICATION.md`: frame and extension specification
- `docs/ARCHITECTURE.md`: component and workflow diagrams
- `docs/PROJECT_REPORT.md`: current engineering report
- `docs/TESTING_GUIDE.md`: device test steps and blank result sheets
- `docs/PPT_CONTENT.md`: presentation content
- `docs/VIVA_QUESTIONS.md`: technical questions and answers
- `docs/BUILD_PROMPT.md`: reusable development instructions

The earlier Word documents are historical design drafts. Current Markdown specifications describe version 2.0.
