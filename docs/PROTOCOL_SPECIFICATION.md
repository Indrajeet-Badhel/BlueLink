# BlueLink Application Protocol 1.1

This specification matches `protocol/src/main/kotlin/dev/bluelink/protocol/Packet.kt` and `Session.kt`. BLP is an application protocol over a reliable ordered byte stream; it does not replace Bluetooth link reliability. Its ACK and retry logic demonstrates application receipt handling.

## Transport and envelope

Use authenticated RFCOMM with UUID `9f0c4b2e-6a71-4d83-94b0-2f78e6a1c305`. There is one connection per conversation. The same app supports either connection role. All integers are big endian. Counters in 4-byte fields are unsigned; timestamps and session IDs occupy eight raw bytes. Kotlin stores session IDs in signed `Long` without interpreting sign.

| Offset | Size | Field | Encoding |
|---|---:|---|---|
| 0 | 4 | Magic | `42 4C 4E 4B`, ASCII BLNK |
| 4 | 1 | Envelope major | 1 |
| 5 | 1 | Type | HELLO=1, DATA=2, ACK=3, PING=4, PONG=5, DISCONNECT=6, ERROR=7 |
| 6 | 2 | Flags | Zero |
| 8 | 8 | Session | Client-selected random nonzero value, echoed by host |
| 16 | 4 | Sequence | DATA sequence or PING ID; otherwise zero |
| 20 | 4 | Acknowledgement | DATA sequence for ACK, PING ID for PONG; otherwise zero |
| 24 | 8 | Timestamp | Sender monotonic milliseconds; retries preserve the original DATA timestamp |
| 32 | 4 | Payload length | 0â€“4096, also subject to negotiated limit |
| 36 | 4 | CRC32 | IEEE CRC32 of header bytes 0â€“35 followed by payload |
| 40 | N | Payload | UTF-8 DATA or binary HELLO; other types have empty payload |

Header size: 40 bytes. Maximum complete frame: 4136 bytes. Empty DATA is invalid. Empty messages are not sent. The limit is on UTF-8 bytes, not visible characters. The app trims leading and trailing whitespace before sending.

CRC uses `java.util.zip.CRC32`. The CRC field itself is omitted from coverage, not included as four zero bytes. The standard algorithm vector `123456789` produces `CBF43926`. CRC is not cryptographic protection.

## HELLO bootstrap

The client sends HELLO after stream setup; the host responds with the same session ID. Neither endpoint accepts DATA before this exchange. Envelope major 1 is required; a different major fails before attempting to decode its payload. Both peers negotiate minor 1, or minor 0 with an older peer.

The HELLO payload is **12 binary bytes**, not JSON:

| Payload offset | Bytes | Field | Current value |
|---|---:|---|---|
| 0 | 1 | Minimum supported minor | 0 |
| 1 | 1 | Maximum supported minor | 1 |
| 2 | 4 | Maximum received payload | 4096 by default |
| 6 | 4 | Required feature bitmap | 3: bit 0 CRC32, bit 1 stop-and-wait |
| 10 | 2 | Reserved | Zero |

Select the smaller payload limit. The accepted range is 1â€“4096; local application text must fit the selected limit. Feature bitmap must equal 3. Additional mandatory features require explicit compatibility work; unknown required bits are not ignored. A range including minor 0 is compatible. No matching minor, invalid reserved fields, invalid size or capabilities terminate the session. The default HELLO deadline is 10 seconds.

## DATA and reliability

Each sending direction has independent sequences beginning at 1 and one outstanding DATA frame. The UI queue is bounded to 50 waiting messages, plus the outstanding message. Each DATA is installed as pending before write submission, preventing a fast ACK from being missed.

A single executor writes complete frames and flushes them. Control writes never wait for a DATA ACK. After successful output, the sender starts a 2000 ms ACK deadline. Three retransmissions are allowed after the initial attempt, for four attempts total. Retries reuse the complete original DATA frame. The receiver validates CRC, session, length, DATA fields and UTF-8 before delivery. A new expected sequence is delivered once and acknowledged. A lower accepted sequence is a duplicate: count it, suppress redisplay and re-ACK. A sequence above the next expected value is a protocol error.

ACK has sequence zero and references a nonzero DATA sequence in the acknowledgement field. Only the active pending sequence completes a send; stale unmatched ACKs cannot complete another message. ACKs are never ACKed. An ACK may confirm application receipt while a user has not seen the message.

Before sequence 2^32 would be used, close the session and require reconnection. No sequence wraps within a session. Retry exhaustion closes the session, fails the outstanding and queued sends, and prevents a sequence gap. Failed means **unconfirmed**: a recipient may have received the DATA while its ACK was lost. There is no exactly-once guarantee across reconnects, process death or manual resending.

## PING PONG and liveness

PING carries a nonzero identifier in sequence. PONG has sequence zero and echoes that ID in acknowledgement. Both payloads are empty. RTT is the sender's local monotonic receive time minus its locally recorded enqueue time. It includes writer queue and application scheduling delay. Never subtract the two phones' clocks.

Users can Ping in Lab; the session also checks every five seconds and sends a probe when no probes are outstanding. At most four probes are outstanding. With no valid inbound frame for more than 30 seconds, the session closes on a periodic check. A lost socket can also fail earlier through I/O. RTT average uses matching samples since reset.

## Parsing and termination

Read exactly 40 header bytes, validate the length before allocation, then accumulate the declared payload. This handles arbitrary fragmentation and coalescing. A zero-progress stream, truncated frame, invalid envelope, CRC, negotiated limit, session, DATA encoding or field terminates the connection. The reader does not attempt to resynchronize an untrusted stream.

DISCONNECT is empty with zero sequence and acknowledgement; the sender attempts a serialized write before closure, with a 300 ms maximum local wait. ERROR with those same empty control fields is understood as peer failure. This release logs and closes on malformed input rather than relying on successfully sending ERROR over a damaged connection.

Reconnect creates a new session. PING state, sequences, queue and duplicate state are not reused. Reset Statistics affects counters and samples only. It does not reset session sequences or retransmission state.

## Example DATA frame

The `goldenWireVectorMatchesIndependentCrcCalculation` JUnit test checks a fixed wire vector against the actual encoder: session 42, sequence 1, timestamp 123, payload `Hello`. See `docs/protocol-vector.txt` for the full hexadecimal frame and verified checksum `3614F2A5`.

## Security

Use Android-approved authenticated pairing. A UUID or session ID is not a password. CRC does not authenticate a peer. Incoming sizes are bounded. Message contents are excluded from event logs. Application-level encryption and protection from an intentionally malicious paired peer are not claimed.


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
