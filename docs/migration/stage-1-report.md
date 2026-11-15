# Stage 1 report: hygiene, discovery and baseline benchmarks

**Repository:** `SMozaff/Watermelon-MediaPlayer`  
**Base:** `main` at `da0bfe6b9d842b2a487613ac51eb25508b164653`  
**Branch:** `migration/stage-1-hygiene-discovery-baseline-benchmarks`  
**Stage:** 1 of 6  
**Status:** Code/documentation work committed; build and benchmark execution UNVERIFIED in this workspace. No Stage 2 work started.

## Summary

Completed the repository hygiene edits on the Stage 1 branch, removed 16 tracked stale build-output files, read and documented the twelve candidate Kotlin files and associated contracts/supporting code, documented the actual interface-check script behavior and direct-test gaps, added opt-in JVM benchmark tests without adding dependencies, and added a characterization test for a suspected Bidi comment/implementation mismatch.

No production Kotlin logic, Gradle dependency versions, build scripts, CI workflows, app files, UI, playback or storage logic were changed. The local wrapper command could not run because the workspace has no repository checkout and a clone attempt failed on DNS resolution for github.com. Subsequent pull-request CI successfully executed the relevant debug build/unit-test tasks, lint, migration-ladder emulator tests and player UI emulator tests. The benchmark harness itself remained default-off in CI, so no raw benchmark timings are claimed.

The uploaded Stage 1 brief did not specify a literal branch name. A dedicated branch was created from the audited `main` commit using the Stage 1 title as its name.

## What changed (files)

### Changed

- `.gitignore`: split the fused final rule into `*-stale-root/` and `.jan/agent/threads/`, preserving the preceding comment.

### Removed from the branch tree

The repository tree contained 16 tracked stale-root files, rather than the approximate 21 mentioned in the brief:

- `.gradle-stale-root/9.7.1/checksums/checksums.lock`
- `.gradle-stale-root/9.7.1/checksums/md5-checksums.bin`
- `.gradle-stale-root/9.7.1/checksums/sha1-checksums.bin`
- `.gradle-stale-root/9.7.1/executionHistory/executionHistory.bin`
- `.gradle-stale-root/9.7.1/executionHistory/executionHistory.lock`
- `.gradle-stale-root/9.7.1/fileChanges/last-build.bin`
- `.gradle-stale-root/9.7.1/fileHashes/fileHashes.bin`
- `.gradle-stale-root/9.7.1/fileHashes/fileHashes.lock`
- `.gradle-stale-root/9.7.1/fileHashes/resourceHashesCache.bin`
- `.gradle-stale-root/9.7.1/gc.properties`
- `.gradle-stale-root/9.7.1/kotlin-dsl-plugin-entries/kotlin-dsl-plugin-entries.lock`
- `.gradle-stale-root/buildOutputCleanup/buildOutputCleanup.lock`
- `.gradle-stale-root/buildOutputCleanup/cache.properties`
- `.gradle-stale-root/buildOutputCleanup/outputFiles.bin`
- `.gradle-stale-root/vcs-1/gc.properties`
- `build-stale-root/reports/problems/problems-report.html`

The local workspace did not contain a clone, so the equivalent Git tree deletions were committed through GitHub's Git Data API. The literal local `git rm -r --cached` command was therefore not executed. The branch tree removes those paths from tracking.

### Added

- `subtitle-engine/src/test/kotlin/com/watermelon/subtitle/bench/SubtitleEngineBaselineBenchmarkTest.kt`: opt-in correlator, consensus and parser workload benchmarks.
- `media-tools/src/test/kotlin/com/watermelon/mediatools/subtitle/sync/SpeechLikelihoodAccumulatorBenchmarkTest.kt`: opt-in PCM16 accumulator consume/finish benchmark.
- `subtitle-engine/src/test/kotlin/com/watermelon/subtitle/bidi/BidiFormatterTest.kt`: pins observed trailing-neutral whitespace behavior without changing production code.
- `docs/migration/kotlin-signatures.md`: source-derived signatures, defaults, numeric types, visibility/call sites, orchestration and test coverage audit.
- `docs/migration/baseline-benchmarks.md`: workload sizing, host details, run instructions and execution limitations.

No Gradle dependency or build-script changes were needed. Existing JUnit test dependencies were sufficient.

## Commands run with results

### Repository and hygiene audit

- Read the repository tree and source files at `main` SHA `da0bfe6b9d842b2a487613ac51eb25508b164653` using GitHub repository APIs.
- Searched the checked tracked text files in workflow, Markdown documentation, script, Gradle and root build/config locations for `.gradle-stale-root`, `build-stale-root` and `stale-root`. No matching path references were found outside the generic `.gitignore` rule.
- GitHub code search for the same stale-root terms returned no source matches.
- The documentation tree also contains a binary DOCX and a PNG image; those binary assets were not text-extracted for the stale-root reference scan. The no-reference conclusion is therefore limited to the checked text files and GitHub code-search results.
- The tree contained 16 tracked files beneath the two stale-root directories; all 16 were removed from the branch tree.
- The original broken `.gitignore` rule was replaced with the two specified rules.

The corrected rules were checked in an isolated temporary Git repository with sample paths. The resulting `git check-ignore -v` output was:

```text
.gitignore:1:*-stale-root/    .gradle-stale-root/sample/probe.bin
.gitignore:1:*-stale-root/    build-stale-root/reports/problems-report.html
.gitignore:2:.jan/agent/threads/    .jan/agent/threads/thread.json
CHECK_IGNORE_EXIT_CODE=0
```

