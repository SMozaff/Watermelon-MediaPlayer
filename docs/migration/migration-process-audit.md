# Migration process audit: Kotlin to Rust

**Audit date:** 2026-11-22
**Audited ref:** `main` at `46e64ff` (Stage 2 merge, PR #72)
**Scope:** the six-stage Kotlin-to-Rust migration process as recorded in `docs/migration/` —
planning, baseline discovery, golden-vector generation, gates G0 onward, and the transition
to Rust implementation. No production change is proposed by this audit.

## Verdict

The process is **disciplined in scope and reporting but not yet fit to authorize a Rust port**.
Stages 1 and 2 did what they said on production-code scope, and the reports honestly mark
failed/unverified items. However, the two artifacts that gate a migration — an executable
behavior oracle (golden vectors) and measured baseline performance — do not exist. D-1 through
D-6 are all blank. Stage 2 was nonetheless merged to `main` after its own PR body said it must
remain draft. **Stage 3 should not start until the blockers below are closed.**

## Status of stages and gates

| Stage / gate | Documented claim | Verified evidence | Verdict |
|---|---|---|---|
| Stage 1 — hygiene, discovery, benchmarks | Complete | PR #71 merged 2026-11-16; all 9 checks on its head commit green (verified via GitHub API). Harness added and default-off. | **Complete, with caveat:** benchmark harness never executed; no timings. |
| Stage 2 — golden vectors and test-gap closure | PARTIAL / NOT ACCEPTANCE-COMPLETE | PR #72 merged 2026-11-16 despite body stating "must remain draft". `testdata/golden/` absent; no generator, no self-check, no checksum proof. | **Not acceptance-complete.** |
| G0 — behavior/perf baseline | INCOMPLETE | `g0-report.md` records 0 cases and no timings; states evidence insufficient for a go/no-go. | **Not passed.** |
| Decision log | Owner decisions pending | `decision-log.md`: D-1…D-6 all blank. | **No authorization to continue.** |
| Rust implementation | Stage 3 not started | No `*.rs`, `Cargo.toml`, `Cargo.lock`, NDK/cargo Gradle config, JNI or UniFFI definitions in the tree. | **Not started.** |

## What is sound

- **Scope control.** Stages 1–2 changed only `.gitignore`, stale build artifacts, test sources
  and `docs/migration/`. No production Kotlin, Gradle dependency, build script, CI workflow,
  UI, playback or storage code was changed. Verified against the commit diffs.
- **Honest reporting.** Stage 2 marks A1, A2, A3, A4, A5 and C as FAIL/UNVERIFIED rather than
  claiming completion, and explicitly corrects an earlier A6 PASS to UNVERIFIED because an
  absent corpus cannot prove a 5 MB bound.
- **CI verification.** Both migration PRs are merged with `Debug build + unit tests`,
  `Benchmarks module compiles`, lint/interface gates and both emulator suites green. The
  "PR CI ran the tests" claim in the Stage 1 report is accurate.
- **Source inventory.** `kotlin-signatures.md` captures all twelve candidates with exact
  numeric types, preconditions, visibility, call sites, orchestration and direct-test gaps —
  an appropriate basis for parity work.
- **Defects surfaced, not silently fixed.** The Bidi comment/implementation mismatch and the
  OpenSubtitles 64 KiB vs 128 KiB discrepancy are recorded as suspected defects with current
  Kotlin behavior left unchanged.
- **Benchmark harness is opt-in** (`-Dwatermelon.benchmarks=true`) and skipped by default, so
  it adds no CI cost.

## Findings

### F-1 — High: incomplete Stage 2 merged to `main`

PR #72 states "This PR must remain draft until the generator, generated corpus, self-check,
checksum comparison and test execution are completed in a Gradle-capable checkout." GitHub
events show `ready_for_review` at 2026-11-16T20:23:21Z and `merged` at 20:23:29Z — eight
seconds later, with no reviews and no issue comments. The merge commit is the current `main`
head. The report's own acceptance table has six FAIL/UNVERIFIED rows.

**Impact:** the "Stage 2" label on `main` now implies an oracle that does not exist. A later
stage can be started on the assumption that baseline work is done.
**Recommendation:** keep the stage status in the report and decision log as the authority, not
the PR title; add an explicit owner note explaining why the partial merge was accepted, or
revert/re-land it as "Stage 2 partial" with a tracking issue for the remaining items.

### F-2 — High: no executable parity oracle

There is no `testdata/golden/` directory, no generator entry point calling the Kotlin
implementations, no Kotlin self-check, and no two-run byte-identical checksum evidence. The
report's A1, A2, A3, A4, A5 and C items are FAIL.

**Impact:** there is no pass/fail definition for "Rust matches Kotlin" for any of the twelve
candidates. This blocks D-1 and any meaningful Stage 3 verification.
**Recommendation:** add a generator that calls the current Kotlin implementations and emits
the ten JSON files with decimal strings plus raw IEEE-754 bits for floats, a self-check test
that loads all ten, and a CI `workflow_dispatch` job (the workflow already supports dispatch)
that runs generation twice and uploads checksums and the corpus as an artifact. This is the
practical answer to the repeated "no Gradle-capable checkout" blocker.

### F-3 — High: no baseline performance evidence

The opt-in harness was added but never run. No JVM nanosecond samples were recorded, and no
ART/device protocol exists (only a "proposed, not implemented" section). `g0-report.md`
correctly refuses to draw a performance conclusion.

**Impact:** if the migration is justified by speed, the go/no-go decision has no measured
input. JVM microbenchmarks are also explicitly not a proxy for Android ART.
**Recommendation:** run the harness once on a named, controlled host and commit raw samples
with the host/JVM metadata already present in `baseline-benchmarks.md`; add a separate
opt-in CI job or artifact upload so the numbers are reproducible. Define the on-device
protocol and threshold before porting, not after.

### F-4 — Medium-High: suspected defects are being frozen as parity behavior

Source verification confirms both reported defects:

- `BidiFormatter.format` line 52 says trailing neutrals are "trim[med] back out of the
  isolate", but the loop appends every neutral into the isolate and then appends PDI. The test
  `neutralPunctuationAndDigitsAttachAccordingToTheCurrentRunLoop` was *changed* by commit
  `d00a450` from `\u2068سلام!\u2069 123` to `\u2068سلام! \u2069` + `123` to match this output.
- `OpenSubtitlesHasher` requires 65,536 bytes and double-counts the same 64 KiB head/tail
  chunk at exactly that size; the published OSHash reference specifies 131,072 bytes.

**Impact:** golden vectors generated from the current Kotlin oracle will encode these bugs as
required Rust output. Correcting them later means regenerating the corpus and re-validating
every ported component.
**Recommendation:** decide explicitly whether parity means "current Kotlin, warts included"
or "correct behavior". If the former, annotate the affected vectors as known-defect parity and
open follow-up tickets; if the latter, fix in Kotlin in a separate behavior-change task
*before* freezing the oracle.

### F-5 — Medium-High: direct test gaps for eight of twelve candidates

`kotlin-signatures.md` §6 records no direct test for M-03 (probe selector), M-05 (time mapper),
M-06 (drift corrector), M-07 (config validation) and M-08 (contracts); M-01, M-02, M-04 and M-09
have partial coverage with boundary cases absent. Stage 2 added tests only for M-10, M-11 and
M-12, and those tests are unexecuted locally.

**Impact:** for most candidates, Kotlin parity will rest on generated vectors alone, with no
independently reasoned expected values to catch a generator bug.
**Recommendation:** add targeted boundary tests for M-03/M-05/M-06/M-07 before generating
vectors, especially `isRtl` tie/threshold behavior, `roundToLong` boundaries, Long overflow in
`LinearDriftCorrector`, and `SubtitleSyncConfig` `require` boundaries.

### F-6 — Medium: critical binding and numeric decisions are unapproved

D-2 (UniFFI vs JNI), D-3 (1e-6 vs bit-exact), D-4 (shadow logging), D-5 (MP3 encoder) and D-6
(owner/reviewer) are blank, alongside D-1. `decision-log.md` states blank choices must not be
inferred. No Rust toolchain, Cargo workspace, NDK config or FFI prototype exists.

**Impact:** the riskiest technical assumptions — FFI toolchain on Android, Float/Double parity
across JVM and Rust/libm, and cutover observability — are untested after two discovery stages.
**Recommendation:** time-box a Stage 3 spike that ports one small function end-to-end with the
chosen binding, runs on an emulator, and compares raw bits and decimal values against Kotlin.
Decide D-2 and D-3 from spike evidence, not from the proposed defaults in the decision log.

### F-7 — Medium: no shadow mode, cutover or rollback plan

`kotlin-signatures.md` §3 notes the app composition root (`AppDependencies.kt`) has no feature
flag or shadow-mode construction; `SettingsPersistence.kt` has no implementation selector.
D-4 is blank.

**Impact:** first cutover would be big-bang with no side-by-side Kotlin/Rust comparison on real
device data, and no documented rollback trigger.
**Recommendation:** define a local-only shadow mode (Kotlin result vs Rust result logged,
opt-in, no telemetry) and rollback criteria before the first port; record the owner decision
in D-4.

### F-8 — Medium: environment-limitation narrative is stale, and a viable path was overlooked

All three reports attribute non-execution to "no repository checkout / no Gradle wrapper /
cannot resolve host github.com". In this workspace the checkout and Gradle 9.7.1 wrapper are
present and `./gradlew --version` succeeds; `curl` to `api.github.com` returns HTTP 200; the
real local blocker is plugin resolution from Google/Maven repositories. The Stage 2 report also
says no workflow-dispatch action was exposed, but `.github/workflows/CI.yaml` declares
`workflow_dispatch` and PR/branch protection allowed both PRs to run full CI.

**Impact:** absolute statements about impossibility are session-specific and can mislead a
future reader into believing the oracle can never be generated. The dispatch path that would
unblock Stage 2 was not pursued.
**Recommendation:** rewrite limitations as dated, session-specific facts; use CI dispatch or a
developer checkout to run generation, and cite the resulting run URL as evidence.

### F-9 — Low-Medium: post-hoc expectation changes without recorded rationale

Commits `d00a450` (bidi expectation) and `de5cba2` (parser fixture `hello` → `abc`) changed
test expectations after the initial characterization commits. The parser change is explained
by `isRtl`'s strict `rtl > ltr` rule (4 RTL vs 5 LTR makes `"سلام hello"` LTR; `"سلام abc"` is
4 vs 3 and RTL), but neither commit nor report records this. The tie/treshold rule itself is
not directly pinned by a test.

