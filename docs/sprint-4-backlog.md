# Watermelon MediaPlayer — Sprint 4 Backlog

**Working name:** Evidence, Recovery, and Release Confidence
**Baseline:** `main` at [`078e650`](https://github.com/SMozaff/Watermelon-MediaPlayer/commit/078e650200ed1379937782cda6feb013c0b3d34e)
**Prepared from:** the original UI/UX audit, the Sprint 2 and Sprint 3 validation protocols, and the review confirming that neither manual validation gate has recorded results yet.[1] [2] [3] [4]

## Sprint objective

Sprint 4 should turn the implementation delivered in Sprints 1–3 into **demonstrated release confidence**. The immediate product risk is no longer an unbuilt control architecture; it is the absence of real-user and physical-device evidence showing that the new architecture is understandable, accessible, and reliable in context.

> **Sprint 4 outcome:** representative users can complete high-consequence tasks without coaching; physical Android devices confirm reliable background-job, large-text, TalkBack, RTL, and compact-layout behavior; and the product has a clear release decision supported by recorded evidence.

## Scope and priority model

| Priority | Intent | Stories |
|---|---|---|
| **P0 — acceptance evidence** | Close the open human-validation gates and prevent unresolved data-loss, accessibility, or core-task blockers from reaching release. | WM-401, WM-402, WM-407 |
| **P1 — confidence-building product work** | Address high-value recovery and findability gaps that remain after the earlier sprints, while generating automated regression evidence. | WM-403, WM-404, WM-405 |
| **P2 — trust and discoverability polish** | Simplify remaining settings language and remove implementation-facing configuration from production journeys. | WM-406 |

The sprint deliberately excludes TV redesign, cloud features, social sharing expansion, subscriptions, recommendation systems, and broad media-engine rewrites. Those efforts would dilute the validation-first objective without resolving the release risks currently known.

## User stories

### WM-401 — Run and record the real-user findability study

**Priority:** P0
**User story:** As a product team, we want to observe representative Android users complete the critical playback, library, media-tool, and deletion tasks without coaching so that we know whether Sprint 2’s control architecture is actually findable.

The study must recruit **five to seven representative Android users**, including where feasible one participant who uses large display text, one participant who uses TalkBack or another screen reader, and one participant who normally uses an RTL system locale. The moderator should not identify a control, icon, or destination during task execution.[2]

| Acceptance criterion | Required evidence |
|---|---|
| Each participant attempts Speed, Add to playlist, Trim, Sort, and Delete-cancel from the defined starting context. | Per-participant task log with completion, time, hesitation, wrong path, and moderator intervention. |
| Every core task reaches at least **80% unassisted completion** across the participant set. | Aggregate completion table by task and participant cohort. |
| The study records accessibility and responsive observations for TalkBack, large text, RTL, compact width, tool exits, and destructive-action wording. | Finding log with severity, reproduction, owner, decision, and retest requirement. |
| Any blocking discoverability, accessibility, or destructive-action defect becomes a committed remediation item before release acceptance. | Linked issue/story and updated acceptance decision. |

**Dependencies:** A stable build of `main`, prepared local-video library, moderator guide, participant consent process, and the Sprint 2 validation protocol.
**Definition of done:** The study evidence, aggregate results, raw findings, and a go/hold recommendation are committed or attached to the release record.

### WM-402 — Execute the physical-device accessibility and long-job suite

**Priority:** P0
**User story:** As a user who relies on Android accessibility settings or needs to leave media processing running, I want the application to remain understandable and recoverable on my actual device so that I can complete tasks with confidence.

This story executes the Sprint 3 release-readiness protocol rather than treating CI success as a proxy for interaction quality. It specifically covers a compression that runs for at least five minutes, backgrounding and returning, multiple active jobs, cancellation, completion and failure recovery, output opening, 320dp and 360dp layouts, font scale 1.3 or greater, TalkBack, forced RTL, and both visual themes.[3]

| Acceptance criterion | Required evidence |
|---|---|
| A long-running job remains visible and controllable after navigation, rotation, app background/return, and reopening its initiating tool. | Device log with job type, state transitions, screenshots or recordings, and observed result. |
| Multiple jobs, cancellation, terminal history, result opening, and the protected original-file decision behave correctly. | Pass/fail record per scenario, including system media-consent outcome where applicable. |
| Mini-player, player controls, browse controls, and playlist creation remain usable at compact widths and large text. | Device/width/font-scale matrix with any overlap, truncation, or target-size defect. |
| TalkBack and RTL preserve meaningful action names, state, traversal order, and destructive-action distinction. | Assistive-technology traversal notes and severity-ranked findings. |
| No unresolved P0 accessibility, data-loss, or background-job continuity defect remains. | Signed release-gate summary and retest links for every corrected blocker. |

**Dependencies:** WM-403 when its job-history behavior is included in the tested build; supported Android devices; local media fixtures; and the Sprint 3 release-readiness protocol.
**Definition of done:** A complete device matrix is recorded with device model, OS version, locale direction, font scale, theme, job type, result, defect severity, and owner disposition.

### WM-403 — Provide a persistent Jobs destination and recent-history recovery

**Priority:** P1
**User story:** As a user who starts a trim, compression, or audio extraction, I want a persistent place to inspect active and recent processing outcomes so that I can recover an output after the active job bar disappears or after I return to the app.

The current shared Jobs surface establishes useful active and in-memory recent states, but it is not a durable, always-reachable destination once no operation is active. Sprint 4 should consolidate active work and a bounded local terminal history into a visible **Jobs** entry point in a discoverable application location, without adding cloud storage or account requirements.

| Acceptance criterion | Required behavior |
|---|---|
| A user can open **Jobs** whether or not work is currently running. | Entry point is labelled, reachable from the normal library/navigation experience, and exposed to TalkBack. |
| Active jobs show operation, source, state, progress, and Cancel. | Updates are live and do not duplicate the same job after navigation or rotation. |
| Recent completed, failed, and cancelled jobs remain available after the active bar disappears and after a normal app restart. | Bounded local history retains type, source label, timestamp, terminal state, output reference, and safe action availability. |
| Terminal outcomes provide Open result, Review original where applicable, Change settings for recoverable failure, and Dismiss/Clear history. | Every action fails safely when an output is no longer present or no compatible handler exists. |
| Completion notifications open the relevant item in Jobs rather than a generic or empty surface. | Notification deep-link test passes for an active and terminal outcome. |

**Dependencies:** Existing `MediaJobManager`, Jobs sheet, Android notification/channel behavior, and WM-402 device verification.
**Definition of done:** Unit coverage exercises active-to-terminal transitions, persistence and eviction behavior, missing-output recovery, and notification routing; the physical-device suite confirms the full journey.

### WM-404 — Add library search, no-results clarity, and recovery

**Priority:** P1
**User story:** As a user with a large media library, I want to search my videos and understand when a query returns no matches so that I can find a file without repeatedly changing sort or view settings.

Search is the most contained next findability enhancement after Sort and View. It should complement the explicit loading, empty, content, and error model from Sprint 1 rather than recreating ambiguous blank states.

| Acceptance criterion | Required behavior |
|---|---|
| The Folder and Videos libraries expose a labelled search action. | Search is keyboard accessible, announced by TalkBack, and compatible with forced RTL. |
| A query matches filename/title and meaningful folder context without blocking indexing. | Results update predictably and preserve the current Sort and View selections. |
| A no-results state distinguishes “no matches for this search” from an empty library or indexing failure. | It shows the query, offers Clear search, and retains Refresh library or relevant recovery action where appropriate. |
| Clearing search restores the prior browse state and scroll context where feasible. | No hidden filter remains after navigation, rotation, or explicit clear. |
| Compact and large-text layouts preserve a visible exit from search. | Search controls do not displace or obscure the primary browse content. |

**Dependencies:** Existing `LibraryUiState`, `FolderViewModel`, `VideoListViewModel`, Sort/View controls, and WM-401 research findings.
**Definition of done:** View-model and Compose regression coverage pass, and at least the search/no-results/clear scenarios are included in the Sprint 4 manual study.

### WM-405 — Add behavioral and accessibility regression coverage for high-consequence journeys

**Priority:** P1
**User story:** As the delivery team, we want automated checks for the behaviors users trust most so that a later change cannot silently restore misleading settings, ambiguous destructive actions, or inaccessible control semantics.

This story converts lessons from the first three sprints into durable guardrails. It should test behavior, not merely compilation, and should focus on flows that previously carried P0 or high-confidence usability risk.[1]

| Acceptance criterion | Required coverage |
|---|---|
| Settings remain truthful. | Change a visible preference, recreate or restart the relevant surface, and assert the observable effect or absence of an exposed unsupported control. |
| Playlist and deletion semantics remain truthful. | Player Add to playlist opens/selects a destination; Favourites remains explicit; deletion cancellation keeps media and reports cancellation. |
| Library states remain distinct. | Indexing, content, true empty, no-search-results, and recoverable error use different accessible messages and actions. |
| Job outcomes remain recoverable. | Active, completed, failed, cancelled, missing output, and pending original-decision states expose the intended actions. |
| Key custom controls retain semantics. | Player actions, Quick tools, File actions, Sort, View, batch actions, mini-player, and Jobs are discoverable by test tags or semantic labels. |
| CI reports the new regression suite. | The main-branch workflow runs the relevant unit/UI tests and preserves artifacts on failure. |

**Dependencies:** WM-403, WM-404, existing GitHub Actions `build-judge` workflow, and the findings produced by WM-401/WM-402.
**Definition of done:** The regression suite runs on `main`, has named tests for each critical journey, and includes a triage rule for any flaky device-dependent coverage.

### WM-406 — Complete settings language and contextual guidance cleanup

**Priority:** P2
**User story:** As a user configuring the app, I want every production setting to use plain language and describe an immediate user-visible outcome so that I can make informed choices without seeing engineering terminology.

Sprint 1 corrected or hid the highest-risk non-functional controls, but the audit still identifies implementation-facing labels and generic setting groups as a trust risk. This story finishes the user-facing language pass and brings high-frequency settings closer to the feature they alter where that materially reduces search cost.[1]

| Acceptance criterion | Required behavior |
|---|---|
| All production settings are reviewed for plain language, accurate summary, and observable result. | An inventory records label, default, effect surface, and automated/manual verification. |
| Engineering-facing, placeholder, premium, tier, or power-user wording is removed from consumer-facing paths unless the capability is fully supported and explained. | Unsupported or internal controls are hidden from production builds. |
| High-frequency controls offer contextual entry points. | Seek mode, browse view preference, subtitle controls, and output settings can be reached from their relevant feature without duplicating state. |
| Deep links do not strand users in a generic Settings page. | The destination lands in or highlights the relevant group and preserves Back behavior. |
| Settings summary and state announcements work in light/dark themes, TalkBack, large text, and RTL. | Inclusion in the physical-device suite is documented. |

**Dependencies:** WM-401 findings, Settings persistence, and WM-405 coverage.
**Definition of done:** The settings inventory is complete, user-facing copy is approved, and every remaining production setting has an observed or automated proof of effect.

### WM-407 — Triage findings, retest fixes, and issue the release recommendation

**Priority:** P0
**User story:** As a release owner, I want every human-validation result to have a clear disposition and retest record so that the decision to release is based on evidence rather than a passing build alone.

This is the Sprint 4 closing story. It is intentionally evidence-led: a new P0 usability, accessibility, or data-loss failure takes precedence over lower-priority enhancement work and must be corrected and rerun before acceptance.

| Acceptance criterion | Required evidence |
|---|---|
| All WM-401 and WM-402 findings are logged with severity, reproduction, owner, target, and disposition. | Single consolidated decision log. |
| Every P0/P1 issue selected for remediation has a linked change, automated check where feasible, and manual retest. | Before/after evidence and retest result. |
| Open risks are explicit. | Deferred issue list states user impact, rationale, owner, and planned milestone. |
| The release recommendation is unambiguous. | **Go**, **Go with documented non-blockers**, or **Hold**, signed against the defined exit criteria. |
| `main` retains a passing full GitHub Actions verification after the final Sprint 4 change. | Link to the final main-branch run. |

**Dependencies:** WM-401 through WM-405; WM-406 where settings findings affect release confidence.
**Definition of done:** The team has a complete acceptance record and an explicit release decision; no implicit “probably ready” state remains.

## Recommended sequence

| Sequence | Workstream | Why it comes first |
|---:|---|---|
| 1 | WM-401 and WM-402 preparation and execution | These reveal whether the three completed implementation sprints solve the right user problems on real devices. |
| 2 | WM-403 and WM-404 implementation | Both strengthen recovery and findability, and can proceed in parallel with evidence gathering when findings do not require a course correction. |
| 3 | WM-405 automated guardrails | Encodes prior P0 fixes plus all stable remediation work before release. |
| 4 | WM-406 settings cleanup | Applies research findings to trust and discoverability without delaying P0 safety work. |
| 5 | WM-407 remediation, retest, and release decision | Converts test observations into a defensible acceptance outcome. |

## Sprint exit criteria

Sprint 4 is complete only when the following conditions are satisfied:

| Gate | Exit criterion |
|---|---|
| Real-user findability | Five to seven representative users have completed the study, every core task meets the 80% unassisted-completion target, or any shortfall has a remediated and retested decision. |
| Physical-device accessibility | The full device matrix is recorded, with no unresolved P0 accessibility, data-loss, job-continuity, or compact-layout defect. |
| Product recovery | Users can reach persistent active and recent Jobs, understand terminal outcomes, and recover a result or failure path. |
| Library finding | Search, no-results clarity, clear-search, and recovery paths are accessible and regression-tested. |
| Automated verification | Main-branch `build-judge` passes after the final Sprint 4 change. |
| Release decision | A documented Go, Go with non-blockers, or Hold recommendation is issued. |

## References

[1]: [Watermelon MediaPlayer UI/UX audit](watermelon-mediaplayer-ui-ux-audit.md)

[2]: [Sprint 2 findability and accessibility validation protocol](../watermelon-mediaplayer-sprint1/docs/sprint-2-findability-validation.md)

[3]: [Sprint 3 release-readiness protocol](../watermelon-mediaplayer-sprint1/docs/sprint-3-release-readiness.md)

[4]: [Review of missing Sprint 2 and Sprint 3 human-validation evidence](sprint-2-and-3-human-validation-review.md)
