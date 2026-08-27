# MP3 Extraction — Physical-Device Validation Record

**Purpose:** Record reproducible, release-quality evidence for the MP3 extraction path. This is an execution template, not completed validation. Mark the release **Hold** until every required scenario is evidenced and every P0/P1 defect is resolved or formally excepted.

## Test environment

| Field | Required entry |
|---|---|
| Candidate Git tag and commit | `________________` |
| Tester and date | `________________` |
| App version/version code | `________________` |
| Output application used for independent playback | `________________` |
| Test media storage location | `________________` |

## Required device matrix

Use physical devices rather than emulators for the final sign-off. At minimum, cover the oldest supported Android generation, an Android 11/12 device, and a current Android 13–15 device. Include an ARM64 production device and record available storage before each test.

| Device | Android version | ABI | Available storage | Pass/fail evidence link |
|---|---:|---|---:|---|
| Oldest supported device | `____` | `____` | `____` | `____` |
| Mid-range Android 11/12 device | `____` | `____` | `____` | `____` |
| Current Android 13–15 device | `____` | `____` | `____` | `____` |

## Input corpus and successful-output checks

Each successful conversion must be independently playable in a second media application, have a plausible duration, and persist after closing and reopening Watermelon. Capture the source filename, output filename, duration, and checksum or inspection result.

| Scenario | Device(s) | Expected outcome | Actual outcome/evidence | Status |
|---|---|---|---|---|
| Short MP4 with stereo 44.1 kHz audio | All | Valid, independently playable MP3 | `________________` | ☐ |
| Long video, at least 20 minutes | At least two | Completes without foreground-service loss | `________________` | ☐ |
| 48 kHz or multichannel source | At least two | Valid MP3 with plausible duration | `________________` | ☐ |
| Audio-only source, if supported by UX | At least one | Clear success or explicit supported limitation | `________________` | ☐ |
| No-audio source | At least one | Clear safe failure; no misleading output | `________________` | ☐ |
| Corrupt/truncated input | At least one | Clear safe failure; no retained partial file | `________________` | ☐ |
| Unicode/RTL filename | At least one | Correct visible name and valid output | `________________` | ☐ |

## Lifecycle, storage, and recovery checks

| Scenario | Expected outcome | Actual outcome/evidence | Status |
|---|---|---|---|
| Cancel before 25%, around 50%, and above 75% | Job stops; partial output is removed or plainly identified as unusable | `________________` | ☐ |
| Background then return | Foreground notification/progress persists; result remains reachable | `________________` | ☐ |
| Process interruption/relaunch | No duplicate job or orphaned output; final state is understandable | `________________` | ☐ |
| Low/free-space exhaustion | Safe error; original preserved; no silent corruption | `________________` | ☐ |
| Permission denied or revoked | Explain required access; no crash | `________________` | ☐ |
| Repeat conversion and concurrent job attempt | Predictable queueing or clear restriction; correct outputs | `________________` | ☐ |
| Destination write failure | Clear failure; no misleading success state | `________________` | ☐ |

## Defect disposition and release decision

| ID | Severity | Description | Owner | Status / retest evidence |
|---|---|---|---|---|
| `________________` | `P0/P1/P2/P3` | `________________` | `________________` | `________________` |

A release owner may mark this record complete only when all required rows pass, the output checks are independently evidenced, and no P0/P1 MP3 defect remains unresolved. Link this completed record from [`release-candidate-record.md`](release-candidate-record.md).
