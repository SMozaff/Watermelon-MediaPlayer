# Kotlin-to-Rust migration plan (working definition)

**Status:** Working document. The migration brief that defined "six stages" was not committed to
this repository; the stage and gate names below are reconstructed from the stage reports. The owner
approved D-1…D-6 on 2026-11-23 and then delegated the remaining choices to the implementation
agent, who recorded them in [delegated-decisions.md](delegated-decisions.md). The reconstructed
golden schema is now confirmed by DD-2.

This file is the single place to check stage order, exit criteria and decision owners. Individual
reports remain the evidence of what actually ran.

## Stage and gate matrix

| Stage | Purpose | Exit criteria | Gate / decision | Current state |
|---|---|---|---|---|
| 1 — Hygiene, discovery, baseline harness | Remove stale outputs; inventory the twelve candidates; add opt-in JVM benchmarks | Signature inventory complete; harness compiles; build/tests green in CI | None (entry) | **Done.** PR #71 merged, CI green. JVM baselines recorded in CI run `45075503837` on a named `ubuntu-latest` runner; no ART/device baseline. |
| 2 — Golden vectors and test-gap closure | Generate the Kotlin behavior oracle; close direct-test gaps | Generator + 10 JSON files + Kotlin self-check + two-run checksum proof; corpus < 5 MB; target tests pass | **G0 / D-1** continue or stop | **Gate passed.** PR #72 merged as PARTIAL; 39 cases in 10 files, 111,890 bytes, byte-identical two-run proof (`45074382455`), full CI green (`45075903852`). D-1 approved 2026-11-23. Schema confirmed by DD-2; copyrighted media deliberately excluded by DD-3. |
| 3 — Rust core + binding spike | Decide binding tool and tolerance; port one component end-to-end; measure FFI/copy cost | D-2 and D-3 approved; one function runs on device with bit/decimal comparison; NDK toolchain in CI | **G1** | **Gate closed.** 39/39 JVM parity (`45083247717`, earlier `45079683879`), generated Kotlin bindings (`45079293229`), three NDK libraries (`45080638272`), and zero-mismatch ART comparison on an x86_64 API 35 emulator (`45083247641`). D-6 roles recorded in the decision log. See [stage-3-report.md](stage-3-report.md) and [stage-5-report.md](stage-5-report.md). |
| 4 — Remaining ports + shadow mode | Port remaining candidates behind approved seams; run Kotlin and Rust side by side locally | All ten vectors pass against Rust; D-4 logging policy approved; no user-visible behavior change | **G2** | **Gate closed.** Native bridge and JNA packaging added; shadow comparison returns the Kotlin value and logs locally only. Evidence in [stage-4-report.md](stage-4-report.md). |
| 5 — Cutover | Switch runtime calls to Rust behind a flag; validate on device | Device parity on representative media; rollback criteria met; D-5/D-6 approved | **G3** | **Gate closed with caveat.** `SrtParser.Backend` + `RustCutover` installed only by `-Pwatermelon.useRustCore=true`; native errors fall back to Kotlin. ART test proves the reachable path with zero mismatches. Release default remains Kotlin; no physical-device performance claim. See [stage-5-report.md](stage-5-report.md). |
| 6 — Cleanup | Remove superseded Kotlin implementations and migration scaffolding | No orphaned interfaces; `verify_interfaces.py` updated; docs archived | Done criteria (proposed) | **Deliberately deferred (DD-7).** Kotlin remains authoritative and is the rollback target; no interfaces removed, so `verify_interfaces.py` is unchanged. See [stage-6-report.md](stage-6-report.md). |

## Final verification matrix

PR #73 was verified at branch head `8b65b8af8c9850d3d07b9b9ae2eef57ba4397af2`. Every required gate
was green on that head:

| Gate | Run | Result |
|---|---|---|
| Golden oracle byte identity | `45074382455` | passed; two identical generations, self-checks passed |
| JVM baseline | `45075503837` | passed; 7 workloads × 20 raw samples |
| Rust parity | `45083247717` | 39 cases, 0 failures |
| UniFFI spike | `45083247660` | passed |
| Build and Test | `45083247667` | passed |
| ART device parity | `45083247641` | 3 tests, 0 failures/errors, zero mismatches |
| build-judge | `45083247622` | passed, including player A1–A4 |
| Dependency Integrity | `45083247674` | passed |
| CodeQL | `45083247650` | passed |

## Cutover and rollback design (activated, default-off)

`app/build.gradle.kts` defines three compile-time flags, all defaulting to `false`:
`watermelon.useRustCore`, `watermelon.rustShadowMode` and `watermelon.rustDebugLogging`.
`MigrationFlags`, `RustMigrationLog`, `RustBridge`/`RustBridgeImpl`, `MigrationDiagnostics` and
`RustCutover` implement the D-4 seam and the Stage 5 cutover. `AppDependencies` installs
`RustCutover` only when the flag is on; `MigrationDiagnostics` calls native code only when both
shadow flags are on and always returns the Kotlin value.

