from pathlib import Path

root=Path(__file__).resolve().parent.parent
docs=root/'docs'
documents={
'NEW_FEATURES.md':'''# BlueLink 2.0 features

The existing lime, purple and plum design is retained. Link, Chat, Lab and Log remain the bottom navigation destinations. SOS and Mini Arcade are secondary destinations reached from Link.

| Feature | Implementation | Boundary |
|---|---|---|
| Private chat | Original DATA, ACK, CRC32 and retransmission remain | Foreground only; receipt is not a read receipt |
| Groups | Host plus up to four clients, independent RFCOMM sessions, canonical sender IDs, membership, removal and closure | Star topology; radio concurrency depends on the phones |
| Polls | 2–8 options, multi-select, change policy, optional 5/15 minute expiry, coordinator results and creator closure | Coordinator acceptance differs from all-member synchronization |
| Documents | SAF picker and save destination, 1024-byte chunks, consent, cancellation, progress, SHA-256 verification | 10 MiB per document; four active transfers; no resume after disconnect |
| Group documents | Each recipient accepts separately; host verifies and saves a member's upload before offering it onward | Host storage and recipient acceptance are required |
| SOS host | Explicit BLE advertiser plus read-only GATT details, category, optional typed location, 5/15/30 minute expiry | Requires advertising hardware; public unverified beacon |
| SOS finder | Filtered BLE scan, multiple cards, RSSI labels, stale/expired removal, optional details read | No pairing or RFCOMM required; foreground scanning, ten-minute scan window |
| Arcade | Snake, unique-solution Sudoku, original runner and falling blocks, pause/restart, local high scores | Local recreation; not a networking innovation |

Source implementation and tests do not constitute physical radio verification. See BUILD_STATUS.md for the actual build and test evidence.
''',
'GROUP_COMMUNICATION.md':'''# Group communication

Create a group from Link, enter its name and description, and choose Start hosting. The app assigns a random group ID. Choose Make this phone discoverable. Other phones use Find a peer or their paired list to connect to the host. Copy joining instructions or make the host visible to invite nearby users.

The listener stays open after accepting a client. Each accepted socket has its own BLP Session, read thread, serialized writer, ACK state, sequences, timers and negotiated minor version. Capacity is host plus four peers; actual concurrent RFCOMM support must be checked on the chosen phones.

After BLP 1.1 negotiation, the host queues GROUP_CREATE with the client's assigned ID before marking that link eligible for broadcasts. This prevents a new member from receiving a group message before its group initialization. GROUP_JOIN supplies the display name; GROUP_MEMBER_UPDATE publishes the canonical member roster.

The host binds sender identity to the connection rather than trusting a sender field from a client. It validates message length and deduplicates recent application message IDs. Accepted messages are echoed to the sender and sent to other ready members. Messages include sender name and a wire timestamp, while the conversation displays local receipt time.

The host sees per-message application ACK counts. A client sees Received by host and then Relayed by host when the echo arrives. These labels do not promise that every participant has received or read the message. Host departure closes the room; automatic host migration is not implemented.

Remove a member in the Link group panel. Close a group with Disconnect. Leaving the app foreground also closes its active group. Poll and file traffic shares each session; chat and control operations are scheduled before waiting bulk file chunks. Queues and retained histories are bounded.

```mermaid
flowchart TD
  H["Host and coordinator"] --- A["Member A session"]
  H --- B["Member B session"]
  H --- C["Member C session"]
  H --- D["Member D session"]
```

There is no mesh routing or direct client-to-client socket. Joining the correct paired host is an Android Bluetooth trust decision; group IDs are not passwords.
''',
'FILE_TRANSFER_SPECIFICATION.md':'''# Document transfer specification

Choose Attach document in Chat. Android's document picker grants access to PDF, TXT, DOCX, DOC or RTF content. The app stages the source in a private cache using an 8192-byte buffer, checks the 10 MiB limit while reading, and computes SHA-256 without loading the complete document into memory. Empty files are rejected.

FILE_OFFER contains id, safe display name, MIME type, size, chunk count and lowercase SHA-256. The recipient chooses Accept and save or Reject. Accept launches Android CreateDocument; no broad storage permission is required. An accepted transfer writes to a private temporary file first.

| Type | Record fields | Behavior |
|---|---|---|
| FILE_OFFER 48 | id, name, mime, size, chunks, sha | Bounded metadata; prompts recipient |
| FILE_ACCEPT 49 | id | Starts sender's streaming input |
| FILE_REJECT 50 | id | Ends an unaccepted offer |
| FILE_CHUNK 51 | id, index plus binary blob | Zero-based index; at most 1024 bytes |
| FILE_ACK 52 | id, index | Confirms the chunk was written to the receive stream |
| FILE_COMPLETE 53 | id; verified=true on receiver response | Sender requests finalization; receiver checks hash, saves through SAF and confirms |
| FILE_CANCEL 54 | id | Explicit cancellation |
| FILE_ERROR 55 | id | Storage, format or integrity failure |

All these packets also use BLP framing, CRC, sequence ACKs and bounded retries. FILE_ACK is an additional application response after file processing, distinct from the BLP ACK. Only one file chunk per transfer waits for FILE_ACK; the sender does not queue the entire file. Duplicate earlier chunks are acknowledged without another write. Unexpected indices or lengths are rejected. The final chunk may be shorter than 1024 bytes.

The recipient requires the complete expected chunk count and exact SHA-256 before copying the reconstructed document to the selected SAF destination. Open file uses a temporary read grant and an installed document viewer. Cancellation or failure closes streams and removes the incomplete cache and newly created receive document where possible. Sender success means the peer reports a verified save, not a human opening the document.

Offers expire after approximately 120 seconds without activity; accepted transfers time out after approximately 45 seconds without progress. There are at most twelve pending/active records, four active streams, and a bounded incoming work queue. Disconnect fails the transfer; resumption is not implemented. Private cache copies are removed on controller cleanup. Histories and temporary files are subject to app-process lifetime.

For a group, the host sends offers independently to members. A member upload first goes to the host. Once the host accepts, reconstructs, verifies and saves it, the host offers the cached verified document to the other members. A recipient can reject its own offer. This deliberately avoids forwarding unchecked chunks to all participants.
''',
'SOS_BLE_SPECIFICATION.md':'''# SOS BLE specification

SOS uses a separate BLE subsystem. It neither requires nor impersonates an RFCOMM conversation. The user explicitly selects Host SOS or Scan SOS. Nothing broadcasts automatically on startup. The host supplies title, message, optional name, category and an optional manually typed location with a separate sharing checkbox. GPS coordinates are never collected.

## Legacy advertisement

Service-data UUID: 5d6e0a10-1f9c-4d13-b4f1-c0a117451001. Its ten-byte service payload fits ordinary legacy advertising: three flag bytes plus eighteen service-data overhead/UUID bytes plus ten payload bytes equals 31 bytes.

| Payload offset | Bytes | Value |
|---|---:|---|
| 0 | 1 | Beacon format version 1 |
| 1 | 1 | Category index 0–5 |
| 2 | 4 | Random nonzero beacon ID, big endian |
| 6 | 4 | Unsigned expiry in UTC epoch minutes, rounded upward |

The scan-response service-data UUID 5d6e0a11-1f9c-4d13-b4f1-c0a117451001 contains at most 13 valid UTF-8 bytes of title. A finder may receive only the primary advertisement; it then displays SOS details available and the category. Unlimited emergency text is not placed in advertising packets.

The host stops at its selected 5, 15 or 30 minute local deadline. The rounded on-air expiry can be up to one minute later; scanner stale detection also removes disappeared beacons. TTL filtering depends on reasonably accurate device clocks.

## Details retrieval

The primary UUID also identifies a GATT service. Read-only characteristic 5d6e0a12-1f9c-4d13-b4f1-c0a117451001 contains a bounded feature Record with title, message, name, category and location. Combined encoded size is at most 512 bytes. Read requests honor offsets and connection MTU; Android long-read behavior must be verified on the selected phone pair. The finder explicitly requests details and closes GATT on completion, failure or a 15-second deadline. Pairing is not requested by this characteristic.

## Lifecycle and validation

Advertising is shown active only after AdvertiseCallback reports success. Missing BLE advertising support, scanner failure or permission denial is shown explicitly. A GATT server is published before advertising begins. Discovery uses a service-data version filter and low-latency scanning while foreground, with a ten-minute automatic scan stop.

The finder rejects invalid size, format, category, zero IDs, expired TTL and implausible TTL beyond 61 minutes. It retains at most fifty cards, limits duplicate processing per ID to once per 500 ms, and removes cards after 45 seconds without another observation. Multiple beacons can be displayed. RSSI is labeled Nearby signal, Signal detected or Weak signal; no exact meter distance is calculated.

## Permissions and safety

API 31+ uses Nearby devices permissions and discovery Location permission for SOS scanning/proximity. Legacy scanning requests foreground Location. The manifest does not assert neverForLocation because that assertion can filter beacons and this mode reports proximity. No background Location access is requested. A finder does not require a Classic Bluetooth bond.

These are public, unauthenticated advertisements and untrusted details. A BlueLink UUID is not proof of identity or of a real emergency. Bluetooth state, hardware, permissions, radio conditions, clock settings and Android foreground restrictions affect availability. This is an assistance mechanism, not guaranteed emergency communication or an emergency-services replacement.
''',
'PROJECT_REPORT.md':'''# BlueLink 2.0 project report

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
'''
}
for name,text in documents.items():
 (docs/name).write_text(text,encoding='utf-8')

