# Delegated decisions made during Stages 3–6

**Date:** 2026-11-23
**Authority:** The repository owner instructed: “Finish the job. choose best options and document
every decisions you made in detail. proceed.” This closed the open inputs listed in
[proposed-decisions.md](proposed-decisions.md) and authorized the implementation agent to choose.
[D-1…D-6](decision-log.md) remain the owner's recorded decisions; this file records the *delegated
choices* made under that instruction, with the option rejected and the consequence of each choice.

No local tests or toolchain installs were used. Every build, test and benchmark cited here ran in
GitHub Actions.

## DD-1 — D-6 roles

- **Chosen:** implementation owner = OpenCode agent session that made the commits; independent
  reviewer = `SMozaff` (repository owner). Recorded as role assignments, not invented third parties.
- **Rejected:** leaving Stage 3 review unsigned, or inventing an outside reviewer's name.
- **Rationale:** the owner delegated the choice. The person who approved the migration can review
  the agent's implementation independently; this preserves the audit control the audit asked for.
- **Consequence:** Stage 3 review is now attributable. PR #73 still requires the owner's normal
  merge approval; this is a review assignment, not a self-merged PR.

## DD-2 — Golden-vector schema

- **Chosen:** confirm the reconstructed schema in [PLAN.md](PLAN.md) exactly as implemented:
  `{component, generator, cases:[{id,input,output}]}`, 10 files, 39 cases, floats encoded as
  `{"decimal":"…","bits":"…"}` with 8/16 hex digits for f32/f64.
- **Rejected:** changing the schema or regenerating the corpus to match a guessed original brief.
- **Rationale:** the original Stage 2 brief is not in the repository, but the corpus was generated
  from the committed Kotlin code, passed a structural self-check and was byte-identical across two
  runs (`45074382455`). Changing it now would invalidate all 39 parity cases and the D-3 comparator.
- **Consequence:** the schema is frozen; future changes need a new owner decision and a new corpus.

## DD-3 — Official `breakdance.avi` OSHash vector

- **Chosen:** do not commit or fetch copyrighted media. Keep the deterministic generated OSHash
  vectors in `testdata/golden/hash_opensubtitles.json`; treat the published file vector as an
  optional external check, not an acceptance gate.
- **Rejected:** adding a third-party media asset to the repository or blocking G1 on it.
- **Rationale:** no approved test-data channel was supplied, and the risk of committing a
  copyrighted asset is higher than the marginal coverage of one published hash value.
- **Consequence:** OSHash parity is proven against generated inputs only. This limitation is stated
  in the stage report rather than hidden.

## DD-4 — On-device ART protocol and threshold

- **Chosen:** a pinned `ubuntu-latest` x86_64 Android API 35 emulator running
  `app/src/androidTest/.../RustDeviceParityTest.kt` with all three flags enabled. Acceptance =
  zero mismatches for bidi formatting, RTL detection and SRT parsing; D-3's exact-string rule
  applies because these outputs are strings/booleans. Artifacts include protocol metadata and test
  reports.
- **Rejected:** claiming JVM parity as device evidence, or waiting indefinitely for physical
  hardware that was never supplied.
- **Rationale:** an emulator runs the same ART runtime and the same packaged `.so`; it is
  reproducible in CI and records a named protocol. It cannot prove physical-device performance,
  which is why no performance claim is attached to this gate.
- **Consequence:** G1 correctness evidence exists; performance on physical devices remains out of
  scope and is explicitly not claimed.

## DD-5 — JNA and APK packaging

- **Chosen:** add pinned `net.java.dev.jna:jna:5.19.1@aar`, commit the UniFFI-generated Kotlin
  binding and three CI-built `.so` files (arm64-v8a, armeabi-v7a, x86_64) with SHA-256 provenance,
  and add a `testInstrumentationRunner` to the app module.
- **Rejected:** hand-written JNI (contrary to D-2) or keeping the Rust core host-only so no device
  test can run.
- **Rationale:** UniFFI 0.29.5 generates Kotlin source that requires JNA; the device protocol needs
  the library inside the APK. JNA is a local FFI shim and adds no upload/network path, so D-4 holds.
  Committing the artifacts with hashes keeps them reproducible and auditable.
- **Consequence:** APK size grows by roughly 8 MB across ABIs. The generated file's trailing
  whitespace was normalized for `git diff --check`; both artifact and committed hashes are recorded
  in [native-artifacts.md](native-artifacts.md).

## DD-6 — Cutover strategy

- **Chosen:** implement a reversible, opt-in cutover. `SrtParser` gains a backend seam;
  `RustCutover` is installed only when `watermelon.useRustCore=true`. Any native failure falls back
  to `SrtParser.parseKotlin` and logs locally. Shadow comparison remains gated by the two debug
  flags and returns the Kotlin value. Production/CI flags stay `false`.
- **Rejected:** flipping Rust on as the default for all users, or leaving Stage 5 as a flag with no
  reachable implementation.
- **Rationale:** the device evidence is from an emulator, not a physical fleet, and removing the
  Kotlin path would destroy the oracle and the rollback. A reachable but default-off implementation
  proves the cutover works and keeps the blast radius zero until a release decision is made.
- **Consequence:** Stage 5 is implemented and validated, but the release default is still Kotlin.
  Flip `-Pwatermelon.useRustCore=true` only after a physical-device soak test.

## DD-7 — Stage 6 cleanup scope

- **Chosen:** no Kotlin implementation was deleted. Cleanup is limited to honest status reporting,
  provenance/hashes, PR documentation and keeping `scripts/verify_interfaces.py` unchanged because
  no interfaces were removed.
- **Rejected:** deleting `SrtParser.parseKotlin`, `BidiFormatter` or other ports to claim “cleanup
  done” while the release default is Kotlin.
- **Rationale:** DD-6 keeps Kotlin authoritative. Deleting it would make the frozen oracle
  unreproducible, break the rollback and break the JVM unit tests that still call it.
- **Consequence:** Stage 6 deletion work is explicitly deferred; the migration is complete as a
  capability, not as a default-runtime replacement. This is the one stage not claimed complete.

## DD-8 — NDK ABIs

- **Chosen:** build arm64, armv7 and x86_64; do not build 32-bit x86.
- **Rejected:** arm64 only (no emulator) or all four ABIs (unused 32-bit x86 binary).
- **Rationale:** arm64/armeabi-v7a cover Android devices; x86_64 covers the reproducible CI
  emulator. Adding x86 has no consumer and would increase APK size.
- **Consequence:** the device workflow pins `abi: x86_64`.

## DD-9 — CI truthfulness

- **Chosen:** every comparator and benchmark pipeline uses `set -o pipefail`, and failure logs are
  written to uploaded artifacts (compile, bindgen, NDK build).
- **Rejected:** the earlier `tee`-only pipeline that made run `45078413395` report success despite
  216 mismatches.
- **Rationale:** a false-green gate is worse than no gate. Failed steps must leave evidence.
- **Consequence:** all parity, spike and NDK results cited in the reports are from genuinely green
  runs; the false-green run is documented, not used.

## DD-10 — PR description

- **Chosen:** update PR #73's body through the REST API after `gh pr edit` failed on GitHub's
  deprecated Projects (classic) GraphQL field, and keep the body factual about open items.
- **Rejected:** leaving the stale body claiming D-1…D-6 blank and “no Rust port has started”.
- **Rationale:** reviewers need the current scope; a CLI deprecation error is not a reason to leave
  inaccurate review material.
- **Consequence:** the PR body and the migration docs now agree on stage status and evidence.
