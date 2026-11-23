use serde_json::{json, Value};

use crate::contracts::{
    ActivitySignature, ParsedSubtitle, SubtitleCue, SyncMeasurement, SyncModel,
};

pub fn f32_value(value: f32) -> Value {
    json!({
        "decimal": value.to_string(),
        "bits": format!("{:08x}", value.to_bits()),
    })
}

pub fn f64_value(value: f64) -> Value {
    json!({
        "decimal": value.to_string(),
        "bits": format!("{:016x}", value.to_bits()),
    })
}

pub fn signature_value(signature: &ActivitySignature) -> Value {
    json!({
        "startMs": signature.start_ms,
        "bucketMs": signature.bucket_ms,
        "occupancy": signature.occupancy.iter().map(|v| f32_value(*v)).collect::<Vec<_>>(),
        "onsets": signature.onsets.iter().map(|v| f32_value(*v)).collect::<Vec<_>>(),
    })
}

pub fn cue_value(cue: &SubtitleCue) -> Value {
    json!({
        "index": cue.index,
        "startMs": cue.start_ms,
        "endMs": cue.end_ms,
        "rawText": cue.raw_text.clone(),
        "baseRtl": cue.base_rtl,
        "displayText": cue.display_text.clone(),
    })
}

pub fn subtitle_value(subtitle: &ParsedSubtitle) -> Value {
    json!({
        "language": subtitle.language.clone(),
        "sourceId": subtitle.source_id.clone(),
        "offsetMs": subtitle.offset_ms,
        "cues": subtitle.cues.iter().map(cue_value).collect::<Vec<_>>(),
    })
}

pub fn measurement_value(measurement: &SyncMeasurement) -> Value {
    json!({
        "subtitleProbeStartMs": measurement.subtitle_probe_start_ms,
        "audioProbeStartMs": measurement.audio_probe_start_ms,
        "estimatedOffsetMs": measurement.estimated_offset_ms,
        "correlationScore": f32_value(measurement.correlation_score.value()),
        "peakMargin": f32_value(measurement.peak_margin.value()),
        "speechCoverage": f32_value(measurement.speech_coverage.value()),
        "transitionCount": measurement.transition_count,
        "probeQuality": f32_value(measurement.probe_quality.value()),
    })
}

pub fn model_value(model: &SyncModel) -> Value {
    match model {
        SyncModel::Identity => json!({"kind": "Identity"}),
        SyncModel::Offset { offset_ms } => json!({"kind": "Offset", "offsetMs": offset_ms}),
        SyncModel::Affine { scale, offset_ms } => json!({
            "kind": "Affine",
            "scale": f64_value(scale.value()),
            "offsetMs": offset_ms,
        }),
        SyncModel::Piecewise { anchors } => json!({
            "kind": "Piecewise",
            "anchors": anchors.iter().map(|a| json!({
                "subtitleTimeMs": a.subtitle_time_ms,
                "mediaTimeMs": a.media_time_ms,
            })).collect::<Vec<_>>(),
        }),
    }
}