readme='''# BlueLink 2.0

Offline Android messaging, groups, live polls, document sharing, BLE SOS and Mini Arcade, retaining the lime/purple/plum Slimecore Compose UI.

## Install and connect

Install `dist/BlueLink_2.0.apk` on supported Android API 24+ phones. Enable Bluetooth and grant permissions. For private chat, A chooses Host a link and Make this phone discoverable; B chooses Find a peer and selects A. Accept Android pairing and wait for Connected.

For a group, A chooses Create a group on Link and starts hosting, then enables visibility. Up to four peers connect to A using Find a peer. Keep the host foreground. Member controls are on Link; polls and document attachments are in Chat. Each recipient accepts and selects a save destination for incoming documents.

SOS and Mini Arcade are secondary buttons on Link. SOS hosting requires BLE advertising hardware; Scan SOS works without Classic pairing. Choose Read full details for an optional GATT read. SOS is a public, unverified assistance beacon and is not guaranteed emergency communication.

## Build

The normal pinned build uses Gradle 9.6.0, AGP 9.4.1 and Kotlin/Compose compiler 2.2.10, with compileSdk 37, targetSdk 35, Build Tools 36.1.0 and minSdk 24. Point Android Studio or ANDROID_HOME/local.properties at your installed SDK. Use a compatible JDK.

```powershell
.\\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
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
'''
(root/'README.md').write_text(readme,encoding='utf-8')

