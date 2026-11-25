# Final verification: Rust-only cutover

**Final code head:** `e2d8adf3ac17db44000be28cbfe1988d1f19e806` (`main`). The only commit after
`fbdf094` was this evidence document; no source, artifact or workflow changed.
**Decision:** D-7 (delete only the ten oracle-covered Kotlin ports) and D-8 (Rust-only runtime,
no fallback) recorded in [decision-log.md](decision-log.md).

All verification was executed in GitHub Actions. No local tests, local toolchains or physical
devices were used. All runs below are on the final head; a false-green run that used `tee`
without `set -o pipefail` (`45078413395`) was excluded earlier and is not evidence.

## Gate results

| Run | Workflow | Job | Result |
|---|---|---|---|
| [`45681853710`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681853710) | build-judge | all gates incl. migration ladder and player runtime | success |
| [`45681853722`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681853722) | Build and Test | `build` | success |
| [`45681853824`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681853824) | CodeQL | `Analyze Kotlin and Java` | success |
| [`45681869376`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681869376) | Migration Rust parity | Rust vs frozen Kotlin golden, D-3 rule | success |
| [`45681876684`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681876684) | Migration golden vectors | frozen corpus inventory, checksums, structural checks | success |
| [`45681882946`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681882946) | Migration device parity | ART parity on x86_64 API 35 emulator | success |
| [`45683793907`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45683793907) | Dependency Integrity | clean resolution, strict verification, MP3 compile gate | success (retry) |
| [`45681896751`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681896751) | Migration UniFFI spike | binding generation and three-ABI NDK build | success |

Dependency Integrity failed once at [`45681890688`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45681890688)
during its clean Gradle resolution step (no artifacts were produced, so the failure was before the
MP3 test gate). The same job had already passed on the identical source at `21b60de` and `fbdf094`,
and passed on retry at `45683793907`; it is recorded as a transient dependency-resolution failure,
not a suppressed code failure.

The device-parity workflow now compiles `app/src/androidTest` before starting the emulator and
uploads `androidTest-compile.log` on failure (`set -o pipefail`), so a silent compile failure can no
longer be mistaken for an emulator problem.

## Failures found and fixed during final verification

1. `RustAdapters.kt` passed a `WireFloat` to `Json.encodeToJsonElement` without an explicit
   serializer; Kotlin could not infer the reified type parameter. Fixed with
   `WireFloat.serializer()` at all seven call sites.
2. The speech accumulator factory declared an `internal` function with a private return type.
   Fixed by declaring the interface `SpeechAccumulator` as the return type.
3. The ART test KDoc contained `` `testdata/golden/*.json` ``; the `/*` sequence opened a nested
   Kotlin block comment that was never closed. Fixed by removing the glob from the comment.
   This was invisible to unit-test gates because no other workflow compiles `app/src/androidTest`.

## Native artifacts and oracle

- The committed Kotlin binding and three `.so` files are the same-run pair produced in
  [`45097554094`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45097554094)
  from commit `6723f19`. Hashes and metadata are in [native-artifacts.md](native-artifacts.md) and
  `app/src/main/jniLibs/SHA256SUMS`; local `sha256sum -c` passes.
- The fresh spike run `45679889287` rebuilt the binding and libraries from the final head. The
  binding is byte-identical after trailing-whitespace/EOF normalization; the `.so` bytes differ,
  confirming these native outputs are not bit-reproducible across runs. The committed artifacts
  were therefore **not** replaced, because mixing a binding from one run with libraries from
  another can break UniFFI checksum validation.
- `testdata/golden/` remains the frozen Kotlin-generated oracle (10 files, 39 cases,
  `generator:"kotlin"`). Its `sha256sum -c` passes locally and in `45679872400`. It is never
  regenerated from Rust, and Rust parity is always Rust versus this frozen oracle, never
  Rust versus Rust (DD-12).

## Scope and evidence limits

- Runtime is Rust-only: `AppDependencies` injects UniFFI adapters unconditionally; no Kotlin
  fallback, shadow mode, migration flag or in-app rollback switch remains. Rollback is a
  Git/release operation only.
- ART evidence is emulator-only (x86_64, API 35). No physical-device soak test was performed and
  no device performance number is claimed. The historical JVM baseline is documented as
  non-reproducible from this checkout.
- JNA `5.19.1@aar` remains pinned and packaged; no telemetry, upload or non-debug logging was
  added (D-4).
