# BlueLink 2.0 verification status

Build date: 13 November 2026. Package dev.bluelink, version name 2.0.0, version code 2. Minimum API 24, target API 35, compile API 37.

| Check | Actual result |
|---|---|
| Expanded protocol and game suite | PASS: 69 JUnit tests, including original reliability tests and new feature/domain tests |
| Group relay integration | PASS in JVM: canonical message relayed across two independent duplex Sessions |
| Old protocol compatibility | PASS in JVM: minor 0 text retained; extension send rejected for that peer |
| Kotlin/Compose source compilation | PASS using matching installed compiler/plugin 2.3.10 |
| Android resources/manifests/dex/packaging | PASS using Gradle 9.6.0, AGP 9.4.1, Build Tools 36.1.0 |
| Debug APK signature | PASS: APK Signature Scheme v2; one signer |
| Package metadata | Confirmed 2.0.0 / code 2 / API 24 minimum |
| Android Lint | BLOCKED: com.android.tools.lint:lint-gradle:32.4.1 is not cached and cannot resolve offline |
| APK alignment | PASS: zipalign verification including 16 KiB native-library alignment |
| ADB installation and UI review | BLOCKED: local ADB server connection could not start in this environment |
| Two/three-phone Bluetooth and BLE tests | NOT EXECUTED; use TESTING_GUIDE physical result sheets |

APK: dist/BlueLink_2.0.apk. SHA-256:

```text
D1A6D8A102CE14EF0073122CE9B34928F0DF3FA6905F9CD0963AE46F69D758B7
```

The initial Gradle compiler path could not resolve uncached Kotlin build-tools/compiler artifacts in offline mode. Gradle worker/daemon localhost IPC is also restricted in this environment. The explicitly documented Build-Offline route compiles protocol and app as separate modules using Android Studio's matching Kotlin/Compose compiler 2.3.10 and JDK 25, emits JVM 17 bytecode, then runs the real Android packaging and signing tasks with those compiled outputs. It uses installed AAPT2 through an explicit override. The normal pinned Kotlin 2.2.10 Gradle compilation path is provided for a fully provisioned workstation and is not claimed as executed here.

Protocol tests use the Kotlin compiler 2.3.21 bundled with the available Gradle distribution and JUnit 4.13.2 in a direct JVM process, avoiding Gradle worker IPC. Test and build logs accompany the source package. Physical radio behavior, GATT long reads, manufacturer concurrency, screen layouts, accessibility and end-user flows still require device acceptance. No measured radio range, speed or latency is fabricated.