protocol=docs/'PROTOCOL_SPECIFICATION.md'
text=protocol.read_text().replace('BlueLink Application Protocol 1.0','BlueLink Application Protocol 1.1').replace('currently negotiate minor 0','negotiate minor 1, or minor 0 with an older peer').replace('| Maximum supported minor | 0 |','| Maximum supported minor | 1 |')
text+='''

## Version 2.0 application extensions

The 40-byte envelope, original type codes 1–7, CRC coverage, session ID and ACK rules remain unchanged. HELLO advertises minor range 0–1. The chosen minor is the smaller supported maximum when ranges intersect. Minor 0 gates off advanced packets and permits private text. Minor 1 enables the following reliable packet families. All extension packets carry sequence numbers and use the same independent stop-and-wait ACK mechanism as DATA. Unlike DATA, they carry binary Records and are not decoded as UTF-8 messages.

| Types | Codes |
|---|---|
| GROUP_CREATE, GROUP_JOIN, GROUP_LEAVE, GROUP_MESSAGE, GROUP_MEMBER_UPDATE, GROUP_CLOSE | 16–21 respectively |
| POLL_CREATE, POLL_VOTE, POLL_UPDATE, POLL_CLOSE | 32–35 respectively |
| FILE_OFFER, FILE_ACCEPT, FILE_REJECT, FILE_CHUNK, FILE_ACK, FILE_COMPLETE, FILE_CANCEL, FILE_ERROR | 48–55 respectively |

A Record begins with encoding version byte 1 and a one-byte field count, at most 32. Each field has an unsigned one-byte key byte length (1–64), strict UTF-8 key bytes, an unsigned two-byte value byte length (0–2048), and strict UTF-8 value bytes. Duplicate keys are rejected. An unsigned two-byte blob length and that many binary bytes follow the fields. Blob size is at most 3072; total Record size must still fit the negotiated BLP payload limit of at most 4096. Extra bytes, unknown Record versions and truncated values are rejected.

GROUP_CREATE fields are id, name, description, you and host. GROUP_JOIN supplies name. Roster fields are group and member.<assigned-id> entries. GROUP_MESSAGE contains id, text, sender, name, group and sent; host assigns sender and name from the connection. The recent ID cache is bounded to 1000 entries, while Session sequence deduplication remains per connection.

POLL_CREATE carries id, creator, question, count, option0..7, multiple, change, expires, closed and snapshot counts. Group host assigns creator from the connection. POLL_VOTE carries id and comma-separated selected option indices. Coordinator POLL_UPDATE adds total, votes0..7 and optionally voter/accepted for application vote feedback. POLL_CLOSE carries id and is authorized against the creator or local host. Expiry is wall-clock epoch milliseconds, coordinated by the creator for private polls or by the group host.

FILE_* record fields and SHA-256 completion are documented in FILE_TRANSFER_SPECIFICATION.md. FILE_CHUNK is bulk priority; queued chat and application controls are selected first. There is still only one outstanding reliable packet per sending direction. BLP ACK means frame/application dispatch acceptance; FILE_ACK confirms receiver chunk processing, and FILE_COMPLETE with verified=true confirms the peer reports a saved verified document.

BLE SOS never uses this RFCOMM envelope in advertisements. Its ten-byte payload and GATT details are specified independently in SOS_BLE_SPECIFICATION.md.
'''
protocol.write_text(text,encoding='utf-8')

