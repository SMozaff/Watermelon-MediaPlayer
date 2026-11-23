# Native artifacts provenance

The Android library and generated Kotlin bindings committed in this repository are **build
outputs**, not hand-written source. They were produced in GitHub Actions and copied from the
published artifacts; no local Rust or Android toolchain was used.

The current set was regenerated from commit `6723f19` in run
[`45097554094`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45097554094),
together with the Kotlin binding. Artifact metadata records `rustc 1.100.0 (0ef1bc386 2026-11-09)`
and NDK `27.3.13750724` for the NDK job. The binding and the three `.so` files **must** come from
the same run; mixing them can break UniFFI checksum validation.

| Artifact | Produced by | Run | SHA-256 |
|---|---|---|---|
| `app/src/main/jniLibs/arm64-v8a/libwatermelon_ffi.so` | `migration-uniffi-spike.yml` → `android-ndk` | `45097554094` | `87fd67eadc97eb799b0ee2fc1056536b16658c9b5d1d989b5d8a34b88c83920b` |
| `app/src/main/jniLibs/armeabi-v7a/libwatermelon_ffi.so` | same | `45097554094` | `8ef1c2a2a1466b5957a9406160a4fac8f3aed920df1cd6c9e26e3945bba4d7da` |
| `app/src/main/jniLibs/x86_64/libwatermelon_ffi.so` | same | `45097554094` | `b69f01a101aee59532eca12785f0d9a8f816fad4aeb5f006f51c5b0a227613b9` |
| `app/src/main/kotlin/uniffi/watermelon_ffi/watermelon_ffi.kt` | `migration-uniffi-spike.yml` → `spike` (UniFFI 0.29.5) | `45097554094` | `31d8f9929d5ac79feaa60bda0d38d4b7a49996629992bea381af377012b33164` in the artifact; committed copy has trailing whitespace and extra EOF blank lines removed, `44d640d5b1f2aae3942684d031f8fc2d8ea36f0ce0615d469b917ff58ee03497` |

The generated binding exports all fifteen functions now used by the Rust-only adapters: the ten
frozen-vector components, the streaming `openSubtitlesHashChunks`, bidi/SRT helpers and the PCM copy
spike. `openSubtitlesHashChunks` exists because a media file must not be copied through FFI in full:
callers pass only the first and last 64 KiB plus the real file size.

All three `.so` files are ELF shared objects for Android 24, built by NDK r27d (27.3.13750724)
with `rustc 1.100.0`. They must be regenerated whenever `rust/crates/ffi` or the UniFFI version
changes; CI verifies the rebuild but does not write to the repository. The committed native
hashes are also checked by `app/src/main/jniLibs/SHA256SUMS`:

```sh
cd app/src/main/jniLibs && sha256sum -c SHA256SUMS
```

The JNA dependency is pinned to `5.19.1` in `gradle/libs.versions.toml` and is selected with
`@aar` in `app/build.gradle.kts` (Gradle version catalogs reject an `ext` key). Its AAR and POM
SHA-256 values are pinned in `gradle/verification-metadata.xml`, so strict dependency verification
still passes. JNA is only used by the generated bindings, adds no network surface and performs no
upload.
