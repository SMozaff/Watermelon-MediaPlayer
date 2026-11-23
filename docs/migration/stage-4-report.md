# Stage 4 report: shadow mode and native packaging

**Branch:** `migration/golden-oracle-scaffolding` · **PR:** #73
**Stage:** 4 of 6 · **Status:** IMPLEMENTED, GATE G2 CLOSED

> Historical record: the `MigrationFlags`/`RustBridge`/`MigrationDiagnostics` shadow seam described
> below was deliberately removed when the owner chose D-8 (Rust-only runtime). JNA packaging and
> the local-only logging policy remain. The current wiring is described in
> [stage-6-report.md](stage-6-report.md).

## What was built

Stage 4 takes the D-4 seam from scaffolding to an executable local comparison and packages the
native artifact needed to run it:

- `MigrationFlags` exposes the three default-false build flags (`USE_RUST_CORE`,
  `RUST_SHADOW_MODE`, `RUST_DEBUG_LOGGING`) as compile-time `BuildConfig` values.
- `RustBridgeImpl` implements the `RustBridge` interface with the UniFFI-generated Kotlin API.
  `MigrationDiagnostics.compare` is wired to it in `AppDependencies`, but it calls native code
  only when both shadow flags are enabled and always returns the Kotlin value.
- `RustMigrationLog` writes comparisons to local logcat only, in debug builds with the opt-in flag.
  There is no telemetry, upload or release logging, so D-4 is preserved.
- The generated UniFFI Kotlin binding and three CI-built `.so` files are committed with SHA-256
  provenance in [native-artifacts.md](native-artifacts.md). JNA `5.19.1@aar` is selected directly in
  `app/build.gradle.kts`; version catalogs reject an `ext` key (proven by CI run `45082203703`).

## Exit criteria

| Criterion | Evidence | Result |
|---|---|---|
| All ten vectors pass against Rust | `migration-rust-parity.yml`, run `45083247717` (and earlier `45079683879`) | `Rust parity: 39 cases, 0 failures`, `cargo_exit=0` |
| D-4 logging policy approved | [decision-log.md](decision-log.md) D-4 | Local only, no upload |
| No user-visible behavior change | `MigrationDiagnostics.compare` returns `kotlinValue`; flags default false in CI/release | Verified by source review and by all green CI gates on the final head |
| Native bridge packaged | `app/src/main/jniLibs/*/libwatermelon_ffi.so` + generated binding; hashes verified locally with `sha256sum -c` | OK; NDK provenance run `45080638272` |

G2 is therefore closed. It is a capability gate: the shadow comparison is reachable only with
explicit local flags, exactly as D-4 specifies.

## Decisions applied

See [delegated-decisions.md](delegated-decisions.md) DD-4 (ART protocol), DD-5 (JNA/packaging),
DD-8 (ABI set) and DD-9 (CI truthfulness). The false-green `tee` incident is not used as evidence.

## Related documents

- [PLAN.md](PLAN.md) · [Stage 3 report](stage-3-report.md) · [Stage 5 report](stage-5-report.md)
- [Native artifacts](native-artifacts.md) · [Delegated decisions](delegated-decisions.md)