This verifies the two rules' matching behavior; it is not a run inside a local checkout of this repository.

### Requested build/test command

Attempted exactly:

```text
./gradlew :subtitle-engine:testDebugUnitTest :media-tools:testDebugUnitTest :common-interfaces:test --stacktrace
bash: line 3: ./gradlew: No such file or directory
EXIT_CODE=127
```

Reason: no repository checkout was available under the execution workspace, so the wrapper was not present. A prior attempt to clone the repository also failed:

```text
git clone --filter=blob:none --no-checkout https://github.com/SMozaff/Watermelon-MediaPlayer.git /tmp/watermelon-repo
fatal: unable to access 'https://github.com/SMozaff/Watermelon-MediaPlayer.git/': Could not resolve host: github.com
```

`gradle --version` also returned `bash: gradle: command not found`. The host was Linux x86_64 with OpenJDK 21.0.12.1; it is not the JDK 17 / Android SDK CI environment configured by the repository workflow.

### Benchmark command

Not run. The harness is present and opt-in, but compile/runtime verification depends on Gradle and the repository checkout that were unavailable. See [baseline-benchmarks.md](baseline-benchmarks.md). No raw numbers are reported because no benchmark actually ran.

### Git operations

The branch was created from `main`; changes were committed without force-push or writes to `main`:

1. `8326861abf86e4f6bbae6e2bfcc8c3cabdb74d27` — split stale-root ignore rules.
2. `10f991320bb44a2628cb36ab0650d38fdf2ea5f9` — remove 16 tracked stale build-output files.
3. `d7cdb3e7da9787820317e9d706f0771c098d4274` — add benchmark tests and source audit documents.

## Acceptance checklist

| Acceptance item | Result | Evidence / limitation |
|---|---|---|
| A1: split `.gitignore` rules | **PASS** | Both rules are present on the branch; isolated `git check-ignore -v` matched each sample path. |
| A2: remove stale-root outputs from tracking | **PASS** | All 16 tracked blobs found beneath the two paths were removed from the branch tree. |
| A2: no references to stale-root paths | **UNVERIFIED** | No matches in checked text files or GitHub code search; binary DOCX/PNG assets were not text-extracted. |
| B: complete source/signature and numeric type inventory | **PASS** | Documented in `docs/migration/kotlin-signatures.md` from source files read at the audited main SHA. |
| B: singleton call sites and internal visibility | **PASS** | Object call sites and media-tools internal consumers are listed in the signatures document. |
| B: interface script naming rule and allow-list | **PASS** | Script source read; filename-derived matching rule documented. No allow-list exists. |
| B: test coverage and direct gaps | **PASS** | All three named test files read and their current direct/indirect coverage documented. |
| C1: opt-in benchmark harness, no extra dependency | **PASS** | Two JUnit benchmark test classes added; both use a default-off system-property assumption and 5 warm-ups / 20 timed iterations. |
| C1: harness compiles and is skipped by default in CI | **PASS** | Both owning module unit-test tasks completed successfully in PR CI; the tests are guarded by a default-off JUnit assumption. |
| C2: baseline measurements recorded | **UNVERIFIED** | No execution environment with the wrapper/Gradle was available; no raw values are invented. |
| C2: ART-on-device caveat and device timing proposal | **PASS** | Included in `docs/migration/baseline-benchmarks.md`. |
| D: requested module test tasks pass | **PASS** | PR CI ran `:common-interfaces:test`, `:subtitle-engine:testDebugUnitTest` and `:media-tools:testDebugUnitTest` in its expanded debug unit-test task set; local wrapper invocation remains unavailable. |
| No production behavior change | **PASS** | The branch additions/removals are limited to `.gitignore`, tracked stale build artifacts, test sources and migration documentation. No `src/main` file, app source, Gradle dependency, build script or CI workflow was changed. |

## Suspected defects

1. **BidiFormatter trailing-neutral handling:** `BidiFormatter.format` contains an inline comment stating trailing neutral characters are trimmed back out of an isolate. The actual loop consumes neutral characters into the opposite-direction run, including trailing whitespace, and then appends PDI. The new test `trailingNeutralWhitespaceRemainsInsideOppositeDirectionIsolate` pins the observed output. Production behavior is unchanged. This remains a suspected comment/implementation mismatch for owner review, not a silently applied correction.

No other production defect was changed or inferred during this stage.

## Open questions for the owner

1. The text reference audit found no stale-root references, but the binary `Docs/Subtitle_System_Blueprint.docx` and `Docs/WatermelonPlayer UI.png` were not text-extracted. Should those assets be inspected before treating the stale-root reference scan as fully exhaustive?
2. Raw benchmark measurements remain pending a deliberate opt-in run from a Gradle-capable checkout. CI compiled the harness but left it default-off. The numbers must be reviewed on a consistent machine before using them as a Gate G0 decision.
3. Should the suspected Bidi mismatch be corrected in a separate behavior-change task, or should the current behavior remain as-is for the migration parity baseline?
4. The existing `verify_interfaces.py` does not scan the subtitle-sync contracts directory. Should its scope be expanded in a later, separately authorized task? It was not modified in Stage 1.
5. The direct test gaps listed in `kotlin-signatures.md` remain open; in particular parser/Bidi/hasher golden cases and the `SubtitleProbeSelectorImpl` / time-mapper tests should be considered before Rust parity work begins.

**Stage boundary:** Stage 1 only. No Rust code, native bindings, CI workflow changes or Phase 1 implementation has been started.
