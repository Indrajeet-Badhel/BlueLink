# Document transfer specification

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
