# BlueLink 2.0 project report

## Abstract

BlueLink is an offline Android communication application with private messaging, host-coordinated group conversations, polls, consent-based document transfer, BLE SOS discovery and local games. It retains the existing Toxic Slimecore Compose interface. Bluetooth Classic RFCOMM carries the BlueLink Application Protocol, while a separate BLE advertisement/GATT subsystem provides SOS discovery without a chat connection. Automated protocol and game verification is distinct from physical radio acceptance.

## Problem statement and motivation

A local communication platform should work without accounts or a cloud message service while exposing how its networking behavior is implemented. A text UI alone does not demonstrate stream framing, peer receipt, group coordination or interrupted file reconstruction. BlueLink combines these concerns in a bounded application that can be inspected and presented as a computer networking project.

## Objectives and scope

The implemented scope is one private conversation or one star-topology group per installation, plus independent foreground SOS operations and local arcade play. A group has one host and up to four clients, subject to hardware concurrency. Documents are limited to 10 MiB. Groups do not migrate hosts, and files do not resume after connection loss. BLE SOS is a public assistance channel rather than an authenticated emergency service.

## Technology and requirements

Two Android API 24+ phones with Bluetooth Classic are required for private radio tests; three or more are needed to exercise group relay. SOS hosting additionally requires BLE advertising support. Kotlin and Jetpack Compose implement the application, ViewModel/StateFlow holds UI state, and a pure JVM module contains framing, feature domain logic and game rules. Android public socket, BLE, GATT and Storage Access Framework APIs provide platform integration.

The normal build is pinned to Gradle 9.6.0, AGP 9.4.1, Kotlin/Compose compiler 2.2.10, compileSdk 37, targetSdk 35 and Build Tools 36.1.0. The available offline verification route uses Android Studio's matching Kotlin/Compose compilers 2.3.10 and JDK 25, emits JVM 17 bytecode and packages with the same Gradle/AGP pipeline. BUILD_STATUS records actual outcomes and unresolved checks.

## Existing approaches and proposed design

Cloud messaging normally uses external infrastructure; simple serial samples may omit application-level framing and diagnostics. This is a design comparison, not a claim that every existing messenger lacks offline or reliability features. BlueLink adds an explicit protocol, per-connection reliability and inspectable events while keeping application scope limited. Mini Arcade is recreational and is not a networking contribution.

## Architecture and communication

MainActivity owns permissions, Bluetooth enablement, discoverability and document pick/save results. BlueLinkModel owns socket generations, one private link or multiple group links, coordinator state, diagnostics and lifecycle. Every link has its own Session with independent sequence and ACK state. A single writer per link prevents byte interleaving. The protocol module is independent of Android, allowing real duplex stream tests without radio hardware.

The host opens an authenticated RFCOMM service with a shared UUID. Clients stop discovery before connecting and approve Android pairing. HELLO negotiates the protocol minor and payload limit. Groups keep the listener open and initialize a member before broadcasting to it. The host canonicalizes sender identity, relays messages, tracks members and publishes roster updates. Group closure and member removal are explicit operations.

## Protocol and reliability

BLP retains a 40-byte big-endian header and a 4096-byte maximum payload. Length is validated before allocation, reads are accumulated exactly, and CRC32 covers header bytes 0–35 plus payload. Minor 0 supports private text; minor 1 enables group, poll and file packet families. Older clients remain eligible for text, and advanced operations are gated on negotiation.

Stop-and-wait is independent in each direction. One DATA or extension packet is pending while other messages wait in bounded queues. ACK starts the next send; timeout causes up to three additional attempts. Duplicates are re-ACKed without another delivery. Queued chat/control work takes precedence over waiting bulk chunks. Retry exhaustion closes the session. Delivered indicates peer application receipt; it does not indicate human reading or all-member group synchronization.

## Feature implementation

Groups use host-plus-clients star routing, assigned identities, bounded membership and recent-message deduplication. Polls support 2–8 options, single or multiple selection, optional vote changes, closure, expiration and coordinator result snapshots. Documents stream in 1024-byte chunks with receiver consent, application chunk acknowledgements and final SHA-256 before SAF saving. In a group, a host verifies a received upload before offering it onward.

SOS uses a compact 31-byte-compatible advertisement and optional scan-response title. A read-only GATT characteristic serves bounded longer details. Users explicitly activate and stop hosting or scanning. Expiry, stale-card removal, capability checks and untrusted-input validation are implemented separately from Classic messaging. Typed location sharing is optional; no GPS collection is implemented.

Snake, Sudoku, Goo Runner and Goo Blocks have independent rule models and Compose game screens. Sudoku puzzles are generated with a uniqueness solver. Collision, scoring, restart and pause behavior are implemented; scores for score-based games are saved locally. Timed game loops pause when the Activity is not resumed and cancel when the game screen is removed.

## UI and usability

The existing #B6FF00/#78FF00 lime, #7200FF purple, #E5FF00 yellow and #190B29 plum palette, original vector slime mascot, organic corners and chunky controls are preserved. Original bottom navigation remains Link, Chat, Lab and Log. Group controls are added to Link; polls and documents appear in Chat; SOS and Arcade use secondary destinations. The UI exposes permission failures, pending transfers, receiver acceptance, protocol status and actual measurements. Large-font, TalkBack and landscape acceptance still require device review.

## Performance and diagnostics

PING/PONG RTT uses a local monotonic clock, including scheduling and queue delay. Unique acknowledged/transferred payload bytes produce directional goodput; headers and retransmissions are excluded. Groups aggregate link counters; displayed RTT is a link sample, not a synchronized end-to-end client-to-client metric. Resetting statistics leaves reliability state intact. Logs omit private message bodies.

## Security and limitations

Android pairing and authenticated RFCOMM are used; the app does not add end-to-end encryption. CRC32 detects accidental corruption, while SHA-256 verifies transferred document integrity against the offered digest. Neither makes a malicious peer trustworthy. Beacons are unauthenticated public data. Sizes, queues, members, histories and scanning windows are bounded. Foreground operation is the support boundary; process death ends sessions and in-memory state.

## Testing and results

The expanded JVM suite covers framing, CRC, malformed fields, split/coalesced streams, duplex reliability, compatibility gating, two-session group relay, canonical identity, poll voting rules, file assembly and corruption/cancellation, SOS encoding/expiry and game rules. The final run and build status are recorded in BUILD_STATUS. Physical tests are intentionally left unreported until executed; no latency, throughput or radio range benchmark is invented.

## Conclusion and future work

BlueLink 2.0 implements the requested feature paths while preserving the original app structure and visual language. Release acceptance requires a built artifact, automated evidence and recorded device verification. Future work may add a reviewed foreground-service mode, authenticated SOS/application encryption, transfer resumption, optional persistence and tested host migration. These are separate enhancements rather than implied current capabilities.

## References

1. Android Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
2. Android Classic connections: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
3. Android BLE advertising: https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser
4. Android BLE scanning: https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner
5. Android GATT server: https://developer.android.com/reference/android/bluetooth/BluetoothGattServer
6. Android document access: https://developer.android.com/training/data-storage/shared/documents-files

Platform references checked during development on 13 November 2026. BLP constants and product limits are project decisions, not external benchmark findings.
