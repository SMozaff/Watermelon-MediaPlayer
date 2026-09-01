# Watermelon Auto Sync — Sandbox Validation

Implemented against verified `main` source structure for `SMozaff/Watermelon-MediaPlayer`.

## Implemented

- Common sync contracts and models.
- Subtitle timing fingerprinting.
- Weighted subtitle activity/onset signal generation.
- SDH/non-dialogue cue down-weighting.
- Distinctive sparse probe selection.
- Hierarchical constant-offset correlation.
- Multi-probe consensus and confidence.
- Linear-drift detection (detect-only; V1 does not flatten it into a constant offset).
- Manual > automatic persistence authority.
- Dedicated `SubtitleSyncProfiles` migration/repository.
- Sparse MediaExtractor/MediaCodec audio-window decoder.
- Streaming lightweight speech-likelihood estimator; no full audio buffer and no intermediate file.
- Single process-wide probe decoder gate.
- Phone Quick Tools manual ±100 ms and explicit Auto Sync action.
- TV manual ±100 ms and explicit Auto Sync action.
- Persistent Auto Sync settings for phone and TV settings surfaces.
- MainActivity orchestration with playback-session stale-result protection.

## Sandbox checks executed

### Pure synchronization core compilation

Compiled the new common/subtitle sync Kotlin sources with `kotlinc` and the installed coroutines runtime.

Result: PASS.

### Constant-offset recovery

Synthetic timing patterns:

- +2500 ms -> +2480 ms (40 ms bucket resolution)
- negative offset recovery -> PASS
- multiple ± offsets -> within one 40 ms bucket

### Multi-probe coordinator

Three independent probes for a synthetic +2500 ms mismatch:

- P1: +2480 ms
- P2: +2480 ms
- P3: +2520 ms
- persisted result: +2480 ms
- confidence: ~0.877

Result: PASS.

The second probe was retargeted using the provisional first-probe offset, confirming the adaptive-probe path.

### Manual precedence

A stored manual offset returns immediately and does not call the audio probe source.

Result: PASS.

### Complex drift

Monotonic offset progression is classified as `ComplexDriftDetected` rather than averaged into a false constant offset.

Result: PASS.

### Conflicting evidence

Conflicting probe offsets produce `LowConfidence` and no automatic correction.

Result: PASS.

### Speech-likelihood accumulator

Generated 8 seconds of PCM with alternating silence and speech-like harmonic bursts.

- silence remained low activity
- speech regions produced strong activity
- onset signal detected multiple transitions

Result: PASS.

### Correlation performance

On this sandbox CPU, a coarse ±180-second search at 200 ms resolution over an 8-second probe took only a few milliseconds. The dominant runtime cost remains Android audio decoding, as expected.

## Validation still requiring Android hardware / full repository

The uploaded source ZIP did not mount into the sandbox, so Gradle/Android instrumentation could not be executed against the complete source tree in this run.

The following require the complete repository plus Android runtime/device:

- AGP/Gradle compilation of all modules together.
- MediaCodec sparse-probe tests against real MP4/MKV/AAC/AC3/etc. files.
- API 23 device validation.
- low-end Android TV decoder contention / dropped-frame measurement.
- real movie + subtitle benchmark corpus calibration.

`apply_autosync_patch.py` applies the implementation to the verified main-branch source layout once the full source tree is available.
