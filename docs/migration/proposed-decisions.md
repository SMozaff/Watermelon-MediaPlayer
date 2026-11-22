# Proposed decisions for owner approval

**Status:** Proposals only. `docs/migration/decision-log.md` remains blank for D-1…D-6 and is the
only authoritative decision record. Nothing here may be copied into that log without the owner
writing it there.

Prepared 2026-11-23 from [migration-process-audit.md](migration-process-audit.md).

## D-1 — Continue past G0?

**Proposal:** Continue, conditional on G0 actually being met first.

**Rationale:** G0 currently has no golden corpus and no benchmark measurements, so "continue"
cannot be evaluated on evidence. The recommendation is to continue only after the generator,
ten JSON files, self-check, two-run checksum proof and at least one recorded benchmark run exist.
A stop decision remains legitimate if the measured JVM/device difference does not justify the
FFI and maintenance cost.

**Consequence if accepted:** Stage 3 may start only after the G0 exit criteria in [PLAN.md](PLAN.md).

## D-2 — Binding tool

**Proposal:** UniFFI, with a time-boxed spike before committing to all twelve ports.

**Rationale:** UniFFI generates both sides of the boundary and keeps the type contract in one
place, which matters for the many `FloatArray` and sealed-result types in the sync contracts.
Hand-written JNI gives more control over PCM buffer copying but is easier to get subtly wrong.
The spike must measure both correctness and the copy cost at the `SpeechLikelihoodAccumulator`
`ByteBuffer` boundary; if that cost dominates, revisit this choice.

**Consequence if accepted:** Add Rust toolchain and UniFFI generation to CI in Stage 3; no JNI
code until the spike reports.

## D-3 — Float tolerance

**Proposal:** 1e-6 tolerance for derived floating-point values, bit-exact comparison for integer,
Long and hash outputs.

**Rationale:** Kotlin/JVM and Rust/libm can differ in the last bits of `ln`, `roundToLong` and
other math used by `SubtitleProbeSelectorImpl` and `OffsetConsensus`. Raw IEEE-754 bits are
recorded in every golden vector, so a stricter rule can be adopted later without regenerating
anything. Bit-exactness across JVM and Rust for transcendental functions is not a realistic
acceptance rule on Android devices.

**Consequence if accepted:** Parity checker applies 1e-6 to decimal float results and exact
equality to bits of integer/hash results; raw bits remain diagnostic evidence, not pass/fail.

## D-4 — Shadow-mode logging policy

**Proposal:** Local only — opt-in debug logging of Kotlin vs Rust outputs, no telemetry, no upload.

**Rationale:** The app is commercial and its network surface is deliberately limited to subtitle
lookups. A shadow mode is needed for safe cutover, but it must not create a new data path.

**Consequence if accepted:** Add a debug-only flag in the app composition root and document a
rollback trigger before the first port.

## D-5 — MP3 encoder

**Proposal:** Keep the pure-Java encoder in `media-tools`.

**Rationale:** It is already validated in CI and has no NDK/JNI dependency. It is not one of the
twelve port candidates, and moving it would add risk without addressing the migration goal.

## D-6 — Migration owner and reviewer

**Proposal:** Owner to name one implementation owner and one independent reviewer before Stage 3.

**Rationale:** The audit found a PARTIAL stage merged with no recorded review. A named reviewer
is the simplest control for the decision-heavy stages ahead.

## Decisions already given during the audit (not D-table items)

| Item | Owner input | Date | Where applied |
|---|---|---|---|
| Decision log handling | Leave D-1…D-6 blank; keep proposals separate | 2026-11-23 | This file; `decision-log.md` untouched |
| Defect disposition | Fix Bidi trailing-neutral isolation and OSHash 128 KiB minimum in Kotlin **before** freezing the oracle | 2026-11-23 | `BidiFormatter.kt`, `OpenSubtitlesHasher.kt` and their tests |
| Execution policy | No local test runs or toolchain installs; all execution through GitHub Actions/workflows | 2026-11-23 | Generator/self-check CI workflow; no local execution claimed |

## Open owner inputs still blocking work

- Official published OSHash media (`breakdance.avi`) through an approved test-data channel, if the
  published vector must be executable locally. Do not commit the copyrighted asset.
- Confirmation of the golden-vector JSON schema in [PLAN.md](PLAN.md) (the original Stage 2 brief
  is not in this repository; the file names and float encoding are reconstructed from reports).
- A named reviewer (D-6).
