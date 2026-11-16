# Stage 2 report: golden vectors and test-gap closure

**Repository:** `SMozaff/Watermelon-MediaPlayer`  
**Branch:** `migration/stage-2-golden-vectors-test-gap-closure`  
**Base:** Stage 1 merged baseline `07b7aa557f11e8f810bb05d4e9ae6a602448e7e3`  
**Stage:** 2 of 6  
**Status:** PARTIAL / NOT ACCEPTANCE-COMPLETE

## Summary

Verified the Stage 2 preconditions: `docs/migration/stage-1-report.md` and `docs/migration/kotlin-signatures.md` exist on the Stage 2 branch, and Stage 1 PR #71 is recorded as merged. Read the twelve migration candidate implementations, the shared subtitle contracts and models, and the relevant existing tests. Added direct characterization tests for SRT parsing, bidi formatting and OpenSubtitles hashing. Added the G0 status report and initialized the decision log with D-1 blank.

The golden-vector generator, ten generated JSON files and Kotlin self-check have **not** been completed. The available execution environment has no repository checkout or Gradle wrapper, and GitHub's repository API cannot execute Kotlin code. No expected outputs were invented. This report deliberately marks those acceptance items FAIL or UNVERIFIED rather than claiming completion.

No production source, Rust code, Gradle build/dependency files, or CI workflows were changed. No new dependencies were introduced.

## What changed (files)

### Added test sources

- `subtitle-engine/src/test/kotlin/com/watermelon/subtitle/parser/SrtParserCharacterizationTest.kt`
  - BOM and CRLF/LF/CR normalization.
  - Missing indices, blank-line variations, timing-line suffixes, comma/dot fractions and 1/2/3-digit milliseconds.
  - Sort order vs original parser indices.
  - HTML-like tag stripping, empty/malformed blocks, Persian/Arabic/mixed direction, language propagation, hour widths and long body lines.
- `subtitle-engine/src/test/kotlin/com/watermelon/subtitle/bidi/BidiFormatterCharacterizationTest.kt`
  - LTR/RTL direction detection, empty input, pure-direction text, mixed runs, punctuation/digit boundaries and the trailing-neutral whitespace behavior already observed in Stage 1.
- `subtitle-engine/src/test/kotlin/com/watermelon/subtitle/hash/OpenSubtitlesHasherTest.kt`
  - Exactly 64 KiB, larger zero-filled input, high-bit/wrapping arithmetic and below-64-KiB rejection.

### Added documentation

- `docs/migration/g0-report.md`
- `docs/migration/decision-log.md`

### Not created

- Golden-vector generator entry point.
- `testdata/golden/*.json` (all ten required files).
- Kotlin golden self-check test.
- Official media-backed OpenSubtitles reference-vector test.

## Commands run with results

### Repository source inspection

- GitHub repository API reads of the Stage 1 report, Kotlin signature inventory, build files, all twelve candidate Kotlin implementations, shared contracts/models, and the existing subtitle-sync, accumulator and bidi tests: completed.
- Verified `subtitle-engine/build.gradle.kts` already applies the Kotlin serialization plugin and declares `kotlinx-serialization-json`. `media-tools/build.gradle.kts` does not declare either. No dependency was added.
- Confirmed Stage 1 branch/PR history from the recorded Stage 1 commit and PR #71 metadata.

### Context7 documentation lookup

Resolved Kotlinx Serialization v1.11.0 and checked its JSON documentation. The docs confirm that special floating-point values can be enabled with `allowSpecialFloatingPointValues`; the Stage 2 schema still requires a decimal string and raw IEEE-754 bit representation for each float, so non-standard JSON numeric tokens are not needed. No code dependency or version was introduced by this lookup.

### Local Gradle test attempt

Attempted:

```text
./gradlew :subtitle-engine:testDebugUnitTest :media-tools:testDebugUnitTest --stacktrace
bash: line 1: ./gradlew: No such file or directory
EXIT_CODE=127
```

The current workspace is not a local repository checkout. No local Kotlin compilation or test execution is claimed. No Gradle system installation or alternate execution path was used.

### Official OpenSubtitles reference search

Found the OpenSubtitles OSHash reference repository and its published vectors:

- `breakdance.avi`: 12,909,756 bytes, `8e245d9679d31e12`
- `dummy.rar` unpacked: 4,295,033,890 bytes, `61f7751fc2a72bfb`

Sources: https://github.com/opensubtitles/oshash and https://opensubtitles.github.io/oshash/.