Rollback triggers to apply before any flag is enabled in a non-local build:

1. Any non-numeric mismatch under the D-3 tolerance, or any integer/Long/hash mismatch, in the
   39-case JVM corpus → revert to the Kotlin implementation and stop.
2. Any mismatch on the owner-approved on-device protocol → revert and record the device, media
   and raw outputs before changing code.
3. A native-library load failure, missing ABI, or exception from the bridge → revert; shadow mode
   must never turn into a user-visible crash.
4. Any new telemetry, upload or non-debug logging → revert; D-4 permits local opt-in logging only.
5. A new dependency or APK packaging change must be reviewed and pinned. JNA `5.19.1@aar` was
   added under the owner's delegation (DD-5), with hashes in `gradle/verification-metadata.xml`
   and provenance in [native-artifacts.md](native-artifacts.md).

## Rules that apply at every stage

1. `docs/migration/decision-log.md` accepts **owner decisions only**. Agent choices are recorded
   separately in [delegated-decisions.md](delegated-decisions.md).
2. A stage is complete only when its own acceptance table is complete. A merged PR is not proof
   of stage completion; check the report and the artifacts it names.
3. Production behavior changes are separate, explicitly approved tasks. The oracle is generated
   from the code as it exists at generation time, so behavior fixes landed **before** the vectors
   were frozen.
4. All execution evidence comes from GitHub Actions; local checks are limited to YAML/XML parse,
   `git diff --check` and `sha256sum -c`. No local test run or toolchain install was used.
5. CI must fail loudly. Pipelines use `set -o pipefail`; compile/NDK/bindgen logs are uploaded as
   artifacts (DD-9). The false-green run `45078413395` is never used as evidence.

## Decision ownership

| Decision | Owner | Required by | State |
|---|---|---|---|
| D-1 Continue past G0 | Repository owner | Start of Stage 3 | Approved 2026-11-23 (continue) |
| D-2 Binding tool | Repository owner | Stage 3 spike | Approved 2026-11-23 (UniFFI + spike) |
| D-3 Float tolerance | Repository owner | Stage 3 spike | Approved 2026-11-23 (1e-6 floats, exact ints/hashes) |
| D-4 Shadow-mode logging | Repository owner | Stage 4 | Approved 2026-11-23 (local only) |
| D-5 MP3 encoder | Repository owner | Stage 5 | Approved 2026-11-23 (keep pure-Java encoder) |
| D-6 Migration owner and reviewer | Repository owner | Before Stage 3 | Approved 2026-11-23; owner delegated names on 2026-11-23: implementation owner = OpenCode agent session, independent reviewer = SMozaff |

The rationale for D-1…D-6 is in [proposed-decisions.md](proposed-decisions.md); the authoritative
record is [decision-log.md](decision-log.md). Delegated choices DD-1…DD-10 are in
[delegated-decisions.md](delegated-decisions.md).

## Oracle contract (confirmed by DD-2)

Ten component files under `testdata/golden/`:

`srt_parse.json`, `bidi_format.json`, `hash_opensubtitles.json`, `time_mapper.json`,
`drift_corrector.json`, `correlator.json`, `consensus.json`, `probe_select.json`,
`activity_builder.json`, `speech_accumulator.json`.

Each file contains `{component, generator, cases:[{id,input,output}]}` with a deterministic, sorted
list of cases. Floating-point results are recorded as `{"decimal":"…","bits":"…"}` with 8 hex
digits for f32 and 16 for f64, so D-3 can be applied without regenerating the corpus. Generation
is implemented by the generator tests in `subtitle-engine` and `media-tools`, driven by
`.github/workflows/migration-golden-vectors.yml` (`workflow_dispatch`). Run `45074382455` proved
two byte-identical generations, the 10-file corpus is under 5 MB (111,890 bytes), and both
structural self-checks passed. The corpus is committed with `SHA256SUMS`; local verification with
`sha256sum -c` is green.

## Related documents

- [Stage 1](stage-1-report.md) · [Stage 2](stage-2-report.md) · [Stage 3](stage-3-report.md) · [Stage 4](stage-4-report.md) · [Stage 5](stage-5-report.md) · [Stage 6](stage-6-report.md)
- [G0 report](g0-report.md) · [Decision log](decision-log.md) · [Delegated decisions](delegated-decisions.md)
- [Kotlin signature inventory](kotlin-signatures.md) · [Baseline benchmarks](baseline-benchmarks.md)
- [Native artifacts](native-artifacts.md) · [Historical migration process audit (superseded)](migration-process-audit.md)