test=docs/'TESTING_GUIDE.md'
text=test.read_text().replace('app/build/outputs/apk/debug/app-debug.apk','dist/BlueLink_2.0.apk')
text+='''

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
'''
test.write_text(text,encoding='utf-8')
arch=docs/'ARCHITECTURE.md'
text=arch.read_text().replace('BlueLinkModel.kt','BlueLinkController.kt')
text+='''

## Version 2.0 composition

```mermaid
flowchart TD
  UI["Existing Compose shell and feature screens"] --> VM["BlueLinkModel coordinator"]
  VM --> G["GroupHub and per-peer Sessions"]
  VM --> P["Poll coordinator and snapshots"]
  VM --> F["TransferEngine and SAF"]
  UI --> S["SosEngine BLE and GATT"]
  UI --> A["Independent game models"]
  G --> W["RFCOMM per-peer writers"]
  P --> W
  F --> W
```

Per-peer sessions isolate ACK and sequence state. Feature dispatch and file work have bounded admission queues. Incoming file chunks are processed on the file worker; group upload forwarding begins after full verification. BLE lifetime, GATT clients and beacon expiry are managed independently of Classic links. Games have no dependency on network transport.
'''
arch.write_text(text,encoding='utf-8')

viva=docs/'VIVA_QUESTIONS.md'
text=viva.read_text()
extra=[('How does BLE differ from Classic here?','Classic RFCOMM carries conversations and documents; BLE advertisements discover SOS without a chat connection.'),('What is GATT?','A BLE service and characteristic model. The SOS details characteristic is read-only.'),('Why not advertise a full SOS message?','Legacy advertisement space is bounded to 31 bytes including fields, so longer details require an optional GATT read.'),('Does finding SOS require pairing?','No. The advertisement and open read-only details characteristic do not request a Classic bond.'),('What does RSSI prove?','Only received signal strength at that observation. It does not establish exact distance or trust.'),('Why a star topology?','A host can coordinate canonical membership and relay over separate RFCOMM sessions without claiming mesh routing.'),('What if the group host leaves?','The group closes. Host migration is not implemented.'),('How are forged sender names handled?','The group host derives identity from the client connection and emits canonical sender fields.'),('Who counts group poll votes?','The host; each member ID has one recorded selection set subject to the change policy.'),('Does a poll result mean everyone synchronized?','No. Coordinator acceptance and queued broadcasts are separate from every member acknowledging an update.'),('Why chunk documents?','Streaming bounded chunks controls memory and allows chat/control scheduling between file operations.'),('Why SHA-256 if CRC32 exists?','CRC covers frames; SHA-256 checks the reconstructed complete document against the offered digest.'),('What permissions select and save documents?','Storage Access Framework grants specific URI access, avoiding broad storage permissions.'),('What does file completion mean?','The recipient reports a complete hash-verified file saved to the selected destination.'),('Can transfers resume after disconnect?','No. This release fails them and requires a fresh consent-based transfer.'),('Is a valid SOS UUID authentication?','No. Advertisements and details remain untrusted public inputs.'),('How is SOS battery use bounded?','Explicit foreground activation, timed scans, advertisement expiry and stop controls.'),('What does neverForLocation affect?','It is a location-use assertion and can filter some beacons. SOS scanning instead requests the relevant discovery permissions.'),('How are game loops separated?','Pure game models own rules, while Compose lifecycle-aware loops render and advance them.'),('How is Sudoku uniqueness checked?','A solver counts up to two solutions after each attempted clue removal; nonunique removals are reverted.'),('Where are high scores saved?','Local Android preferences; there are no cloud leaderboards.'),('What makes the offline build evidence different?','It uses installed matching Kotlin/Compose compilers rather than claiming the unavailable normal Gradle compiler artifacts were resolved.')]
for i,(q,answer) in enumerate(extra,49):text+=f'\n{i}. **{q}** {answer}\n'
viva.write_text(text,encoding='utf-8')

