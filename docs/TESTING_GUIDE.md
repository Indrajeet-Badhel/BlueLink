# BlueLink build and two phone testing guide

## Automated tests

Standard command after dependency resolution:

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
```

The isolated JVM protocol suite also runs without a Gradle daemon using `scripts/Test-Protocol.ps1`. Supply a JDK home and an extracted Gradle 9.6.0 distribution, plus an optional populated dependency cache. This fallback compiles with the Kotlin compiler bundled with that distribution, so it validates protocol logic separately from the application's pinned Kotlin compiler. Actual outcomes are in `BUILD_STATUS.md`.

## Installation

On both Android API 24+ phones, install `dist/BlueLink_2.0.apk` after a successful build. Enable installation from your chosen trusted file source when prompted. Alternatively enable USB debugging, approve your computer, and run:

```powershell
adb devices
adb -s <PHONE_A_SERIAL> install -r dist/BlueLink_2.0.apk
adb -s <PHONE_B_SERIAL> install -r dist/BlueLink_2.0.apk
```

Use the same signed APK for the first demonstration. A signature mismatch can block updates; do not remove an existing installation without considering local data. USB and a computer are not required for subsequent Bluetooth messaging.

## Setup and pairing

1. Enable Bluetooth and keep BlueLink visible on both phones. Wi-Fi and mobile data may be disabled.
2. Grant Nearby devices on API 31+, including CONNECT, SCAN and ADVERTISE as requested. On API 24â€“30 grant Location for scanning and enable system Location if discovery is empty.
3. On A choose **Host a link**. The status should show **Waiting for connection**.
4. On A choose **Make this phone discoverable** and approve the temporary visibility prompt.
5. On B choose **Find a peer**, then select A. The app cancels discovery before connecting.
6. Compare and accept Android pairing codes. Check the notification area if a pairing request is not visible.
7. Wait for **Connected** on both phones. The app opens Chat after HELLO succeeds.

If discovery does not find A, pair A and B in Android Settings, return to BlueLink, start host mode on A again, and choose A from B's paired-device list. Pairing alone does not establish the app connection. Discoverability is temporary; paired connections do not require a fresh scan.

## Messaging and diagnostics

Send a message A to B and B to A; verify exact Unicode text and a single received bubble. Delivered means a peer application ACK, not human reading. Send simultaneously from both phones, use Lab to Ping, and check event counters. Goodput is unique payload bytes divided by elapsed monotonic time since reset, excluding frame overhead and retransmission bytes.

Disconnect, reconnect and repeat. Reverse host/client roles. Disable Bluetooth on one phone and verify recovery; shutdown may be detected by I/O or the heartbeat deadline. A failed send can have reached the recipient if its ACK was lost, so inspect the other phone before resending.

## Planned physical result sheet

Tester/date: __________. Build/version: __________. Phone A model/API: __________. Phone B model/API: __________. Host role: __________. targetSdk/compileSdk: __________.

Actual result cells are intentionally blank until physical tests are executed.

| Test | Expected result | Actual result | Pass/Fail | Remarks |
|---|---|---|---|---|
| Installation | App opens on both phones | | | |
| Permission approval | Action proceeds | | | |
| Permission denial/revocation | Recovery message, no crash | | | |
| Discover and pair | Host found, Android pairing completes | | | |
| A host B join | Both reach protocol ready | | | |
| A to B text | One message and application ACK | | | |
| B to A text | One message and application ACK | | | |
| Unicode | Text unchanged | | | |
| Simultaneous send | Both delivered without deadlock | | | |
| Ping | Matched PONG and measured RTT | | | |
| Statistics reset | Counters reset; messaging continues | | | |
| Graceful disconnect | Both leave ready state | | | |
| Bluetooth disabled | Failure detected, recovery possible | | | |
| Reconnect | New session exchanges text | | | |
| B host A join | Reversed roles work | | | |
| Background/recreation | Documented lifecycle behavior, no leaked socket | | | |
| Mixed supported Android releases | Shared BLP version works | | | |
| Different manufacturer pair | Same workflow works | | | |
| Large font and landscape | Readable controls and navigation | | | |
| TalkBack | Meaningful controls and status | | | |

Controlled corruption, lost ACK and peer incompatibility should also be exercised in the JVM harness; RF interference is not a reliable way to generate CRC failures because Bluetooth already detects and retries errors below BLP.

## Troubleshooting

| Symptom | Action |
|---|---|
| No devices found | Confirm host visibility, scan permissions, Bluetooth and older Android Location settings; pair in Settings as a fallback |
| Paired but cannot connect | Start host mode, use matching service UUID and secure socket mode, stop discovery, then retry |
| Pairing waits indefinitely | Inspect both phones for system pairing notifications; cancel and retry if approval timed out |
| Socket opens but not Connected | Inspect HELLO timeout, protocol capabilities and matching application release |
| App leaves connection on screen lock | This release is foreground only; return, host and reconnect |
| Unconfirmed send | Check recipient, ACK counters and redacted logs before manually resending |
| Missing SDK | Install platform 37 through SDK Manager and select a compatible JDK |
| Gradle daemon localhost blocked | Use the documented in-process local build script; missing dependency downloads still need network |

## Compatibility policy

The initial device support floor is API 24. The app uses runtime permission branching and public Bluetooth socket APIs. Phones need compatible BLP envelopes, not identical Android versions. Keep compileSdk, targetSdk, AGP, wrapper, JDK and Kotlin versions pinned and tested as a set. Do not claim arbitrary SDK combinations or untested future OS releases are supported.

Suggested coverage: API 24 with a modern phone; API 30 with API 31+; two modern phones from different manufacturers. Swap host roles. Emulator-only tests do not validate physical RFCOMM connectivity. Generic Android Classic RFCOMM is not an iPhone transport.


## BlueLink 2.0 physical acceptance

Use three Android phones to validate group relay. On A create a group and enable visibility; B and C find and connect to A. Confirm roster updates and canonical sender names on all phones. Send B to C through A. Remove B and verify it can no longer contribute. Close A and verify clients leave the room. Repeat with swapped hosts, then add further peers up to the configured four-client limit on capable hardware.

Create single-select and multi-select polls; vote from B/C, attempt duplicate and changed votes, close the poll and test expiration. Distinguish host acceptance from all-member result receipt. Test in a private conversation as well.

Send PDF, TXT and DOCX documents both directions. Accept, reject and cancel offers. Confirm progress, correct saved bytes and SHA-256. In a group send B to A, accept/save on A, then accept the forwarded offer on C. Continue chat during transfer. Disconnect mid-transfer and confirm failure rather than a false completed state.

On a supported phone explicitly Host SOS for five minutes. On an unpaired second phone Scan SOS without joining chat. Verify category/title, RSSI label, GATT details, optional typed location disclosure, explicit stop and expiry/stale removal. Test several hosts and unsupported advertising hardware. Keep scanners foreground. Test denied/revoked permissions and radio disable. Clock skew is a separate TTL limitation.

Play each arcade game; verify controls, scoring or completion, restart, background pause and persisted high scores. Check TalkBack, large fonts, small-screen portrait and landscape, and Android document picker recreation. These are device acceptance steps, not implied automated passes.

| Test | Expected | Actual | Pass/Fail | Evidence |
|---|---|---|---|---|
| Three-phone group relay | Correct sender, single message on peers | | | |
| Member removal/host closure | Roster and connection recover | | | |
| Poll create/vote/change/expiry | Coordinated valid counts | | | |
| Document accept/save/hash | Exact file, verified completion | | | |
| Document reject/cancel/disconnect | Correct end state and cleanup | | | |
| Group document forwarding | Host verifies before re-offering | | | |
| SOS without pairing | Beacon visible, details obtainable | | | |
| Multiple beacons/expiry | Independent cards and removal | | | |
| Unsupported BLE advertising | Clear failure, no false active state | | | |
| Four games and lifecycle | Playable, pause/restart, scores retained | | | |
| Accessibility and screen layouts | Readable controls and content | | | |
