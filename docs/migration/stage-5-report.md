# Stage 5 report: reversible cutover and Android ART evidence

**Branch:** `migration/golden-oracle-scaffolding` · **PR:** #73
**Branch head:** `8b65b8af8c9850d3d07b9b9ae2eef57ba4397af2`
**Stage:** 5 of 6 · **Status:** IMPLEMENTED, GATE G3 CLOSED WITH CAVEAT

## Cutover design

- `SrtParser.Backend` is a `fun interface` with a volatile `backend` property. The default
  implementation delegates to `SrtParser.parseKotlin`, so the oracle and rollback path are intact.
- `RustCutover` is installed by `AppDependencies` **only** when `watermelon.useRustCore=true`.
  It calls the Rust `srt_parse_json`, `bidi_is_rtl` and `bidi_format` exports through the
  UniFFI-generated Kotlin API. Any native failure is caught, logged locally and falls back to
  `SrtParser.parseKotlin`; no exception reaches a user.
- With `watermelon.rustShadowMode=true`, the Kotlin result is computed first and compared under
  the D-3 rule before the Rust result is returned. This is the only path that returns Rust output.
- Release and CI builds keep all flags `false`. The default runtime remains Kotlin; this is a
  validated *capability*, not a default-runtime replacement.

## On-device ART protocol (DD-4)

`app/src/androidTest/kotlin/com/watermelon/app/migration/RustDeviceParityTest.kt` runs on a pinned
x86_64 Android API 35 emulator via
`.github/workflows/migration-device-parity.yml` (action SHA
`a421e43855164a8197daf9d8d40fe71c6996bb0d`). It enables all three flags and checks:

1. the packaged `.so` loads and the UniFFI checksums match on ART;
2. `bidiIsRtl` and `bidiFormat` match `BidiFormatter` for LTR, RTL, mixed and punctuated samples;
3. `srtParseJson` matches `SrtParser.parseKotlin` for a two-cue mixed-language SRT.

Acceptance is zero mismatches. D-3's exact-string rule applies to these outputs. The workflow
uploads protocol metadata and Android test reports as artifacts.

### Final CI matrix on branch head `8b65b8a`

| Evidence | Run | Result |
|---|---|---|
| ART parity on x86_64 API 35 emulator | `45083247641` | `tests="3" failures="0" errors="0"`; all three named tests passed with zero mismatches |
| JVM parity for all ten components | `45083247717` | `Rust parity: 39 cases, 0 failures` |
| Build and Test (compile, lint, unit/instrumentation gates) | `45083247667` | green |
| Full build-judge gate | `45083247622` | green; all five jobs passed, including the player A1–A4 runtime regression gate |
| UniFFI spike (bindings, PCM copy measurement) | `45083247660` | green |
| Dependency Integrity | `45083247674` | green; strict verification retained with JNA pins |
| CodeQL | `45083247650` | green |

All seven runs were triggered by PR #73 for branch head `8b65b8af8c9850d3d07b9b9ae2eef57ba4397af2`.
The device artifact's `metadata.txt` records PR checkout commit
`4feacc1a9f681b24cd327991ce982422789cf92f` because GitHub Actions checks out the pull request's
merge ref; the workflow run API identifies the branch head above. The earlier `45081607445`
emulator boot timeout and `45082914674` backslash-continuation failure were infrastructure/script
defects, fixed by DD-11 and the one-line script change; neither is used as parity evidence.

## Rollback triggers

The triggers in [PLAN.md](PLAN.md) remain in force. In particular: any D-3 mismatch, native load
failure, new telemetry, or unverified ABI change reverts to the Kotlin path by turning the flag
off; `SrtParser.parseKotlin` is never deleted while the flag exists.

**Caveat:** evidence comes from a CI emulator, not a physical device fleet. No physical-device
performance claim is made. Flip the flag for a release only after a physical-device soak test.

## Related documents

- [PLAN.md](PLAN.md) · [Stage 4 report](stage-4-report.md) · [Stage 6 report](stage-6-report.md)
- [Delegated decisions](delegated-decisions.md) DD-4, DD-5, DD-6, DD-8
