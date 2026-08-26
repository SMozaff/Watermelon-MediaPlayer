# Sprint 4 Release Decision (WM-407)

**Prepared:** 2026-08-26
**Evaluated against:** branch `claude/clone-repo-read-audit-5ye1cd` (this session's remediation
work, based on `main` at `2d5eacc`) — this branch is not yet merged to `main`; `main` itself
carries none of this session's fixes.
**Recommendation: HOLD.**

This is not a close call — three of WM-407's own stated prerequisites are unmet, and the task's
own definition of done is explicit that a Go decision without them "would misrepresent the actual
state of the project." All three are documented below with the real evidence gathered, not an
assumption.

---

## Prerequisite check (per WM-407's own definition of done)

| Prerequisite | Status | Evidence |
|---|---|---|
| `./gradlew build` passes | **FAILS** | See "Build/test verification" below — real command run this session, verbatim failure captured. |
| `./gradlew test` passes | **NOT REACHABLE** | Build fails at Gradle's configuration phase, before any test task can run — see below. |
| Device/accessibility suite results (WM-402) | **MISSING** | Suite was not run this session — see "Device/accessibility suite" below for why, per that task's own stop condition. |
| Findability study results (WM-401) | **MISSING (real)** | No participant sessions have been run. Preparation-only work was done — see "Findability study" below. |

Given this, a "Go" recommendation is not available. This document proceeds to lay out what a
future retriage needs, per WM-407's own required structure, rather than stopping short of writing
anything down.

---

## Build/test verification (run fresh this session)

`./gradlew build --stacktrace` was actually executed against this branch (not assumed from
memory of an earlier attempt). It fails at Gradle's **configuration** phase — before compiling a
single module or running a single test:

```
* What went wrong:
Plugin [id: 'com.android.application', version: '8.7.0', apply: false] was not found in any of the following sources:
...
- Plugin Repositories (could not resolve plugin artifact 'com.android.application:com.android.application.gradle.plugin:8.7.0')
```

Root cause: this session's sandbox has organization-policy-denied outbound access to
`dl.google.com` and `jitpack.io` (confirmed via the environment's proxy status endpoint — 403,
explicit policy denial, not a transient failure). Since AGP itself can only resolve from
`google()`, **no module in this project can be compiled in this environment**, regardless of
which module is targeted or how small — this was independently reconfirmed by also attempting a
scoped `:common-interfaces:build` earlier in this session, which fails identically because Gradle
evaluates the whole project's `plugins {}` declarations during configuration.

`./gradlew test` was not run separately: it cannot get past the same configuration failure, so a
second attempt would only reproduce the identical error.