The source repository says the algorithm requires at least 131,072 bytes. Current Kotlin source requires only 65,536 bytes. This discrepancy is recorded as a suspected defect; production behavior is unchanged. The published media was not committed. The official vector is not yet executable in the test suite because the reference media asset is not present.

## Acceptance checklist

| Acceptance item | Result | Evidence / limitation |
|---|---|---|
| Stage 1 report and signatures file exist; Stage 1 PR merged | **PASS** | Both files fetched from the Stage 2 branch; PR #71 found in repository history as merged. |
| A1: deterministic Kotlin generator | **FAIL** | No generator source committed. |
| A2: all ten golden JSON files and required schema | **FAIL** | None generated; see G0 report. |
| A3: decimal string and raw IEEE-754 bits | **UNVERIFIED** | No generated vectors to inspect. |
| A4: deterministic output and two-run checksum proof | **UNVERIFIED** | Generator could not be run. |
| A5: requested synthetic corpus coverage | **FAIL** | Characterization tests cover a subset; no full golden corpus exists. |
| A6: corpus under 5 MB | **UNVERIFIED** | No generated corpus exists, so its eventual size cannot be measured. |
| B: SRT parser characterization tests exist | **PASS** | Direct tests added; execution UNVERIFIED. |
| B: Bidi formatter characterization tests exist | **PASS** | Direct tests added; execution UNVERIFIED. |
| B: OpenSubtitles hasher characterization tests exist | **PASS** | Direct synthetic tests added; execution UNVERIFIED. |
| B: official published reference vector searched | **PASS** | Official OSHash repository and two published vectors found. |
| B: official reference vector added as executable test | **UNVERIFIED** | Reference media not supplied; no copyrighted media committed. |
| C: Kotlin self-check loads all ten golden files | **FAIL** | Golden files and self-check not created. |
| D: G0 report written | **PASS** | `docs/migration/g0-report.md`; explicitly records zero generated cases and absent benchmark measurements. |
| D: decision log exists and D-1 blank | **PASS** | `docs/migration/decision-log.md`; D-1 is blank. |
| No production behavior change | **PASS** | Only test and documentation paths changed. |
| No new dependencies or CI changes | **PASS** | No Gradle, Cargo, CI or production files changed. |

## Suspected defects

1. **BidiFormatter comment/implementation mismatch:** the method comment says trailing neutral characters are trimmed out of an isolate, but the loop consumes trailing neutral whitespace into the isolate. Existing and new tests pin the observed output. No production fix was made.
2. **OpenSubtitlesHasher minimum size discrepancy:** current Kotlin accepts files from 65,536 bytes and hashes overlapping head/tail chunks for exactly 64 KiB. The published OpenSubtitles reference repository specifies a 131,072-byte minimum. This is recorded for owner review; current Kotlin behavior remains the parity oracle for this stage.
3. **OpenSubtitlesHasher published vector gap:** published reference values are known, but the reference media has not been supplied through an approved test-data channel. No fake or reconstructed "official input" test is claimed.

## Open questions for the owner

1. Can a Gradle-capable checkout or repository CI execution path be used to run the Kotlin generator and self-check? This is required before any golden expected outputs can be committed honestly.
2. Can the official `breakdance.avi` reference media be supplied through an approved test-data channel? It must not be committed to the repository.
3. Should the official 128 KiB minimum discrepancy be handled as a separate behavior-change task, or should Stage 2 continue pinning the current 64 KiB Kotlin behavior only?
4. Stage 1 benchmark timings were never recorded. Should G0 wait for a deliberate opt-in JVM benchmark run before D-1 is decided?

**Stage boundary:** Stage 2 only. No Rust implementation, production changes, dependency changes, CI changes or Stage 3 work has started.

## Follow-up execution note (2026-11-16)

A fresh attempt to obtain a runnable checkout used:

\`\`\`text
 git clone --depth 1 --branch migration/stage-2-golden-vectors-test-gap-closure https://github.com/SMozaff/Watermelon-MediaPlayer.git /tmp/watermelon-stage2
 fatal: unable to access 'https://github.com/SMozaff/Watermelon-MediaPlayer.git/': Could not resolve host: github.com
 EXIT_CODE=128
\`\`\`

The execution container reports OpenJDK 21.0.12.1, but `gradle --version` returns `gradle: command not found`. The repository wrapper is not present because clone failed. GitHub API writes and reads are available, but no GitHub Actions dispatch operation is exposed by the connected GitHub tools, and the commit workflow-run lookup returned no PR-triggered runs for the latest Stage 2 commit. Consequently the generator cannot be run or verified in this session. The earlier A6 PASS label was corrected to UNVERIFIED because an absent corpus does not prove the generated corpus will be below 5 MB.

