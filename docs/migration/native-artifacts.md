# Native artifacts provenance

The Android library and generated Kotlin bindings committed in this repository are **build
outputs**, not hand-written source. They were produced in GitHub Actions and copied from the
published artifacts; no local Rust or Android toolchain was used.

| Artifact | Produced by | Run | SHA-256 |
|---|---|---|---|
| `app/src/main/jniLibs/arm64-v8a/libwatermelon_ffi.so` | `migration-uniffi-spike.yml` → `android-ndk` | `45080638272` | `f59585d8598cdf27d65a70c05bb33a0a70e679d5cb829adcf1abca47070e7b6f` |
| `app/src/main/jniLibs/armeabi-v7a/libwatermelon_ffi.so` | same | `45080638272` | `53f480b1ccad6a6f0597cb44e2b19abc850da29f2cb26d2c7c9ffd644d136b04` |
| `app/src/main/jniLibs/x86_64/libwatermelon_ffi.so` | same | `45080638272` | `761508e1cce6d08a3ec0368320ac0d957c757744e8c0426813c0d748602a5b2b` |
| `app/src/main/kotlin/uniffi/watermelon_ffi/watermelon_ffi.kt` | `migration-uniffi-spike.yml` → `spike` (UniFFI 0.29.5) | `45079293229` | `dc38bd481fdbc9799bf96fff974bff1c279a6baebb3b79e6808218bcda5d1378` in the artifact; committed copy has trailing whitespace removed, `9c94ee085cecf0123a422dff7c2120c600d77565ff5a3f49143fd7b4f39191e6` |

All three `.so` files are ELF shared objects for Android 24, built by NDK r27d (27.3.13750724)
with `rustc 1.100.0`. They must be regenerated whenever `rust/crates/ffi` or the UniFFI version
changes; CI verifies the rebuild but does not write to the repository.

The JNA dependency is pinned to `5.19.1` in `gradle/libs.versions.toml` and is selected with
`@aar` in `app/build.gradle.kts` (Gradle version catalogs reject an `ext` key). Its AAR and POM
SHA-256 values are pinned in `gradle/verification-metadata.xml`, so strict dependency verification
still passes. JNA is only used by these generated bindings, adds no network surface and the D-4
logging seam performs no upload.
