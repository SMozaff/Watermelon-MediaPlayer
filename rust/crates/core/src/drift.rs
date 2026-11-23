use crate::contracts::DriftCue;

pub fn apply(cues: &[DriftCue], offset_ms: i64) -> Vec<DriftCue> {
    cues.iter()
        .map(|cue| DriftCue {
            start_ms: (cue.start_ms + offset_ms).max(0),
            end_ms: (cue.end_ms + offset_ms).max(0),
            text: cue.text.clone(),
        })
        .collect()
}

pub fn apply_to_timestamp(timestamp_ms: i64, offset_ms: i64) -> i64 {
    (timestamp_ms + offset_ms).max(0)
}