**Impact:** a Rust port must reproduce this heuristic exactly, and the reason for the fixture
change is lost to future maintainers.
**Recommendation:** add an explicit `isRtl` boundary test (equal counts, one-character
difference) and note in the characterization test why the fixture uses `abc`.

### F-10 — Low-Medium: no single source of truth for the stage plan; interface guard does not cover sync

The six-stage plan and gate definitions exist only implicit in stage reports; there is no
`docs/migration/PLAN.md` defining stages, exit criteria, decision owners and gate order.
`scripts/verify_interfaces.py` compares filename-derived names in `repository` and
`controller` directories only; it does not scan `common-interfaces/.../subtitle/sync` or
`media-tools`, and it does not parse declarations.

**Impact:** reviewers cannot independently tell what "Stage 2 done" means, and the sync
contracts that the Rust seams will touch are not guarded when adapters are renamed or removed.
**Recommendation:** add a concise stage/gate matrix to `docs/migration/PLAN.md`; decide the
seam strategy (facade `*Impl.kt` delegating to Rust vs direct calls) and extend the interface
check accordingly before Stage 3.

## Required before Stage 3

1. Owner decisions D-1 through D-4 recorded with date and name (D-5/D-6 as applicable).
2. Ten golden JSON files, generator, self-check, and two-run checksum evidence committed; G0
   report updated from INCOMPLETE.
