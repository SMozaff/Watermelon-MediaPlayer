---
status: active
supersedes: migration-architecture
---

# Kotlin-to-Rust migration architecture (Rust-only cutover, 2026-11-23)

- Rust core lives in `rust/crates/core`; UniFFI boundary in `rust/crates/ffi`; bindgen binary in `rust/crates/uniffi-bindgen`. The exported surface now covers all ten frozen-vector components plus a streaming `openSubtitlesHashChunks` export for the first/last 64 KiB of media files.
- Golden oracle: `testdata/golden/` (10 files, 39 cases), frozen with `generator:"kotlin"`. Parity comparator is `rust/crates/core/src/bin/parity.rs` using D-3: 1e-6 for derived floats, exact integers/Long/hash/strings, raw bits diagnostic only. The Kotlin generator tests were deleted; `migration-golden-vectors.yml` is verification-only (DD-12). Never regenerate the corpus from Rust.
- Native artifacts are CI build outputs committed under `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}`; generated Kotlin binding is `app/src/main/kotlin/uniffi/watermelon_ffi/watermelon_ffi.kt` (UniFFI 0.29.5, JNA 5.19.1). Provenance and hashes: `docs/migration/native-artifacts.md`. Binding and `.so` files must come from the same CI run.
- Runtime: Rust-only (D-8). `common-interfaces/.../SubtitlePorts.kt` declares the seams; `app/.../migration/RustAdapters.kt` implements them over UniFFI; `AppDependencies` injects adapters unconditionally. There is no Kotlin fallback, no `-Pwatermelon.*` flag, no runtime switch, and no shadow scaffolding. Rollback is Git/release only. JNA packaging stays.
- Logging remains local-only (D-4): no telemetry, upload or non-debug logging.
- Device protocol: `.github/workflows/migration-device-parity.yml` runs `app/src/androidTest/.../RustDeviceParityTest.kt` on an x86_64 API 35 emulator against frozen goldens; no flags. Emulator-only evidence; no physical-device performance claim.
- All execution evidence comes from GitHub Actions; local checks are limited to YAML/XML parse, `git diff --check` and `sha256sum -c`. Push workflow changes over SSH; the HTTPS OAuth credential lacks `workflow` scope. Use `env -u GH_TOKEN gh ...`.
- Delegated choices and rationale: `docs/migration/delegated-decisions.md`; owner decisions D-7/D-8 in `docs/migration/decision-log.md`.