**This failure is environmental, not caused by any code change.** It would reproduce identically
against `main` directly (nothing about the AGP plugin resolution depends on this branch's diffs)
— confirmed by the fact the very first build attempt in this session, before any of this
session's commits existed, failed with the same root cause. This project's build has never
successfully compiled end-to-end in any session on record (per the original audit and this
session's own environment-setup attempt) — that remains true today.

---

## Consolidated findings

| Source | Finding | Severity | Status |
|---|---|---|---|
| Environment/build (this session) | No module compiles in this sandbox — `dl.google.com`/`jitpack.io` policy-blocked | Blocking (verification) | **Open** — needs an environment with that egress allowed, or CI (which has it) |
| JitPack `java-lame` resolution | Never verified against a real build in any session | Blocking (audio-extraction feature) | **Open** — blocked by the above |
| `Mp3Encoder`/`AudioExtractor` | Never compiled or run against real audio | Blocking (audio-extraction feature) | **Open** — blocked by the above |
| Zero unit tests (audit finding) | No test coverage exists for `MediaJobManager`, `PlaybackControllerImpl`, `SubtitleRepositoryImpl`, or migration edge cases beyond the one existing test | Blocking (regression risk) | **Open** — blocked by the above (can't run `./gradlew test`) |
| Sleep-timer "end of folder" mode | Was a stub (always paused, never distinguished last-item) | Was blocking | **Remediated, not retested** — fixed this session (`PlaybackController.setQueueContext`, see PR); logic reasoned through and cross-checked against real Media3 `Player.Listener` docs, but not build/run-verified due to the same environment blocker |
| `VideoCompressor` encoder settings | File's own doc comment claimed prior Context7 verification | Non-blocking (already resolved) | **Verified this session** — independently re-checked `DefaultEncoderFactory.Builder`/`VideoEncoderSettings.Builder.setBitrate()`/`setEnableFallback(false)` against real Media3 source (`BitrateAnalysisTest.java`) via Context7; matches exactly. No code change needed. Not compile-verified (same environment blocker), but this is corroboration of an already-confirmed API surface, not new unverified code. |
| Subtitle remote-lookup reachability probe | Was a placeholder | Was blocking | **Remediated, not retested** — real `ConnectivityManager`/`NetworkCapabilities` connectivity check added this session, gating the existing (already-correct) mirror-rotation/timeout/failover logic in `SubtitleApiClient`. Not compile-verified (same environment blocker). |
| INTERNET permission undisclosed | README claims "zero telemetry" with no user-facing explanation of network use | Was non-blocking (trust/disclosure) | **Remediated this session** — manifest comment + new Settings entry, confirmed subtitle lookups are the app's only network usage |
| Job-notification placeholder icon | Used a generic system icon | Non-blocking (cosmetic) | **Remediated this session** — new branded vector icon |
| No release signing/versioning | Only a debug build type existed | Blocking (release mechanics) | **Remediated this session** — signing config (property-gated, no real credentials), version bump, CI `assembleRelease` step |
| Premium/paywall placeholder UI | Paywall visible with no real purchase flow | Blocking if shipped as-is | **Not addressed this session** — out of this session's task scope; still an open decision per the remediation plan (hide vs. build real billing) |
| Sprint-1 P0 findings (settings observability, pre-Android-11 delete flow) | Audit could not confirm whether these are still present on current `main` | Unknown severity until re-checked | **Not independently re-verified this session** — out of this session's task scope; still an open question from the original audit |
| Device/accessibility suite (WM-402) | Never run | Blocking (release gate) | **Not run this session** — see below |
| Findability study (WM-401) | Never run | Blocking (release gate) | **Not run this session** — see below |

---

## Device/accessibility suite (WM-402)

**Not run.** Per that task's own explicit prerequisite: it assumes "the app actually builds and
runs" and that the higher-priority code fixes have landed and are verified — "If those aren't done
yet, say so and stop rather than running the suite against an incomplete build." Neither condition
holds: the app has never been successfully built in this or any prior session, and this session's
own fixes (sleep timer, subtitle probe) are unbuilt/unverified. Running the suite against that
state would produce noise, not signal, exactly as that task warns against. No results file was
created, and no check was marked pass or fail.

## Findability study (WM-401)

**No real sessions run — none fabricated.** This session has no mechanism to recruit or moderate
real human participants, and none was offered. Per that task's own constraint, this is the single
most important one in the whole remediation plan: fabricated data would hide exactly the
usability risks this study exists to surface. Instead, the legitimate preparation work was done
and delivered as `docs/sprint-2-findability-study-logistics.md`:
- Confirmed (source-level) that every control label the protocol's moderator instructions
  reference still exists under the same name in current UI code — no protocol drift found.
- Documented two real readiness gaps: no verified build exists yet, and no prepared local-video
  test library exists in the repo (by design — video fixtures don't belong in source control;
  this needs to be assembled directly on test devices).
- Produced a full recruitment/consent/session-structure/recording-template checklist so a human
  can run this efficiently once a build exists.

No participant data, completion rates, or findings exist, and none should be treated as if they
do.

---

## What must happen before this can flip to Go

1. **Get a real, verified build.** Either grant this kind of session egress to
   `dl.google.com`/`jitpack.io`, or run the build/test/JitPack-resolution/MP3-encoder-smoke-test
   chain (remediation plan items 5-8) in an environment that has it (e.g. the project's own
   GitHub Actions CI, which already has that access per the runs observed on this session's PR).
2. **Retest this session's remediations against that real build**: sleep-timer end-of-folder
   behavior, the subtitle reachability check, and `VideoCompressor`'s encoder settings (already
   independently doc-verified, but never compiled).
3. **Add the missing unit test coverage** (`MediaJobManager`, `PlaybackControllerImpl`,
   `SubtitleRepositoryImpl`, migration edge cases) once a build exists to run `./gradlew test`
   against.
4. **Run WM-402** (device/accessibility suite) against that verified build and record real
   results.
5. **Run WM-401** (findability study) with real recruited participants, using the logistics
   checklist prepared this session, and record real results.
6. **Resolve the two still-open items outside this session's scope**: the premium/paywall
   placeholder decision, and re-verifying the two Sprint-1 P0 findings against current `main`.
7. Once all of the above have real, recorded evidence — re-run this triage (WM-407) with that
   evidence in hand, not this document's placeholder gaps.

## Recommendation

**HOLD.** Zero of WM-407's four prerequisites are currently satisfiable with real evidence in
this environment. This is not a judgment that the underlying work is bad — several real fixes
landed this session — but "production ready" cannot be honestly claimed while the build has never
compiled, no device/accessibility evidence exists, and no real findability study has been run.
