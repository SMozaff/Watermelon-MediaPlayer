# Stage 3 report: Rust core, UniFFI spike and JVM parity

**Branch:** `migration/golden-oracle-scaffolding` · **PR:** #73
**Stage:** 3 of 6 · **Status:** IMPLEMENTED, GATE G1 CLOSED (see Stage 5 ART evidence)

> Post-audit update: the two items that kept G1 open — missing D-6 names and no on-device ART
> comparison — are now resolved. D-6 roles are recorded in [decision-log.md](decision-log.md), and
> the ART protocol and zero-mismatch result are in [stage-5-report.md](stage-5-report.md). The rest
> of this report is the historical Stage 3 record.

## Summary

The ten frozen Kotlin golden-vector components have Rust implementations under `rust/crates/core`,
and the D-3 comparator in `rust/crates/core/src/bin/parity.rs` passes all 39 committed cases.
A UniFFI boundary crate (`rust/crates/ffi`) and a workspace bindgen binary were added for the D-2
spike, with CI-generated Kotlin bindings. The D-2 PCM copy-cost measurement was executed on a
named GitHub-hosted runner. The Android NDK cross-compile job is in the same workflow.

No local Rust, Gradle or Android toolchain was installed or used. All execution evidence below is
from GitHub Actions. Stage 3 review cannot be signed off because D-6 names are still missing and
no on-device ART comparison has been performed.

## Evidence

| Item | Evidence | Result |
|---|---|---|
| D-1, D-2, D-3 approvals | [decision-log.md](decision-log.md) | Approved 2026-11-23 by SMozaff |
| Rust workspace compiles, tests and all targets | `cargo test --workspace --all-targets --locked` in workflow `migration-rust-parity.yml`; runs `45078413395`, `45078549135`, `45079298267`, `45079683879` | Green on the latest commit `f2375fb`; `cargo_exit=0` |
| Frozen-oracle parity, D-3 rule | `parity-report.txt` artifact from runs `45078549135`, `45079298267`, `45079683879` | `Rust parity: 39 cases, 0 failures`; 39 `PASS` lines; 1e-6 for derived floats, exact int/Long/hash, raw bits diagnostic |
| UniFFI Kotlin bindings generated from the built shared library | Workflow `migration-uniffi-spike.yml`, run `45079293229`; artifact `migration-uniffi-spike/build/uniffi-kotlin/uniffi/watermelon_ffi/watermelon_ffi.kt` | Generated successfully with UniFFI 0.29 in `--library` mode |
| D-2 PCM copy-cost spike | Same workflow, artifact `migration-uniffi/pcm-copy-report.txt`, run `45079293229` | 256,000-byte buffer, 3 warmup + 20 timed samples per path |
| Android NDK toolchain | Run `45079891144`, artifact `migration-ndk-libraries`: `metadata.txt`, `build.log`, `*-file.txt` and both `.so` files | arm64 and armv7 release libraries built in 1m24s for Android 24 with NDK 27.3.13750724 (r27d), `rustc 1.100.0` |
| Golden oracle / JVM baseline prerequisites | Runs `45074382455` / `45075503837` | 10 files, 39 cases, 111,890 bytes, byte-identical; 7 workloads × 20 raw samples |

## D-2 spike result

The spike compares a borrowed PCM byte slice with an owned `Vec<u8>` copy (the boundary UniFFI
uses for Kotlin `ByteArray`) around the same parity-tested accumulator. Host metadata for run
`45079293229`: Ubuntu 26.04.1 LTS, `rustc 1.100.0`, Intel Xeon 6975P-C, 4 vCPU, kernel
`7.0.0-1020-aws`. Both runs of the workflow produced the same shape of result; the table below
uses the green `45079293229` artifact.

| Path | Median ns | Mean ns |
|---|---:|---:|
| Borrowed `&[u8]` | 11,137,226 | 11,138,672 |
| Owned `Vec<u8>` copy | 10,986,942 | 10,988,803 |

The owned-copy path was slightly *faster* in this host measurement; the difference is well inside
sample noise and gives no evidence that PCM copying dominates the boundary. This supports
continuing with the approved UniFFI choice. These are host numbers, not ART/device numbers, and
are not a performance acceptance criterion.

## Defects found and fixed during Stage 3

1. **False-green CI (critical).** The parity step used `cargo run ... | tee ...`, so the pipeline
   exit status came from `tee` and run `45078413395` reported success while the report contained
   216 speech mismatches. Fixed with `set -o pipefail`; the corrected run `45078549135` genuinely
   failed or passed according to the comparator. No parity claim is based on the false-green run.
2. **Rust compile errors** from moving borrowed golden fields and a `parse_float(&Map)` type
   mismatch; fixed and re-verified in CI.
3. **Speech accumulator normalization** used `n` rather than Kotlin's `max(1L, n - 1L)` for
   `diffRms`/`zcr`; fixed and confirmed by the 39-case parity report.
4. **UniFFI bindgen CLI syntax.** UniFFI 0.29 requires `generate --library <path>`; the earlier
   positional form failed with `UDL file does not appear to be inside a crate`. The bindgen
   step now logs its output to an artifact on failure.

## Originally open before G1 / Stage 4 review — now closed

- **D-6 names:** resolved by the owner's delegation recorded in [decision-log.md](decision-log.md):
  implementation owner = the OpenCode agent session; independent reviewer = SMozaff.
- **On-device comparison:** resolved by the ART protocol in
  [stage-5-report.md](stage-5-report.md) — bidi and SRT parity on an x86_64 API 35 emulator with
  zero mismatches. Host JVM parity alone was not treated as sufficient.
- **Packaging and call path:** originally out of scope for Stage 3, subsequently added in Stages 4–5
  (JNA AAR, committed `.so` files, generated Kotlin binding, default-off `RustCutover`).

The original sentence "no production call path calls Rust yet" remains true for default CI and
release builds; the opt-in cutover path is described in [stage-5-report.md](stage-5-report.md).

## Related documents

- [PLAN.md](PLAN.md) · [Decision log](decision-log.md) · [Stage 2 report](stage-2-report.md)
- [Kotlin signature inventory](kotlin-signatures.md) · [Migration process audit](migration-process-audit.md)
