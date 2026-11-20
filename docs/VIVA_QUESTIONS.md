# BlueLink viva questions and answers

1. **What is BlueLink?** An Android app for direct offline text messaging over Bluetooth Classic RFCOMM with an explicit application protocol.
2. **Why Bluetooth Classic?** It exposes a socket byte stream suitable for a small bidirectional text protocol between Android peers.
3. **What is RFCOMM?** A Bluetooth protocol providing serial-port-like channels and ordered stream communication.
4. **Is the host a cloud server?** No. One phone listens locally; the other initiates the Bluetooth connection.
5. **Can the host send messages?** Yes. Both connected sockets support reading and writing, regardless of which endpoint initiated.
6. **What is full duplex?** Both endpoints can transmit and receive concurrently.
7. **Why use an application UUID?** It identifies the RFCOMM service. The client and host must agree on it.
8. **Does pairing mean BlueLink is connected?** No. The app still needs a listening service, a connected socket and a successful BLP handshake.
9. **What is discoverability?** Temporary visibility to device discovery requests. It is separate from opening a server socket.
10. **Why stop scanning before connection?** Discovery consumes Bluetooth resources and can impair setup and communication.
11. **What is BLP?** BlueLink Application Protocol, the app's framing, validation and receipt protocol above RFCOMM.
12. **Why implement framing on a reliable stream?** Stream reads do not preserve application message boundaries.
13. **What is fragmentation?** A frame arrives through multiple read calls and must be accumulated.
14. **What is coalescing?** Several frames are available in the stream together and must be parsed separately.
15. **What is serialization here?** Deterministically encoding field values and UTF-8 payload into bytes; Kotlin objects are not sent directly.
16. **What byte order is used?** Big endian for all multi-byte numeric fields.
17. **What are the frame limits?** A 40-byte header plus at most 4096 payload bytes, subject to HELLO negotiation.
18. **Why check length before allocation?** To reject oversized or malformed peer input before allocating attacker-controlled memory sizes.
19. **What does CRC32 do?** It detects many accidental changes to covered bytes. It is not authentication or encryption.
20. **What bytes does CRC cover?** Header bytes 0â€“35 followed by payload; the stored checksum at 36â€“39 is omitted.
21. **Why a session ID?** It separates the current exchange from stale frames and state. It is not a secret or identity proof.
22. **Why sequence DATA?** To identify the outstanding message, match ACKs and recognize retransmitted duplicates.
23. **What is an ACK?** An application receipt response referencing a validated DATA sequence.
24. **Does Delivered mean read?** No. It means the peer app acknowledged receipt, not that the recipient opened or read the message.
25. **What is stop-and-wait ARQ?** Send one DATA, await its ACK, retry on timeout, then proceed or fail according to bounded rules.
26. **How many attempts occur by default?** One initial transmission and up to three retransmissions.
27. **What if the ACK is lost?** The sender retries, and the receiver re-ACKs the duplicate without displaying it again.
28. **Why not ACK an ACK?** That would create an unnecessary acknowledgement exchange with no natural termination.
29. **Why can Failed still mean the message arrived?** The DATA may have reached the receiver while all ACKs were lost or the connection closed before confirmation.
30. **How is sequence wrap handled?** The session closes before a number would be reused; a new connection begins with a fresh session ID.
31. **How is RTT measured?** Match PONG to a local PING identifier and subtract locally recorded monotonic times.
32. **Why not subtract the phones' timestamps?** Their clocks are not synchronized and would introduce clock offset error.
33. **What is goodput?** Unique useful payload bytes successfully acknowledged or delivered divided by elapsed time; overhead and retry bytes are excluded.
34. **Why one writer?** To serialize whole frames and prevent two components from interleaving bytes in one output stream.
35. **How is simultaneous-send deadlock avoided?** ACK/PONG controls use the writer directly while each direction independently waits for its DATA ACK.
36. **Why keep Bluetooth off the main thread?** Accept, connect and read may block; running them on the UI thread would freeze the app.
37. **What unblocks a cancelled read?** Closing the underlying Bluetooth socket releases the transport and causes I/O to return or fail.
38. **Which Android permissions matter?** Foreground scan Location on API 24â€“30, and CONNECT, SCAN and ADVERTISE as needed on API 31+.
39. **What do minSdk, compileSdk and targetSdk mean?** Device installation floor, API definitions available to compile, and platform behavior level opted into by the application.
40. **Does compileSdk require matching phone OS?** No. Supported phones can run older OS versions when runtime API use and dependencies respect minSdk.
41. **What is HELLO for?** Establish a matching session and compatible protocol minor, payload limit and required features before DATA.
42. **Is end-to-end encryption implemented?** No. The release uses Android-managed authenticated Bluetooth pairing and makes no separate app-level E2EE claim.
43. **Do JVM tests prove Bluetooth works on phones?** No. They validate protocol logic; physical discovery, pairing and RFCOMM behavior need device tests.
44. **Why no guaranteed speed figure?** Throughput depends on hardware, software scheduling, radio conditions and stop-and-wait overhead; it must be measured.
45. **What happens on backgrounding?** The foreground-only release closes its session and requires reconnection on return, except for handled system permission/discoverability flows.
46. **Does Reset Statistics affect message reliability?** No. It resets visible counters and timing samples while preserving session and duplicate state.
47. **Does it work on iPhone?** This generic Android RFCOMM design is not an equivalent iOS transport; separate platform work is needed.
48. **What future improvements are reasonable?** Tested background lifecycle, opt-in storage, richer negotiation and authenticated application encryption, each with explicit validation.

