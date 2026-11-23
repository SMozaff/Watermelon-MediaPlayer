# Kotlin-to-Rust migration architecture (2026-11-23)

- Rust core lives in `rust/crates/core`; UniFFI boundary in `rust/crates/ffi`; bindgen binary in `rust/crates/uniffi-bindgen`.
- Golden oracle: `testdata/golden/` (10 files, 39 cases). Parity comparator is `rust/crates/core/src/bin/parity.rs` using D-3: 1e-6 for derived floats, exact integers/Long/hash/strings, raw bits diagnostic only.
- Native artifacts are CI build outputs committed under `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}`; generated Kotlin binding is `app/src/main/kotlin/uniffi/watermelon_ffi/watermelon_ffi.kt` (UniFFI 0.29.5, JNA 5.19.1). Provenance and hashes: `docs/migration/native-artifacts.md`.
- Cutover seam: `SrtParser.Backend` with `parseKotlin` fallback. `RustCutover` installed only by `AppDependencies` when `watermelon.useRustCore=true`; any native exception falls back to Kotlin. Release/CI default is Kotlin.
- Logging: `RustMigrationLog` is local-only debug logging; `MigrationDiagnostics.compare` always returns the Kotlin value. No telemetry or upload.
- Device protocol: `.github/workflows/migration-device-parity.yml` runs `app/src/androidTest/.../RustDeviceParityTest.kt` on x86_64 API 35 emulator with all three flags enabled; zero mismatches required.
- All execution evidence comes from GitHub Actions; local checks limited to YAML parse, `git diff --check`, `sha256sum -c`.
- Delegated choices and rationale: `docs/migration/delegated-decisions.md`.
