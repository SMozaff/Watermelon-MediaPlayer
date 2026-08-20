# Sprint 2 Findability and Accessibility Validation Protocol

## Purpose

This protocol is the manual validation gate for the Sprint 2 control-architecture work. It evaluates whether the new player, browse, tool-exit, and multi-selection controls can be found and used without moderator coaching before the team accepts Sprint 2 and starts Sprint 3.

## Participants and setup

Recruit **five to seven representative Android users**. Include at least one user who uses large display text, one user who navigates with TalkBack or another screen reader, and one user who normally uses an RTL system locale where available. Test on a compact phone width and on a current Android version with device-media consent enabled.

The moderator should start each scenario on a prepared local-video library. Participants may think aloud, but the moderator must not tell them where a control is or which icon to select.

## Core scenario scorecard

| Scenario | Starting point | Successful outcome | Evidence to record |
|---|---|---|---|
| Change playback speed | Full player | Participant opens **Player actions**, finds **Quick tools**, and changes speed. | Completion, elapsed time, hesitation, wrong controls opened. |
| Add a playing video to a playlist | Full player | Participant opens **File actions**, chooses **Add to playlist**, and selects or creates a destination. | Completion and whether Favourites was confused with playlists. |
| Start a trim | Full player | Participant opens **File actions**, chooses **Trim**, selects a range, and starts the job. | Completion, whether task context and Back behavior were understood. |
| Change library sorting | Folders or Videos | Participant finds **Sort**, changes field or order, and recognizes the active choice. | Completion, scroll position, and comprehension of ascending/descending. |
| Cancel device deletion | Full player or multi-select | Participant finds **Delete from device**, reaches confirmation, then safely cancels. | Completion and any confusion with playlist removal. |

A scenario succeeds only when the participant finishes it without moderator instruction. The Sprint 2 target is at least **80% unassisted completion** for every core task across the participant set.

## Accessibility and responsive checks

| Check | Expected result |
|---|---|
| TalkBack traversal | Player actions, Quick tools, File actions, Sort, View, and batch actions announce a meaningful name and state in task order. |
| Large text and compact width | Two top-level browse controls remain visible; selection actions wrap vertically without overlap, truncation that changes meaning, or accidental destructive activation. |
| RTL | Back navigation, player title/context, action order, and tool headers follow layout direction without mirroring semantic meaning. |
| Task exits | Trim and Compress show Back; unsaved adjustments request discard confirmation; a running job requires an explicit confirmation before returning. |
| Destructive distinction | **Remove from this playlist** is visually and verbally distinct from **Delete from device**. |

## Decision log

For every hesitation, failure, or accessibility defect, record the participant context, task, intended action, observed behavior, severity, and owner decision. Blocking discoverability, accessibility, or destructive-action defects must be corrected before Sprint 3 is accepted.
