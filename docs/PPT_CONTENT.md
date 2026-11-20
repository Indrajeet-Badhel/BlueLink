# BlueLink 2.0 presentation outline

## 1 BlueLink 2.0

**On slide:** Offline communication with groups, documents and SOS.

**Suggested visual:** Actual app screens.

**Speaker notes:** Separate source/build evidence from physical device acceptance.

## 2 Introduction

**On slide:** Private messaging and host-based local groups.

**Suggested visual:** Two phones and Bluetooth.

**Speaker notes:** Classic RFCOMM is the connected data transport; neither endpoint needs an Internet server.

## 3 Problem statement

**On slide:** Local access, explicit delivery and emergency discovery.

**Suggested visual:** Use-case table.

**Speaker notes:** Describe offline coordination without claiming a guaranteed emergency channel.

## 4 Objectives

**On slide:** Real transport, bounded protocol and useful diagnostics.

**Suggested visual:** Acceptance checklist.

**Speaker notes:** Explain that Mini Arcade is recreational, not a networking innovation.

## 5 Existing approaches

**On slide:** Cloud infrastructure versus local transport examples.

**Suggested visual:** Comparison table.

**Speaker notes:** Avoid universal claims about competing messengers.

## 6 Proposed system

**On slide:** Private chat, groups, polls, documents, SOS and arcade.

**Suggested visual:** Feature map.

**Speaker notes:** Show the preserved interface and small secondary additions.

## 7 Technology stack

**On slide:** Kotlin, Compose, Android APIs and pure JVM models.

**Suggested visual:** Stack table.

**Speaker notes:** Distinguish device minimum, SDK targets and tested build toolchain.

## 8 Overall architecture

**On slide:** ViewModel coordinator with per-peer sessions.

**Suggested visual:** ARCHITECTURE diagram.

**Speaker notes:** Explain independent BLE and game subsystems.

## 9 Classic communication

**On slide:** Permissions, visibility, pairing, socket, HELLO.

**Suggested visual:** Connection sequence.

**Speaker notes:** Pairing is not the same as a BlueLink application session.

## 10 BLP protocol

**On slide:** 40-byte header, CRC, 4096-byte payload, minor negotiation.

**Suggested visual:** Wire-format table.

**Speaker notes:** Minor zero permits legacy text; minor one gates extension families.

## 11 Group communication

**On slide:** Star host, assigned identities and member roster.

**Suggested visual:** GROUP_COMMUNICATION diagram.

**Speaker notes:** Each member has an independent session; host radio concurrency must be tested.

## 12 Live polling

**On slide:** Validated options, host counts, change policy and closure.

**Suggested visual:** Actual poll card.

**Speaker notes:** Acceptance by the coordinator is not proof every member synchronized.

## 13 Document sharing

**On slide:** Consent, streaming chunks, SHA-256 and SAF save.

**Suggested visual:** File lifecycle.

**Speaker notes:** Explain group verification before re-offering documents.

## 14 BLE SOS host

**On slide:** Compact public beacon, expiry and explicit stop.

**Suggested visual:** Advertisement byte table.

**Speaker notes:** Do not present SOS as normal connected messaging or authenticated emergency services.

## 15 SOS finder

**On slide:** Unpaired scan, RSSI cards and optional GATT details.

**Suggested visual:** Finder flow.

**Speaker notes:** RSSI is not exact distance. Long-read behavior needs device validation.

## 16 Mini Arcade

**On slide:** Snake, Sudoku, runner and falling blocks.

**Suggested visual:** Actual game screens.

**Speaker notes:** Rules are independent from transport; loops pause with lifecycle and scores stay local.

## 17 Performance and diagnostics

**On slide:** Measured RTT, directional goodput and counters.

**Suggested visual:** Actual Lab and Log.

**Speaker notes:** Do not add invented speed, range or latency charts.

## 18 Testing

**On slide:** Automated evidence, compiler evidence and device sheets.

**Suggested visual:** BUILD_STATUS table.

**Speaker notes:** Use final executed counts and leave physical test results blank until performed.

## 19 Limitations and future work

**On slide:** Foreground scope, radio limits, public SOS, no resume.

**Suggested visual:** Boundaries table.

**Speaker notes:** Future encryption, persistence, services and host migration need separate work.

## 20 Conclusion

**On slide:** Implemented feature paths with reproducible evidence.

**Suggested visual:** Acceptance checklist.

**Speaker notes:** Summarize only verified outcomes and the remaining physical review.