49. **How does BLE differ from Classic here?** Classic RFCOMM carries conversations and documents; BLE advertisements discover SOS without a chat connection.

50. **What is GATT?** A BLE service and characteristic model. The SOS details characteristic is read-only.

51. **Why not advertise a full SOS message?** Legacy advertisement space is bounded to 31 bytes including fields, so longer details require an optional GATT read.

52. **Does finding SOS require pairing?** No. The advertisement and open read-only details characteristic do not request a Classic bond.

53. **What does RSSI prove?** Only received signal strength at that observation. It does not establish exact distance or trust.

54. **Why a star topology?** A host can coordinate canonical membership and relay over separate RFCOMM sessions without claiming mesh routing.

55. **What if the group host leaves?** The group closes. Host migration is not implemented.

56. **How are forged sender names handled?** The group host derives identity from the client connection and emits canonical sender fields.

57. **Who counts group poll votes?** The host; each member ID has one recorded selection set subject to the change policy.

58. **Does a poll result mean everyone synchronized?** No. Coordinator acceptance and queued broadcasts are separate from every member acknowledging an update.

59. **Why chunk documents?** Streaming bounded chunks controls memory and allows chat/control scheduling between file operations.

60. **Why SHA-256 if CRC32 exists?** CRC covers frames; SHA-256 checks the reconstructed complete document against the offered digest.

61. **What permissions select and save documents?** Storage Access Framework grants specific URI access, avoiding broad storage permissions.

62. **What does file completion mean?** The recipient reports a complete hash-verified file saved to the selected destination.

63. **Can transfers resume after disconnect?** No. This release fails them and requires a fresh consent-based transfer.

64. **Is a valid SOS UUID authentication?** No. Advertisements and details remain untrusted public inputs.

65. **How is SOS battery use bounded?** Explicit foreground activation, timed scans, advertisement expiry and stop controls.

66. **What does neverForLocation affect?** It is a location-use assertion and can filter some beacons. SOS scanning instead requests the relevant discovery permissions.

67. **How are game loops separated?** Pure game models own rules, while Compose lifecycle-aware loops render and advance them.

68. **How is Sudoku uniqueness checked?** A solver counts up to two solutions after each attempted clue removal; nonunique removals are reverted.

69. **Where are high scores saved?** Local Android preferences; there are no cloud leaderboards.

70. **What makes the offline build evidence different?** It uses installed matching Kotlin/Compose compilers rather than claiming the unavailable normal Gradle compiler artifacts were resolved.
