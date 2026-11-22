# Kotlin-to-Rust migration plan (working definition)

**Status:** Working document, not owner-approved. The migration brief that defined "six stages"
was not committed to this repository; the stage and gate names below are reconstructed from the
stage reports and must be confirmed by the owner before they are treated as authoritative.

This file is the single place to check stage order, exit criteria and decision owners. Individual
reports remain the evidence of what actually ran.

## Stage and gate matrix

| Stage | Purpose | Exit criteria | Gate / decision | Current state |
|---|---|---|---|---|
| 1 — Hygiene, discovery, baseline harness | Remove stale outputs; inventory the twelve candidates; add opt-in JVM benchmarks | Signature inventory complete; harness compiles; build/tests green in CI | None (entry) | **Done.** PR #71 merged, CI green. JVM baselines recorded in CI run `45075503837` on a named `ubuntu-latest` runner; no ART/device baseline. |
| 2 — Golden vectors and test-gap closure | Generate the Kotlin behavior oracle; close direct-test gaps | Generator + 10 JSON files + Kotlin self-check + two-run checksum proof; corpus < 5 MB; target tests pass | **G0 / D-1** continue or stop | **Execution complete, owner gate open.** PR #72 merged as PARTIAL; the post-audit branch closed the generator, 39 cases in 10 files, self-check and byte-identical two-run proof (CI run `45074382455`), and full CI is green (`45074866004`). Owner schema confirmation and official OSHash media vector remain open; D-1 is still blank. |
| 3 — Rust core + binding spike | Decide binding tool and tolerance; port one component end-to-end; measure FFI/copy cost | D-2 and D-3 approved; one function runs on device with bit/decimal comparison; NDK toolchain in CI | **G1** (proposed) | Not started. No Rust/Cargo/NDK files. |
| 4 — Remaining ports + shadow mode | Port remaining candidates behind approved seams; run Kotlin and Rust side by side locally | All ten vectors pass against Rust; D-4 logging policy approved; no user-visible behavior change | **G2** (proposed) | Not started. |
| 5 — Cutover | Switch runtime calls to Rust behind a flag; validate on device | Device parity on representative media; rollback criteria met; D-5/D-6 approved | **G3** (proposed) | Not started. |
| 6 — Cleanup | Remove superseded Kotlin implementations and migration scaffolding | No orphaned interfaces; `verify_interfaces.py` updated; docs archived | Done criteria (proposed) | Not started. |

## Rules that apply at every stage

1. `docs/migration/decision-log.md` accepts **owner decisions only**. A blank cell is not a choice
   and must not be inferred from a proposal, a report, or code.
2. A stage is complete only when its own acceptance table is complete. A merged PR is not proof
   of stage completion; check the report and the artifacts it names.
3. Production behavior changes are separate, explicitly approved tasks. The oracle is generated
   from the code as it exists at generation time, so behavior fixes must land **before** the
   vectors are frozen.
4. All execution evidence comes from CI or a named checkout. Reports must state the session,
   date and exact command; "it cannot be done" is never a valid substitute for a run attempt.

## Decision ownership

| Decision | Owner | Required by | State |
|---|---|---|---|
| D-1 Continue past G0 | Repository owner | Start of Stage 3 | Blank |
| D-2 Binding tool | Repository owner | Stage 3 spike | Blank |
| D-3 Float tolerance | Repository owner | Stage 3 spike | Blank |
| D-4 Shadow-mode logging | Repository owner | Stage 4 | Blank |
| D-5 MP3 encoder | Repository owner | Stage 5 | Blank |
| D-6 Migration owner and reviewer | Repository owner | Before Stage 3 | Blank |

Recommendations for each blank decision are collected in
[proposed-decisions.md](proposed-decisions.md). That file is input for the owner; it is not a
decision record.

## Oracle contract (to be confirmed)

Ten component files under `testdata/golden/`:

`srt_parse.json`, `bidi_format.json`, `hash_opensubtitles.json`, `time_mapper.json`,
`drift_corrector.json`, `correlator.json`, `consensus.json`, `probe_select.json`,
`activity_builder.json`, `speech_accumulator.json`.

Generation is implemented by the generator tests in `subtitle-engine` and `media-tools` and is
driven by `.github/workflows/migration-golden-vectors.yml` (`workflow_dispatch`). The workflow
proves two runs are byte-identical, checks the 10-file corpus is under 5 MB, runs the structural
self-check, and uploads the corpus as an artifact. Run `45074382455` passed all of these and the
verified corpus is committed under `testdata/golden/` with `SHA256SUMS` and provenance notes. The
workflow does not write to the repository; regenerate and recommit when behavior changes.

Each file contains a deterministic, sorted list of cases. Every floating-point result is
recorded twice: as a decimal string and as raw IEEE-754 bits (`f32`/`f64`) so that a tolerance
decision (D-3) can be applied later without regenerating the corpus. Generation is executed by
`workflow_dispatch` in GitHub Actions and must be byte-identical across two consecutive runs.

## Related documents

- [Stage 1 report](stage-1-report.md) · [Stage 2 report](stage-2-report.md)
- [G0 report](g0-report.md) · [Decision log](decision-log.md)
- [Kotlin signature inventory](kotlin-signatures.md) · [Baseline benchmarks](baseline-benchmarks.md)
- [Migration process audit](migration-process-audit.md)
