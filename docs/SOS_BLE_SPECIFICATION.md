# SOS BLE specification

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
