use std::env;
use std::fs;
use std::path::PathBuf;
use std::process::ExitCode;

use serde::Deserialize;
use serde_json::{json, Value};
use watermelon_core::{
    activity, bidi, consensus,
    contracts::{
        ActivitySignature, CorrelationConfig, DriftCue, Float32, ParsedSubtitle, SyncMeasurement,
        SyncModel,
    },
    correlator, drift, hash,
    json_util::{cue_value, f32_value, signature_value},
    probe, speech, srt, time_mapper,
};

const FLOAT_TOLERANCE: f32 = 1e-6;

#[derive(Debug, Deserialize)]
struct GoldenFile {
    component: String,
    #[allow(dead_code)]
    generator: String,
    cases: Vec<GoldenCase>,
}

#[derive(Debug, Deserialize)]
struct GoldenCase {
    id: String,
    input: Value,
    output: Value,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GoldenSignature {
    start_ms: i64,
    bucket_ms: i32,
    occupancy: Vec<Float32>,
    onsets: Vec<Float32>,
}

impl From<GoldenSignature> for ActivitySignature {
    fn from(value: GoldenSignature) -> Self {
        Self {
            start_ms: value.start_ms,
            bucket_ms: value.bucket_ms,
            occupancy: value.occupancy.iter().map(Float32::value).collect(),
            onsets: value.onsets.iter().map(Float32::value).collect(),
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SrtParseInput {
    content: String,
    language: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BidiInput {
    text: String,
    base_rtl: bool,
}

#[derive(Debug, Deserialize)]
struct HashInput {
    size: usize,
    pattern: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TimeMapperInput {
    model: SyncModel,
    timestamp_ms: i64,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DriftInput {
    cues: Option<Vec<DriftCue>>,
    offset_ms: i64,
    timestamp_ms: Option<i64>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CorrelatorInput {
    audio: GoldenSignature,
    subtitle_context: GoldenSignature,
    config: CorrelationConfig,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConsensusInput {
    measurements: Vec<SyncMeasurement>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProbeInput {
    subtitle: ParsedSubtitle,
    media_duration_ms: i64,
    max_count: usize,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ActivityInput {
    subtitle: ParsedSubtitle,
    start_ms: i64,
    end_ms: i64,
    bucket_ms: i32,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SpeechInput {
    target_start_us: i64,
    duration_ms: i64,
    bucket_ms: i32,
    sample_rate: i32,
    channel_count: i32,
    encoding: String,
    sample_count: i64,
}

fn main() -> ExitCode {
    let golden_dir = env::args()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("testdata/golden"));
    let mut files: Vec<PathBuf> = match fs::read_dir(&golden_dir) {
        Ok(entries) => entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.extension().and_then(|s| s.to_str()) == Some("json"))
            .collect(),
        Err(error) => {
            eprintln!("cannot read {}: {error}", golden_dir.display());
            return ExitCode::FAILURE;
        }
    };
    files.sort();

    let mut total_cases = 0usize;
    let mut failures: Vec<String> = Vec::new();

    for path in files {
        let text = match fs::read_to_string(&path) {
            Ok(text) => text,
            Err(error) => {
                failures.push(format!("{}: {error}", path.display()));
                continue;
            }
        };
        let golden: GoldenFile = match serde_json::from_str(&text) {
            Ok(golden) => golden,
            Err(error) => {
                failures.push(format!("{}: invalid golden JSON: {error}", path.display()));
                continue;
            }
        };
        println!("[{}] {} cases", golden.component, golden.cases.len());
        for case in &golden.cases {
            total_cases += 1;
            let path_label = format!("{}/{}", golden.component, case.id);
            match run_case(&golden.component, case) {
                Ok(actual) => {
                    let mut errors = Vec::new();
                    compare(&case.output, &actual, "$", &mut errors);
                    if errors.is_empty() {
                        println!("  PASS {path_label}");
                    } else {
                        println!("  FAIL {path_label}");
                        for error in errors {
                            failures.push(format!("{path_label}: {error}"));
                        }
                    }
                }
                Err(error) => {
                    println!("  FAIL {path_label}");
                    failures.push(format!("{path_label}: {error}"));
                }
            }
        }
    }

    println!("\nRust parity: {total_cases} cases, {} failures", failures.len());
    for failure in &failures {
        println!("  {failure}");
    }
    if failures.is_empty() {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    }
}

fn run_case(component: &str, case: &GoldenCase) -> Result<Value, String> {
    match component {
        "srt_parse" => {
            let input: SrtParseInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            let parsed = srt::parse(&input.content, input.language.as_deref());
            Ok(json!({
                "language": parsed.language.clone(),
                "cues": parsed.cues.iter().map(cue_value).collect::<Vec<_>>(),
            }))
        }
        "bidi_format" => {
            let input: BidiInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            Ok(json!({
                "isRtl": bidi::is_rtl(&input.text),
                "formatted": bidi::format(&input.text, input.base_rtl),
            }))
        }
        "hash_opensubtitles" => {
            let input: HashInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            Ok(json!({"hash": hash::hash_synthetic(input.size, &input.pattern)?}))
        }
        "time_mapper" => {
            let input: TimeMapperInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            Ok(json!({"mappedMs": time_mapper::map(&input.model, input.timestamp_ms)}))
        }
        "drift_corrector" => {
            let input: DriftInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            if let Some(cues) = input.cues {
                let shifted = drift::apply(&cues, input.offset_ms);
                Ok(json!({"cues": shifted.iter().map(|cue| json!({
                    "startMs": cue.start_ms,
                    "endMs": cue.end_ms,
                    "text": cue.text.clone(),
                })).collect::<Vec<_>>()}))
            } else {
                let timestamp_ms = input.timestamp_ms.ok_or("missing timestampMs")?;
                Ok(json!({"mappedMs": drift::apply_to_timestamp(timestamp_ms, input.offset_ms)}))
            }
        }
        "correlator" => {
            let input: CorrelatorInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            let result = correlator::correlate(
                &input.audio.into(),
                &input.subtitle_context.into(),
                &input.config,
            )?;
            Ok(json!({
                "offsetMs": result.offset_ms,
                "peakScore": f32_value(result.peak_score),
                "secondPeakScore": f32_value(result.second_peak_score),
                "peakMargin": f32_value(result.peak_margin),
            }))
        }
        "consensus" => {
            let input: ConsensusInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            Ok(json!({"result": consensus::resolve(&input.measurements)}))
        }
        "probe_select" => {
            let input: ProbeInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            let candidates = probe::select(&input.subtitle, input.media_duration_ms, input.max_count);
            Ok(json!({"candidates": candidates.iter().map(|candidate| json!({
                "subtitleStartMs": candidate.subtitle_start_ms,
                "durationMs": candidate.duration_ms,
                "distinctiveness": f32_value(candidate.distinctiveness),
            })).collect::<Vec<_>>()}))
        }
        "activity_builder" => {
            let input: ActivityInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            let signature = activity::build(
                &input.subtitle,
                input.start_ms,
                input.end_ms,
                input.bucket_ms,
            )?;
            Ok(signature_value(&signature))
        }
        "speech_accumulator" => {
            let input: SpeechInput = serde_json::from_value(case.input.clone()).map_err(fmt_err)?;
            let encoding = speech::PcmSampleEncoding::from_golden(&input.encoding)?;
            let bytes = speech::synthesize_golden_pcm(input.sample_rate, input.sample_count, encoding);
            let mut accumulator = speech::SpeechLikelihoodAccumulator::new(
                input.target_start_us,
                input.duration_ms,
                input.bucket_ms,
            );
            accumulator.consume(
                &bytes,
                0,
                bytes.len(),
                0,
                input.sample_rate,
                input.channel_count,
                encoding,
            );
            let signature = accumulator.finish();
            Ok(json!({
                "startMs": signature.start_ms,
                "bucketMs": signature.bucket_ms,
                "decodedSampleCount": accumulator.decoded_sample_count,
                "occupancy": signature.occupancy.iter().map(|v| f32_value(*v)).collect::<Vec<_>>(),
                "onsets": signature.onsets.iter().map(|v| f32_value(*v)).collect::<Vec<_>>(),
            }))
        }
        other => Err(format!("unknown component: {other}")),
    }
}

fn fmt_err(error: serde_json::Error) -> String {
    error.to_string()
}

/// D-3: 1e-6 tolerance for derived floats; bit-exact integers, Longs, hashes and strings.
/// Raw bits are diagnostic only: bit equality is an accepted fast path, never a requirement.
fn compare(expected: &Value, actual: &Value, path: &str, errors: &mut Vec<String>) {
    match (expected, actual) {
        (Value::Object(e), Value::Object(a))
            if e.contains_key("decimal") && e.contains_key("bits") =>
        {
            let expected_bits = e["bits"].as_str().unwrap_or("");
            let actual_bits = a["bits"].as_str().unwrap_or("");
            if expected_bits == actual_bits {
                return;
            }
            let expected_value = parse_float(e);
            let actual_value = parse_float(a);
            match (expected_value, actual_value) {
                (Some(e_value), Some(a_value)) if (e_value - a_value).abs() <= FLOAT_TOLERANCE => {}
                _ => errors.push(format!(
                    "{path}: float mismatch expected(decimal={}, bits={}) actual(decimal={}, bits={})",
                    e["decimal"].as_str().unwrap_or("?"),
                    expected_bits,
                    a["decimal"].as_str().unwrap_or("?"),
                    actual_bits,
                )),
            }
        }
        (Value::Object(e), Value::Object(a)) => {
            let mut keys: Vec<&String> = e.keys().chain(a.keys()).collect();
            keys.sort();
            keys.dedup();
            for key in keys {
                let child_path = format!("{path}.{key}");
                match (e.get(key), a.get(key)) {
                    (Some(e_value), Some(a_value)) => compare(e_value, a_value, &child_path, errors),
                    _ => errors.push(format!("{child_path}: missing key")),
                }
            }
        }
        (Value::Array(e), Value::Array(a)) => {
            if e.len() != a.len() {
                errors.push(format!("{path}: array length expected {} actual {}", e.len(), a.len()));
                return;
            }
            for (index, (e_value, a_value)) in e.iter().zip(a.iter()).enumerate() {
                compare(e_value, a_value, &format!("{path}[{index}]"), errors);
            }
        }
        (Value::Number(_), Value::Number(_)) => {
            if expected != actual {
                errors.push(format!("{path}: expected {expected} actual {actual}"));
            }
        }
        (Value::String(e), Value::String(a)) => {
            if e != a {
                errors.push(format!("{path}: expected {e:?} actual {a:?}"));
            }
        }
        (Value::Bool(e), Value::Bool(a)) => {
            if e != a {
                errors.push(format!("{path}: expected {e} actual {a}"));
            }
        }
        (Value::Null, Value::Null) => {}
        _ => errors.push(format!("{path}: shape mismatch expected {expected} actual {actual}")),
    }
}

fn parse_float(value: &serde_json::Map<String, Value>) -> Option<f32> {
    if let Some(decimal) = value.get("decimal").and_then(Value::as_str) {
        if let Ok(parsed) = decimal.parse::<f32>() {
            return Some(parsed);
        }
    }
    let bits = u32::from_str_radix(value.get("bits").and_then(Value::as_str).unwrap_or(""), 16).ok()?;
    Some(f32::from_bits(bits))
}
