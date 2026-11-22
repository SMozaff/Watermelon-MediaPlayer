use serde::{Deserialize, Serialize};

/// Golden-vector float encoding: the Kotlin oracle stores both a shortest decimal and raw bits.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Float32 {
    pub decimal: String,
    pub bits: String,
}

impl Float32 {
    pub fn value(&self) -> f32 {
        if let Ok(value) = self.decimal.parse::<f32>() {
            return value;
        }
        let bits = u32::from_str_radix(&self.bits, 16).unwrap_or(0);
        f32::from_bits(bits)
    }
}

/// Golden-vector f64 encoding used by affine sync models.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Float64 {
    pub decimal: String,
    pub bits: String,
}

impl Float64 {
    pub fn value(&self) -> f64 {
        if let Ok(value) = self.decimal.parse::<f64>() {
            return value;
        }
        let bits = u64::from_str_radix(&self.bits, 16).unwrap_or(0);
        f64::from_bits(bits)
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ActivitySignature {
    pub start_ms: i64,
    pub bucket_ms: i32,
    pub occupancy: Vec<f32>,
    pub onsets: Vec<f32>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SubtitleCue {
    pub index: i32,
    pub start_ms: i64,
    pub end_ms: i64,
    pub raw_text: String,
    pub base_rtl: bool,
    pub display_text: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ParsedSubtitle {
    #[serde(default)]
    pub cues: Vec<SubtitleCue>,
    #[serde(default)]
    pub language: Option<String>,
    #[serde(default = "default_source_id")]
    pub source_id: String,
    #[serde(default)]
    pub offset_ms: i64,
}

fn default_source_id() -> String {
    "local".to_owned()
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncAnchor {
    pub subtitle_time_ms: i64,
    pub media_time_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "kind")]
pub enum SyncModel {
    Identity,
    Offset {
        #[serde(rename = "offsetMs")]
        offset_ms: i64,
    },
    Affine {
        scale: Float64,
        #[serde(rename = "offsetMs")]
        offset_ms: i64,
    },
    Piecewise {
        anchors: Vec<SyncAnchor>,
    },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncMeasurement {
    pub subtitle_probe_start_ms: i64,
    pub audio_probe_start_ms: i64,
    pub estimated_offset_ms: i64,
    pub correlation_score: Float32,
    pub peak_margin: Float32,
    pub speech_coverage: Float32,
    pub transition_count: i32,
    pub probe_quality: Float32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CorrelationConfig {
    pub min_offset_ms: i64,
    pub max_offset_ms: i64,
    pub step_ms: i64,
    pub onset_weight: Float32,
    pub occupancy_weight: Float32,
    pub silence_weight: Float32,
    pub independent_peak_distance_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProbeCandidate {
    pub subtitle_start_ms: i64,
    pub duration_ms: i64,
    pub distinctiveness: f32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DriftCue {
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
}

/// Kotlin's Double.roundToLong() is floor(x + 0.5), not Rust's half-away-from-zero round().
pub fn kotlin_round_to_long(value: f64) -> i64 {
    (value + 0.5).floor() as i64
}
