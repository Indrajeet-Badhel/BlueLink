# Complete BlueLink development prompt

Build and finish the Android application in this existing BlueLink project. Inspect the source and current BUILD_STATUS before changing anything. Keep working code and user edits. Do not present a static website or simulated Bluetooth chat as the application.

## Product

Deliver an offline peer-to-peer text messenger for two Android phones using Bluetooth Classic authenticated RFCOMM. Either phone can host; the other discovers or selects it from paired devices. Use the existing application UUID. Android must handle pairing and Bluetooth approval through public supported APIs. Both endpoints can send and receive after the BLP handshake. Do not require Internet, Wi-Fi, mobile data, accounts or a cloud server.

## Visual direction

Make the entire UI follow a Toxic Slimecore + Psychedelic Cartoon + Acid Maximalism aesthetic. The feeling is radioactive cartoon madness in a usable mobile messenger, inspired by weird 1990s cartoons, Nickelodeon slime, Y2K graphics and psychedelic underground art.

Use #B6FF00 and #78FF00 lime/green, #7200FF purple, #E5FF00 yellow and #190B29 deep plum. Create original vector slime characters, liquid gradients, irregular organic corners, sticker labels, dot-grid textures, neon borders and layered surfaces. Use chunky buttons, expressive line icons, playful heavyweight italic headlines and restrained destination transitions. Keep body text readable. Avoid strobing, excessive motion, fake activity, a generic SaaS dashboard or a corporate visual style.

Give the UI a structured navigation shell with four destinations:

- Link: a large illustrated hero, honest Bluetooth/connection status, Host a link, Find a peer, paired/discovered devices, Make this phone discoverable and cancellation/disconnect.
- Chat: connected peer, bidirectional message bubbles, timestamps, Sending/Sent/Delivered/Failed states, functional send control, keyboard behavior and a confirmed local clear action.
- Lab: measured PING/PONG RTT, average RTT, payload goodput, ACK/DATA counts, retries, duplicates, CRC failures, protocol errors, duration and safe reset.
- Log: chronological redacted events, clear and explicit export.

Preserve 48 dp touch targets, readable contrast, clear labels, font scaling, screen-reader descriptions, scrollable smaller screens, landscape/tablet behavior, keyboard insets and a calm/reduced-motion option. Visual personality must not obscure connection instructions or delivery state.

## Architecture and compatibility

Use Kotlin, Compose, ViewModel/StateFlow and an Android-independent protocol module. Keep one socket owner, one reader and one serialized writer. Run blocking Bluetooth operations outside the UI thread. Close sockets to unblock I/O, cancel timers and invalidate stale callbacks. Preserve connection ownership across configuration recreation. Do not promise background delivery unless it is implemented with the appropriate current Android service behavior.

Retain minSdk 24 if dependencies permit it. Keep a tested, pinned Gradle/AGP/Kotlin/JDK/Compose combination and an explicit compileSdk and targetSdk. Newer compile SDK does not mean phones must run that OS. Isolate platform differences with runtime API checks and permission policies. On API 24â€“30 handle legacy Bluetooth permissions and foreground scan Location requirements. On API 31+ correctly request CONNECT, SCAN and ADVERTISE when needed. Never use hidden APIs, hardcoded RFCOMM channels or automatic pairing approval. Do not describe this as guaranteed support for every SDK or future Android release. iPhone support needs a separate transport design.

## Protocol and reliability

Treat docs/PROTOCOL_SPECIFICATION.md and implemented PacketCodec as the protocol authority. Preserve the 40-byte big-endian BLP 1 header, bounded payload and CRC coverage. HELLO is the documented 12-byte binary structure. Handle fragmented/coalesced reads and reject malformed length before allocation. Validate checksum, session, required capabilities and UTF-8.

Implement independent stop-and-wait in each direction. DATA starts at sequence 1. Match ACK to the active pending session and sequence, retry boundedly, suppress duplicate display and re-ACK duplicates. A writer must be able to send ACK/PONG while awaiting its own DATA ACK. Never ACK ACKs. Preserve retry frame identity. Close on retry exhaustion to avoid sequence gaps and restart before sequence reuse. Delivered means application receipt; Failed can mean unconfirmed arrival after a lost ACK. Never automatically replay across sessions as though exactly-once delivery were guaranteed.

Use local monotonic timing for RTT, byte-accurate goodput and actual event counters. Exclude retransmissions from unique payload goodput. Keep protocol events free of private message content. CRC is not encryption; claim end-to-end encryption only if actually implemented and verified.

## Verification and delivery

Run codec, CRC, invalid length/version/type/flags/UTF-8, fragmentation, duplicate, ACK timeout, simultaneous-send, queue, handshake, stale-session, disconnect and lifecycle tests. Run Android compilation and Lint. Build and sign a debug APK. Verify actual screen layouts through an emulator or physical device when available. Install on two physical Android phones and record outcomes without inventing passes or latency figures.

Update README, BUILD_STATUS, protocol/architecture documentation, two-phone testing guide, presentation outline and viva answers to match the final implementation. Supply the APK path, source archive, exact launch/install instructions, test output and any unresolved device checks. If an environment limitation blocks compilation or installation, identify the exact failing artifact/task, preserve the complete source, and provide the reproducible recovery command. Do not call the project fully ready until build and physical acceptance criteria are satisfied.


## BlueLink 2.0 current scope

Preserve the existing UI and implemented 2.0 source. The feature authority is NEW_FEATURES.md and the group, file, SOS and protocol specifications. Retain host-plus-four-member star sessions, canonical group sender identities, 2–8 option coordinated polls, 10 MiB streaming document transfers with consent and SHA-256, separate legacy-compatible BLE SOS advertising/GATT, and four lifecycle-aware offline games. Do not replace these with placeholder cards or simulated network events. Read BUILD_STATUS before making readiness claims. Run the full expanded suite and provide a fresh signed version 2.0 APK plus a source archive. Physical Classic/BLE and screen/accessibility checks must be reported as pending unless actually executed.
