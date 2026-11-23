# Stage 6 report: cleanup and Rust-only cutover

**Branch:** `main` · **Stage:** 6 of 6 · **Status:** DONE under owner decisions D-7/D-8

## Decision history

The earlier version of this report recorded Stage 6 as deliberately deferred (DD-7), because the
release default was Kotlin and the frozen oracle plus the rollback path depended on the Kotlin
ports. The owner subsequently chose:

- **D-7:** delete only the ten superseded, oracle-covered Kotlin ports;
- **D-8:** make Rust the sole runtime and remove the fallback/degradation scaffolding.

DD-6 (opt-in reversible cutover) and DD-7 (defer deletion) are superseded. Rollback is now a
Git/release operation, not an in-app flag.

## What changed

### Deleted Kotlin implementations (ten)

`SrtParser`, `BidiFormatter`, `OpenSubtitlesHasher`, `SubtitleTimeMapper`, `LinearDriftCorrector`,
`ActivityCorrelatorImpl`, `OffsetConsensus`, `SubtitleProbeSelectorImpl`,
`SubtitleActivityBuilderImpl` and `SpeechLikelihoodAccumulator`.

Their Kotlin-only characterization, unit, golden-generator and benchmark tests were deleted with
them. `GoldenJson.kt` and the structural self-check tests remain, as does the frozen corpus.

### Removed cutover scaffolding

`MigrationFlags`, `RustMigrationLog`, `RustBridge`, `RustBridgeImpl`, `MigrationDiagnostics` and
`RustCutover`, together with the `-Pwatermelon.useRustCore`, `-Pwatermelon.rustShadowMode` and
`-Pwatermelon.rustDebugLogging` Gradle properties and their `BuildConfig` fields. JNA packaging
was kept; it is required by the generated UniFFI binding.

### Added Rust-only seams and adapters

`common-interfaces/.../SubtitlePorts.kt` declares `SrtParserPort`, `SubtitleHasherPort`,
`SubtitleOffsetConsensus`, `PcmChunk`, `SpeechAccumulator` and `SpeechAccumulatorFactory`.
`app/.../migration/RustAdapters.kt` implements them over the UniFFI exports. `AppDependencies`
injects those adapters unconditionally into the repository, sidecar source, speech probe source and
sync coordinator. `SubtitleHasherPort` passes only the first and last 64 KiB of a media file, so a
multi-gigabyte video is never copied into native memory.

## Exit criteria

| Criterion | State |
|---|---|
| Superseded Kotlin implementations removed | Done: exactly the ten oracle-covered ports |
| No Kotlin fallback remains | Done: no runtime switch, flags or shadow scaffolding |
| `verify_interfaces.py` updated | Not applicable: removed classes were additions/ports; shared contracts were kept |
| Golden oracle preserved | Done: corpus frozen and workflow converted to verification-only (DD-12) |
| Docs archived | Superseded by current reports linked from [PLAN.md](PLAN.md) |

## Evidence limits

ART evidence is from the pinned x86_64 API 35 emulator only. No physical-device soak test was
performed and no device performance number is claimed.

## Related documents

- [PLAN.md](PLAN.md) · [Stage 5 report](stage-5-report.md)
- [Decision log](decision-log.md) D-7/D-8 · [Delegated decisions](delegated-decisions.md) DD-6, DD-7, DD-12