3. At least one actual benchmark run with raw samples recorded, plus an agreed device protocol.
4. Explicit disposition of the Bidi and OSHash suspected defects.
5. FFI/binding spike result, including float-bit comparison and PCM copy overhead at the
   `SpeechLikelihoodAccumulator` boundary.
6. Shadow-mode and rollback design approved.

Until these are in place, "Stage 3" would proceed without a correctness oracle, a performance
baseline, an approved binding decision, or a cutover safety net.

## Post-audit actions taken (2026-11-23)

Owner input: keep D-1…D-6 blank, fix defects in Kotlin before freezing the oracle, and run all
execution through GitHub Actions rather than the agent workspace.

| Recommendation | Action | Status |
|---|---|---|
| F-1 stage labeling | `PLAN.md` defines stage/gate order and states that a merged PR is not stage completion; owner note still needed on PR #72 | Partial |
| F-2 parity oracle | Generator, self-check and workflow added; run `45074382455` generated 10 files (111,890 bytes), proved two byte-identical runs and passed self-check; corpus committed under `testdata/golden/` | Executed; owner schema confirmation still open |
| F-3 benchmark evidence | No run possible locally; the existing opt-in harness remains the mechanism. Still outstanding | Open |
| F-4 suspected defects | Both fixed in Kotlin with tests; see [defect-fixes.md](defect-fixes.md) | Done |
| F-5 test gaps | Direct boundary tests added for probe selector, time mapper, drift corrector, config and `isRtl` | Done |
| F-6 decisions | Proposals written in [proposed-decisions.md](proposed-decisions.md); `decision-log.md` untouched | Awaiting owner |
| F-7 shadow mode | Design proposal only (D-4); no code | Awaiting owner |
| F-8 stale limitation wording | `PLAN.md` and this report record session-specific limitations; historical reports left as historical | Done |
| F-9 post-hoc fixtures | `isRtl` boundary test added and the `abc` fixture rationale documented | Done |
| F-10 plan and interface guard | `PLAN.md` added; interface-script seam change still depends on the D-2 decision | Partial |

**Execution note:** no local tests were run and no toolchains were installed. Local Gradle cannot
resolve AGP 9.4.0 from this network (Google Maven returns 404). All execution evidence comes from
GitHub Actions on branch `migration/golden-oracle-scaffolding`: oracle run `45074382455` passed;
full CI run `45074017608` found one stale Bidi test expectation, fixed before the final run.
The initial `GH_TOKEN` was invalid; a working credential became available later and pushed the
work over SSH (the OAuth token lacks `workflow` scope).
