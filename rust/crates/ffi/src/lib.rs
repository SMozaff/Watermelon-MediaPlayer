//! UniFFI spike boundary. Only the minimum surface needed to validate the binding
//! tool and the PCM byte-buffer copy cost is exported here; the pure Rust core
//! remains the parity-tested implementation.

uniffi::setup_scaffolding!();

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
