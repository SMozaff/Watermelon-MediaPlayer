# Sprint 3 Release-Readiness Protocol

## Purpose

This protocol is the manual quality gate for Sprint 3. It verifies that media jobs remain visible but non-blocking, that terminal outcomes are recoverable, and that the revised controls remain usable with constrained space and assistive technology.

## Long-running media-job scenario

Start a compression expected to run for at least five minutes. Choose **Continue in background**, browse folders and videos, change a bottom-navigation tab, place the app in the background, return, and open **View jobs**.

| Check | Expected result |
|---|---|
| Persistent visibility | The job bar remains available outside the initiating tool, and the Jobs sheet identifies the operation, source name, state, progress, and Cancel action. |
| Multiple operations | Starting a second supported job changes the bar and notification to an explicit count or summary; no active operation is silently hidden. |
| Lifecycle resilience | Rotation, tab changes, app background/foreground transitions, and reopening the initiating tool show the same application-scoped job rather than creating a duplicate. |
| Cancellation | Cancelling a running job results in a clear cancelled recent state and safe staging-file cleanup. |
| Completion and recovery | Completed output is visible in recent Jobs, opens where device support permits, and trim/compress retain the protected original-file decision. Failed jobs name the operation, expose the reason, and provide Change settings or Dismiss. |

## Responsive and assistive-technology checks

| Surface | Test conditions | Expected result |
|---|---|---|
| Mini-player | 320dp, 360dp, normal width, font scale 1.3 or above | Compact mode retains the thumbnail, readable title, Play/Pause, and Close. Secondary controls move to the restored full player. There is no overlap, clipped hit target, or unusable title. |
| Player and library controls | TalkBack enabled, forced RTL, dark and light themes | Actions announce their name and state in a meaningful order. The player actions, Quick tools, File actions, Sort, View, and batch actions remain distinguishable without color alone. |
| Playlist empty state | No playlists, keyboard enabled | The benefit of playlists and Create playlist action are visible. The creation dialog focuses the name field, and first creation transitions into the playlist list immediately. |
| Primary actions | Dark and light themes | Watermelon Red primary surfaces use the semantic dark on-primary foreground, while selected and destructive states also retain label, placement, or structural cues. |

## Exit criteria

Sprint 3 is ready for product acceptance only when the long-running job scenario succeeds without an undisclosed blocking modal, critical actions remain usable with TalkBack and large text, and the responsive matrix has no overlap or inaccessible target defects. Record device, Android version, font scale, theme, locale direction, job type, and observed result for every test run.
