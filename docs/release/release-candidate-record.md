# Watermelon MediaPlayer Release Candidate Record

> **Decision rule:** This record is **Hold** until every required entry has evidence and the named release owner records a Go decision. A successful build alone is not a release approval.

## Candidate identity

| Field | Required entry |
|---|---|
| Candidate version | `1.0.0` |
| Version code | `1` |
| Immutable Git tag | `v________________` |
| Commit SHA | `________________` |
| Release owner | `________________` |
| Review date | `________________` |
| Distribution channel | `________________` |

## Automated evidence

| Gate | Required evidence | Status |
|---|---|---|
| Full Android build and unit tests | [PR #14 build-judge](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/33134026879) passed for commit `525d39029e7893e6229dc8d86ef756867383d5d6`; re-run is required for the immutable candidate tag. | **Passing PR evidence; tagged rerun pending** |
| Migration/emulator gate | [PR #14 migration-ladder job](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/33134026879) passed for commit `525d39029e7893e6229dc8d86ef756867383d5d6`; re-run is required for the immutable candidate tag. | **Passing PR evidence; tagged rerun pending** |
| Dependency Integrity | [PR #14 clean-cache dependency gate](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/33134026843) passed: strict verification, synthetic MP3 test, and SBOM artifact generation. A tagged-candidate rerun is still required. | **Passing PR evidence; tagged rerun pending** |
| CodeQL | [PR #14 CodeQL analysis](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/33134026868) passed for commit `525d39029e7893e6229dc8d86ef756867383d5d6`; alert triage and a tagged-candidate rerun remain required. | **Passing PR evidence; tagged rerun pending** |
| Optimized unsigned candidate | Release Candidate workflow artifact and SHA-256 | Pending |
| Signed candidate | Protected-environment workflow artifact, certificate output, and SHA-256 | Pending |
| Strict Gradle verification | `gradle/verification-metadata.xml` has SHA-256 records for 655 components/1,194 artifacts. Java 17 strict classpath and release-lint paths, plus the PR #14 clean-cache gate, passed. Release-owner review remains required. | **Passing PR evidence; owner review pending** |

## Manual product evidence

| Gate | Required evidence | Status |
|---|---|---|
| MP3 extraction | Completed physical-device matrix, independently decoded outputs, and defect disposition | Pending |
| Android TV | Remote focus, 1080p/4K overscan, long labels, platform keyboard, and return-navigation results | Pending |
| Accessibility | TalkBack, enlarged text, forced RTL, and light/dark theme results | Pending |
| Media-job lifecycle | Compression/trim/extract backgrounding, cancellation, low-storage, and restart results | Pending |
| Subtitle privacy | Completed observed network-flow inventory and accurate user-facing disclosure | Pending |
| java-lame provenance | Completed [`java-lame-provenance.md`](java-lame-provenance.md) with an assigned owner | Pending |
| Known limitations | Residual-risk list, owner, and accepted mitigation | Pending |

## Signing and artifact verification

The signed candidate must be built only through the protected `release` environment after a release owner has supplied its secrets. The workflow produces a certificate report and SHA-256 manifest; record both here. A debug-signed or unsigned APK is never distributable.

| Item | Required entry |
|---|---|
| Signed APK filename | `________________` |
| APK SHA-256 | `________________` |
| Signing certificate SHA-256 | `________________` |
| Signing workflow URL | `________________` |
| Artifact retention/download location | `________________` |

## Decision

| Decision | Release owner | Date/time | Notes |
|---|---|---|---|
| ☐ Hold / ☐ Go | `________________` | `________________` | `________________` |

A Go decision confirms that every Pending item above has been replaced with linked evidence or a recorded, time-bound exception accepted by the release owner. Publishing to an app store, download page, or GitHub release remains a distinct, deliberate action after this record is complete.