slides=[
('BlueLink 2.0','Offline communication with groups, documents and SOS','Actual app screens','Separate source/build evidence from physical device acceptance.'),
('Introduction','Private messaging and host-based local groups','Two phones and Bluetooth','Classic RFCOMM is the connected data transport; neither endpoint needs an Internet server.'),
('Problem statement','Local access, explicit delivery and emergency discovery','Use-case table','Describe offline coordination without claiming a guaranteed emergency channel.'),
('Objectives','Real transport, bounded protocol and useful diagnostics','Acceptance checklist','Explain that Mini Arcade is recreational, not a networking innovation.'),
('Existing approaches','Cloud infrastructure versus local transport examples','Comparison table','Avoid universal claims about competing messengers.'),
('Proposed system','Private chat, groups, polls, documents, SOS and arcade','Feature map','Show the preserved interface and small secondary additions.'),
('Technology stack','Kotlin, Compose, Android APIs and pure JVM models','Stack table','Distinguish device minimum, SDK targets and tested build toolchain.'),
('Overall architecture','ViewModel coordinator with per-peer sessions','ARCHITECTURE diagram','Explain independent BLE and game subsystems.'),
('Classic communication','Permissions, visibility, pairing, socket, HELLO','Connection sequence','Pairing is not the same as a BlueLink application session.'),
('BLP protocol','40-byte header, CRC, 4096-byte payload, minor negotiation','Wire-format table','Minor zero permits legacy text; minor one gates extension families.'),
('Group communication','Star host, assigned identities and member roster','GROUP_COMMUNICATION diagram','Each member has an independent session; host radio concurrency must be tested.'),
('Live polling','Validated options, host counts, change policy and closure','Actual poll card','Acceptance by the coordinator is not proof every member synchronized.'),
('Document sharing','Consent, streaming chunks, SHA-256 and SAF save','File lifecycle','Explain group verification before re-offering documents.'),
('BLE SOS host','Compact public beacon, expiry and explicit stop','Advertisement byte table','Do not present SOS as normal connected messaging or authenticated emergency services.'),
('SOS finder','Unpaired scan, RSSI cards and optional GATT details','Finder flow','RSSI is not exact distance. Long-read behavior needs device validation.'),
('Mini Arcade','Snake, Sudoku, runner and falling blocks','Actual game screens','Rules are independent from transport; loops pause with lifecycle and scores stay local.'),
('Performance and diagnostics','Measured RTT, directional goodput and counters','Actual Lab and Log','Do not add invented speed, range or latency charts.'),
('Testing','Automated evidence, compiler evidence and device sheets','BUILD_STATUS table','Use final executed counts and leave physical test results blank until performed.'),
('Limitations and future work','Foreground scope, radio limits, public SOS, no resume','Boundaries table','Future encryption, persistence, services and host migration need separate work.'),
('Conclusion','Implemented feature paths with reproducible evidence','Acceptance checklist','Summarize only verified outcomes and the remaining physical review.')]
text='# BlueLink 2.0 presentation outline\n'
for i,(title,content,visual,notes) in enumerate(slides,1):text+=f'\n## {i} {title}\n\n**On slide:** {content}.\n\n**Suggested visual:** {visual}.\n\n**Speaker notes:** {notes}\n'
(docs/'PPT_CONTENT.md').write_text(text,encoding='utf-8')

prompt=docs/'BUILD_PROMPT.md'
text=prompt.read_text()
text+='''

## BlueLink 2.0 current scope

Preserve the existing UI and implemented 2.0 source. The feature authority is NEW_FEATURES.md and the group, file, SOS and protocol specifications. Retain host-plus-four-member star sessions, canonical group sender identities, 2–8 option coordinated polls, 10 MiB streaming document transfers with consent and SHA-256, separate legacy-compatible BLE SOS advertising/GATT, and four lifecycle-aware offline games. Do not replace these with placeholder cards or simulated network events. Read BUILD_STATUS before making readiness claims. Run the full expanded suite and provide a fresh signed version 2.0 APK plus a source archive. Physical Classic/BLE and screen/accessibility checks must be reported as pending unless actually executed.
'''
prompt.write_text(text,encoding='utf-8')
print('Updated',len(documents)+8,'documentation files')
