# Stage 6 report: cleanup scope

**Branch:** `migration/golden-oracle-scaffolding` · **PR:** #73
**Stage:** 6 of 6 · **Status:** DELIBERATELY DEFERRED (see DD-7)

## Decision

No Kotlin implementation is deleted in this change. Stage 6 is recorded as deliberately deferred,
not as completed, because the release default remains Kotlin (DD-6) and the frozen oracle plus the
rollback path both depend on `SrtParser.parseKotlin`, `BidiFormatter` and the other ports.

Deleting them would:

1. make the 39-case D-3 corpus unreproducible by the implementation it was generated from;
2. remove the rollback target that the Stage 5 fallback and every rollback trigger require;
3. break existing JVM unit tests that still call the Kotlin implementations;
4. claim a migration of the application runtime that has not happened — only the SRT/bidi boundary
   has a reachable Rust cutover, and it is default-off.

`scripts/verify_interfaces.py` is unchanged because no interface in `common-interfaces` was removed
or renamed. The `RustBridge` interface is internal to the `app` module and is an addition, not a
replacement, so it is out of scope for that guard.

## What was cleaned up

- Reverted a misplaced log-capture edit from the build-judge workflow (`.github/workflows/CI.yaml`)
  and placed it in the actual "Build and Test" workflow (`.github/workflows/android.yml`).
- Replaced the invalid version-catalog `ext` key with a direct `@aar` dependency after CI run
  `45082203703` proved Gradle rejects `ext`.
- Added the JNA AAR and POM SHA-256 values to `gradle/verification-metadata.xml`, so strict
  dependency verification remains intact rather than being disabled.
- Normalized trailing whitespace in the generated UniFFI Kotlin binding so `git diff --check`
  passes, while recording both the artifact hash and committed hash in
  [native-artifacts.md](native-artifacts.md).

## Exit criteria — not met, by decision

| Criterion | State |
|---|---|
| Superseded Kotlin implementations removed | Not done (DD-7) |
| `verify_interfaces.py` updated | Not applicable: no interface removed |
| Docs archived | Superseded; current reports are linked from [PLAN.md](PLAN.md) and kept current instead |

This is the one stage not claimed complete. The owner can close it by approving a physical-device
soak test and a release default change, then removing Kotlin ports component by component.

## Related documents

- [PLAN.md](PLAN.md) · [Stage 5 report](stage-5-report.md)
- [Delegated decisions](delegated-decisions.md) DD-6, DD-7
