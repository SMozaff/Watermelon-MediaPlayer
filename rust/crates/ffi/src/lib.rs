//! UniFFI boundary for the parity-tested Rust core. JSON strings keep the generated
//! Kotlin surface small while the golden-vector contracts stay in the core crate.

use watermelon_core::contracts::{
    ActivitySignature, CorrelationConfig, DriftCue, ParsedSubtitle, SyncMeasurement, SyncModel,
};
use watermelon_core::speech::{PcmSampleEncoding, SpeechLikelihoodAccumulator};

uniffi::setup_scaffolding!();

/// Minimum input accepted by OpenSubtitles hashing (two 64 KiB chunks).
const OPEN_SUBTITLES_MIN_BYTES: usize = 128 * 1024;

fn error_json(message: impl std::fmt::Display) -> String {
    serde_json::json!({ "error": message.to_string() }).to_string()
}

#[uniffi::export]
pub fn bidi_is_rtl(text: String) -> bool {
    watermelon_core::bidi::is_rtl(&text)
}

#[uniffi::export]
pub fn bidi_format(text: String, base_rtl: bool) -> String {
    watermelon_core::bidi::format(&text, base_rtl)
}

#[uniffi::export]
pub fn srt_parse_json(content: String, language: Option<String>) -> String {
    let parsed = watermelon_core::srt::parse(&content, language.as_deref());
    serde_json::to_string(&serde_json::json!({
        "language": parsed.language,
        "cues": parsed.cues.iter().map(|cue| serde_json::json!({
            "index": cue.index,
            "startMs": cue.start_ms,
            "endMs": cue.end_ms,
            "rawText": cue.raw_text,
            "baseRtl": cue.base_rtl,
            "displayText": cue.display_text,
        })).collect::<Vec<_>>(),
    }))
    .expect("SRT parse result is serializable")
}

/// Measures the cost of the owned `Vec<u8>` boundary UniFFI would use for PCM,
/// compared against a borrowed slice. Returns raw nanoseconds as JSON so the
/// Kotlin side does not need a generated record type during the spike.
#[uniffi::export]
pub fn pcm_copy_spike_json(sample_count: i64, samples: u32) -> String {
    let result = watermelon_core::speech::measure_pcm_copy_cost(sample_count, samples);
    serde_json::to_string(&result).expect("spike result is serializable")
}

/// OpenSubtitles hash of the supplied subtitle bytes as plain 16-hex digits.
#[uniffi::export]
pub fn open_subtitles_hash(bytes: Vec<u8>) -> String {
    if bytes.len() < OPEN_SUBTITLES_MIN_BYTES {
        return error_json("subtitle bytes must be at least 131072 bytes");
    }
    watermelon_core::hash::hash_bytes(&bytes)
}

/// Production OpenSubtitles hash for large media: only the first and last 64 KiB cross FFI.
#[uniffi::export]
pub fn open_subtitles_hash_chunks(file_size: u64, head: Vec<u8>, tail: Vec<u8>) -> String {
    watermelon_core::hash::hash_from_chunks(file_size, &head, &tail).unwrap_or_else(error_json)
}

/// Maps a timestamp through a serialized `SyncModel`; invalid models use Identity.
#[uniffi::export]
pub fn time_map_json(model_json: String, timestamp_ms: i64) -> i64 {
    let model: SyncModel = serde_json::from_str(&model_json).unwrap_or(SyncModel::Identity);
    watermelon_core::time_mapper::map(&model, timestamp_ms)
}

/// Shifts serialized cues and returns a JSON array of `{startMs,endMs,text}`.
#[uniffi::export]
pub fn drift_apply_json(cues_json: String, offset_ms: i64) -> String {
    let cues: Vec<DriftCue> = serde_json::from_str(&cues_json).unwrap_or_default();
    let shifted = watermelon_core::drift::apply(&cues, offset_ms);
    serde_json::to_string(&shifted).unwrap_or_else(|_| "[]".to_owned())
}

/// Shifts a single timestamp, clamped at zero.
#[uniffi::export]
pub fn drift_timestamp_json(timestamp_ms: i64, offset_ms: i64) -> i64 {
    watermelon_core::drift::apply_to_timestamp(timestamp_ms, offset_ms)
}

/// Correlates audio/subtitle signatures. Returns compact offset/score fields or `{"error":...}`.
#[uniffi::export]
pub fn correlate_json(audio_json: String, subtitle_json: String, config_json: String) -> String {
    let result = (|| -> Result<String, String> {
        let audio: ActivitySignature = serde_json::from_str(&audio_json)
            .map_err(|error| error.to_string())?;
        let subtitle: ActivitySignature = serde_json::from_str(&subtitle_json)
            .map_err(|error| error.to_string())?;
        let config: CorrelationConfig = serde_json::from_str(&config_json)
            .map_err(|error| error.to_string())?;

        // Guard inputs that would divide by zero or index past a signature array.
        if audio.bucket_ms <= 0 || subtitle.bucket_ms <= 0 {
            return Err("bucketMs must be > 0".to_owned());
        }
        if audio.onsets.len() < audio.occupancy.len()
            || subtitle.onsets.len() < subtitle.occupancy.len()
        {
            return Err("onsets must cover every occupancy bucket".to_owned());
        }

        let result = watermelon_core::correlator::correlate(&audio, &subtitle, &config)?;
        serde_json::to_string(&serde_json::json!({
            "offsetMs": result.offset_ms,
            "peakScore": result.peak_score,
            "secondPeakScore": result.second_peak_score,
            "peakMargin": result.peak_margin,
        }))
        .map_err(|error| error.to_string())
    })();
    result.unwrap_or_else(error_json)
}

