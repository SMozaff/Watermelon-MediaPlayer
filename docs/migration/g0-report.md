# G0 report: golden vectors and Kotlin behavior baseline

**Repository:** `SMozaff/Watermelon-MediaPlayer`  
**Stage branch:** `migration/stage-2-golden-vectors-test-gap-closure`  
**Kotlin source baseline:** `07b7aa557f11e8f810bb05d4e9ae6a602448e7e3`  
**Status:** INCOMPLETE. The golden-vector generator has not been run and no generated corpus is committed.

## Corpus counts and size

The requested ten files are not yet present. No case count is claimed for generated vectors.

| Component file | Generated cases | Status |
|---|---:|---|
| `srt_parse.json` | 0 | NOT GENERATED |
| `bidi_format.json` | 0 | NOT GENERATED |
| `hash_opensubtitles.json` | 0 | NOT GENERATED |
| `time_mapper.json` | 0 | NOT GENERATED |
| `drift_corrector.json` | 0 | NOT GENERATED |
| `correlator.json` | 0 | NOT GENERATED |
| `consensus.json` | 0 | NOT GENERATED |
| `probe_select.json` | 0 | NOT GENERATED |
| `activity_builder.json` | 0 | NOT GENERATED |
| `speech_accumulator.json` | 0 | NOT GENERATED |
| **Total** | **0** | **NOT GENERATED** |

Current `testdata/golden/` corpus size: 0 bytes because no golden files have been produced. This is not acceptance of an empty corpus; it records the incomplete state.

## Stage 1 benchmark baseline

Stage 1 added an opt-in JUnit harness for correlator primary/expanded/refinement, 3/5-measurement consensus, a 2,000-cue SRT workload and an 8-second 16 kHz mono PCM16 accumulator workload. It uses 5 warm-ups and 20 timed iterations. The harness compiled in Stage 1 PR CI, but remained default-off and no raw measurements were recorded. The Stage 1 report explicitly states that no raw timing values are available. Therefore this report copies no numeric timing results: **baseline measurements remain UNVERIFIED**.

These workloads are JVM microbenchmarks, not ART-on-device measurements. Their absence means no performance-based G0 conclusion can be drawn.

## Characterization findings

1. `BidiFormatter.format` consumes trailing neutral whitespace into the opposite-direction isolate despite a comment saying trailing neutrals are trimmed out. Existing Stage 1 and new Stage 2 characterization tests pin the observed output. Production code was not changed.
2. `OpenSubtitlesHasher` currently accepts files of at least 65,536 bytes. The published OpenSubtitles OSHash reference repository documents a 131,072-byte minimum and publishes `breakdance.avi` (12,909,756 bytes, `8e245d9679d31e12`) and a 4 GB vector (`61f7751fc2a72bfb`). This is a suspected compatibility discrepancy only; current Kotlin behavior remains the migration oracle. No copyrighted reference media was added. See https://github.com/opensubtitles/oshash and https://opensubtitles.github.io/oshash/.
3. The current Kotlin hasher's 64 KiB acceptance and overlapping head/tail reads at exactly 64 KiB are pinned by synthetic characterization tests. No production correction is made in this migration stage.

## G0 assessment

The Stage 1 JVM timings are absent, and the Stage 2 golden corpus has not been generated or checked. There is not enough measured evidence to conclude that the proposed Rust migration's performance benefit justifies continuing. This report does not make the decision. Owner decision D-1 remains blank in [decision-log.md](decision-log.md).

## Blocking execution limitation

The current execution workspace has no repository checkout or Gradle wrapper. The Stage 2 test command was attempted and failed with exit code 127:

```text
./gradlew :subtitle-engine:testDebugUnitTest :media-tools:testDebugUnitTest --stacktrace
bash: line 1: ./gradlew: No such file or directory
```

The GitHub repository contents API was available for source inspection and commits, but it does not execute the Kotlin oracle. Consequently, expected outputs have not been fabricated or reconstructed by hand. A Gradle-capable checkout or repository CI execution path is required to run the generator, write the ten files, perform two-run checksum comparison, and execute the self-check.

## Owner inputs still needed

- Supply the official `breakdance.avi` reference asset through an approved test-data channel if the official published vector must be executed locally; do not commit copyrighted media.
- Supply real-derived signatures only if desired. Synthetic-only data remains the current scope.
- Decide D-1 only after the generated corpus and benchmark evidence are available.
