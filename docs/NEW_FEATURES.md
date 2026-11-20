# BlueLink 2.0 features

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