/// Resolves serialized measurements to the core's consensus `Value` (float objects included).
#[uniffi::export]
pub fn consensus_resolve_json(measurements_json: String) -> String {
    let result = (|| -> Result<String, String> {
        let measurements: Vec<SyncMeasurement> = serde_json::from_str(&measurements_json)
            .map_err(|error| error.to_string())?;
        serde_json::to_string(&watermelon_core::consensus::resolve(&measurements))
            .map_err(|error| error.to_string())
    })();
    result.unwrap_or_else(error_json)
}

/// Whether the last measurement supports a provisional offset; invalid input is false.
#[uniffi::export]
pub fn consensus_has_useful_provisional_offset_json(measurements_json: String) -> bool {
    let measurements: Vec<SyncMeasurement> =
        serde_json::from_str(&measurements_json).unwrap_or_default();
    watermelon_core::consensus::has_useful_provisional_offset(&measurements)
}

/// Selects subtitle probe windows as a JSON array with plain f32 distinctiveness.
#[uniffi::export]
pub fn probe_select_json(
    subtitle_json: String,
    media_duration_ms: i64,
    max_count: u32,
) -> String {
    let subtitle: ParsedSubtitle = match serde_json::from_str(&subtitle_json) {
        Ok(subtitle) => subtitle,
        Err(error) => return error_json(error),
    };
    let candidates = watermelon_core::probe::select(&subtitle, media_duration_ms, max_count as usize);
    serde_json::to_string(
        &candidates
            .iter()
            .map(|candidate| {
                serde_json::json!({
                    "subtitleStartMs": candidate.subtitle_start_ms,
                    "durationMs": candidate.duration_ms,
                    "distinctiveness": candidate.distinctiveness,
                })
            })
            .collect::<Vec<_>>(),
    )
    .unwrap_or_else(|_| "[]".to_owned())
}

/// Builds a subtitle activity signature with plain-number occupancy and onsets.
#[uniffi::export]
pub fn activity_build_json(
    subtitle_json: String,
    start_ms: i64,
    end_ms: i64,
    bucket_ms: i32,
) -> String {
    let result = (|| -> Result<String, String> {
        let subtitle: ParsedSubtitle =
            serde_json::from_str(&subtitle_json).map_err(|error| error.to_string())?;
        let signature =
            watermelon_core::activity::build(&subtitle, start_ms, end_ms, bucket_ms)?;
        serde_json::to_string(&serde_json::json!({
            "startMs": signature.start_ms,
            "bucketMs": signature.bucket_ms,
            "occupancy": signature.occupancy,
            "onsets": signature.onsets,
        }))
        .map_err(|error| error.to_string())
    })();
    result.unwrap_or_else(error_json)
}

/// One PCM buffer and its presentation metadata, passed by value from Kotlin.
#[derive(uniffi::Record)]
pub struct PcmChunk {
    pub bytes: Vec<u8>,
    pub presentation_time_us: i64,
    pub sample_rate: i32,
    pub channel_count: i32,
    pub encoding: String,
}

/// Feeds PCM chunks through the speech accumulator and returns its signature as JSON.
#[uniffi::export]
pub fn speech_accumulator_json(
    target_start_us: i64,
    duration_ms: i64,
    bucket_ms: i32,
    chunks: Vec<PcmChunk>,
) -> String {
    if bucket_ms <= 0 {
        return error_json("bucketMs must be > 0");
    }
    let mut accumulator =
        SpeechLikelihoodAccumulator::new(target_start_us, duration_ms, bucket_ms);
    for chunk in &chunks {
        let encoding = match PcmSampleEncoding::from_golden(&chunk.encoding) {
            Ok(encoding) => encoding,
            Err(error) => return error_json(error),
        };
        accumulator.consume(
            &chunk.bytes,
            0,
            chunk.bytes.len(),
            chunk.presentation_time_us,
            chunk.sample_rate,
            chunk.channel_count,
            encoding,
        );
    }
    let signature = accumulator.finish();
    serde_json::to_string(&serde_json::json!({
        "startMs": signature.start_ms,
        "bucketMs": signature.bucket_ms,
        "decodedSampleCount": accumulator.decoded_sample_count,
        "occupancy": signature.occupancy,
        "onsets": signature.onsets,
    }))
    .unwrap_or_else(|_| "{}".to_owned())
}
