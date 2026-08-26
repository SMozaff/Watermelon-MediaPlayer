# Sprint 2 Findability Study — Recruitment & Logistics Checklist

**Prepared:** 2026-08-26
**Companion to:** `sprint-2-findability-validation.md` (the study protocol itself — unchanged; this
document only covers the practical mechanics of running it).
**Status:** Preparation only. No sessions have been run and no results exist yet — see
"What this document is not" below.

---

## Protocol readiness check (done in this pass)

Before handing this to a moderator, the control labels the protocol's moderator instructions and
scorecard reference were cross-checked against the current `main` UI source, to catch drift
between when the protocol was written and the app's present state:

| Protocol reference | Current UI source | Status |
|---|---|---|
| "Player actions" sheet | `PlayerControlPanel.kt:41` `SheetTitle("Player actions")` | Matches |
| "Quick tools" | `PlayerControlPanel.kt:43,86` | Matches |
| "File actions" | `PlayerControlPanel.kt:48,233` | Matches |
| "Add to playlist" | `PlayerControlPanel.kt:240`, `MultiSelectionDock.kt:95`, `VideoListScreen.kt:499` | Matches |
| "Trim" (from File actions) | `PlayerControlPanel.kt:246` `SheetAction("Trim", ...)` | Matches |
| "Sort" (library) | `VideoListScreen.kt:245-246,391` — `WatermelonIcons.Sort` / "Sort: {current}" / "Sort videos" dialog | Matches |
| "Remove from this playlist" | `MultiSelectionDock.kt:110` | Matches |
| "Delete from device" | `MultiSelectionDock.kt:118`, `PlayerControlPanel.kt:258` | Matches |

No moderator-guide updates were needed — every control the protocol references still exists under
the same name in the current source. **This is a source-level check only** (the labels exist in the
Kotlin/Compose code); it is not confirmation that a running build actually renders them this way —
see the build-verification gap below.

## What's NOT yet confirmed (open gaps before this can run)

1. **No stable, buildable APK has been produced or verified in any session to date.** The
   project's Android build has never successfully compiled end-to-end (see the remediation
   plan's environment/dependency tasks) — most recently blocked in this session by
   organization-policy-denied access to `dl.google.com`/`jitpack.io`. **Do not schedule
   participant sessions until a real, running build exists and has been smoke-tested** against
   the five core scenarios by a team member first.
2. **No prepared local-video test library exists in this repository.** The protocol calls for
   "a prepared local-video library" sideloaded onto test devices before each session; no such
   asset set exists in this repo (and video fixtures don't belong in source control regardless —
   this needs to be assembled and sideloaded onto the actual test devices, not shipped as a
   repo file). Suggested minimum: 8-12 videos across at least two folders, mixed
   durations/resolutions/aspect ratios, so Sort/Trim/Compress/Add-to-playlist scenarios have
   real variety to act on.

## Participant recruitment

- **Target:** 5-7 participants (protocol minimum).
- **Required mix** (protocol's own requirement — recruit specifically for these, don't treat them
  as optional nice-to-haves):
  - At least 1 participant who uses **large display text** as their normal system setting.
  - At least 1 participant who navigates with **TalkBack** (or another screen reader) day-to-day.
  - At least 1 participant who normally uses an **RTL system locale** (Farsi or Arabic, matching
    this app's actual supported locales).
- **Device requirement:** each session needs a compact-width phone and a currently supported
  Android version, with the device's media-consent (photo/video permission) dialog already
  handled or ready to walk through as part of setup.
- **Recruitment channels:** not prescribed here — use whatever the team's standard participant
  panel/recruiting process is. If none exists yet, this is itself a blocker to flag to the
  release owner (WM-401's board entry names "participant recruitment" as a dependency, not an
  assumed-available resource).

## Consent process

- Each participant should be told, before the session starts: what's being observed (their
  ability to find and use specific controls, not their general opinion of the app), that the
  moderator will not coach them, that they can stop at any time, and how their data (recordings,
  notes) will be stored/used/retained/deleted.
- Get explicit recorded consent (verbal, on the recording, or a signed form — whichever matches
  the team's standard practice) before starting the first task.
- If screen recording and/or audio/video of the participant is captured, say so explicitly and
  get separate consent for that specifically, since it's more sensitive than task-completion notes
  alone.

## Session structure and length

- Suggested budget per participant: **45-60 minutes** total —
  - 5 min: welcome, consent, comfort check (especially for the TalkBack/large-text/RTL
    participants — confirm their assistive settings are active *before* starting, not mid-task).
  - ~30-35 min: the five core scenarios from the protocol's scorecard, in order, plus the
    accessibility/responsive checks relevant to that participant's setup (e.g. don't skip TalkBack
    traversal checks just because the TalkBack participant already demonstrated task completion —
    both data points matter).
  - 5-10 min: brief debrief — any moment they'd like to comment on, freeform.
- Between participants, budget 10-15 minutes for the moderator to write up notes while fresh,
  rather than batching write-ups to the end of the day.

## Recording / notes template

For each participant × scenario cell, record (matching the protocol's own "Evidence to record"
column plus enough context to be useful later):

| Field | What to capture |
|---|---|
| Participant ID | Anonymized (e.g. P1-P7) — never a real name in this log. |
| Participant profile | Which of the required-mix categories they represent, if any (large text / TalkBack / RTL locale / none). |
| Device + OS version | Exact model and Android version used for this session. |
| Scenario | Which of the five core scenarios. |
| Outcome | Completed unassisted / completed with hesitation / completed with moderator prompt (counts as a fail per protocol) / did not complete. |
| Elapsed time | Time from scenario start to completion or abandonment. |
| Observed behavior | What the participant actually did — wrong menu opened, hesitation point, verbal confusion, etc. Free text, be specific (this is the actionable part). |
| Severity (if a defect) | Blocking (violates the 80% completion bar, or is an accessibility/destructive-action defect per the protocol's decision-log rule) vs. non-blocking (minor friction, still completed). |

Aggregate at the end: per-scenario completion rate across all participants, checked against the
protocol's 80% unassisted-completion target.

## What this document is not

This is a **logistics checklist**, not a completed study. No participants have been recruited, no
sessions have been run, and no findings exist. Per the study protocol's own explicit constraint,
fabricating plausible-sounding results would actively defeat the purpose of this gate — do not
populate this document, or any results file, with invented data. The next real step is: get a
verified build (see the open gap above), assemble the test-video library, recruit the required
participant mix, and run real sessions using the template above.
