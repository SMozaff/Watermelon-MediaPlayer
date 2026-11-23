use crate::contracts::{ActivitySignature, CorrelationConfig};

#[derive(Debug, Clone, PartialEq)]
pub struct CorrelationResult {
    pub offset_ms: i64,
    pub peak_score: f32,
    pub second_peak_score: f32,
    pub peak_margin: f32,
}

pub fn correlate(
    audio: &ActivitySignature,
    subtitle: &ActivitySignature,
    config: &CorrelationConfig,
) -> Result<CorrelationResult, String> {
    if audio.bucket_ms != subtitle.bucket_ms {
        return Err("bucket sizes must match".to_owned());
    }
    if config.max_offset_ms < config.min_offset_ms {
        return Err("maxOffsetMs must be >= minOffsetMs".to_owned());
    }
    if config.step_ms < audio.bucket_ms as i64 || config.step_ms % audio.bucket_ms as i64 != 0 {
        return Err("stepMs must be a multiple of bucketMs".to_owned());
    }

    let first_offset = ceil_to_step(config.min_offset_ms, config.step_ms);
    let last_offset = floor_to_step(config.max_offset_ms, config.step_ms);
    if first_offset > last_offset {
        return Ok(CorrelationResult {
            offset_ms: 0,
            peak_score: 0.0,
            second_peak_score: 0.0,
            peak_margin: 0.0,
        });
    }

    let mut best_offset = first_offset;
    let mut best_score = f32::NEG_INFINITY;
    let mut scored: Vec<(i64, f32)> = Vec::new();
    let mut offset = first_offset;
    while offset <= last_offset {
        let score = score_at_offset(audio, subtitle, offset, config);
        scored.push((offset, score));
        if score > best_score {
            best_score = score;
            best_offset = offset;
        }
        offset += config.step_ms;
    }

    let second = scored
        .iter()
        .filter(|(candidate, _)| {
            (candidate - best_offset).abs() >= config.independent_peak_distance_ms
        })
        .map(|(_, score)| *score)
        .fold(0.0f32, f32::max);

    Ok(CorrelationResult {
        offset_ms: best_offset,
        peak_score: best_score.clamp(0.0, 1.0),
        second_peak_score: second.clamp(0.0, 1.0),
        peak_margin: (best_score - second).clamp(0.0, 1.0),
    })
}

fn score_at_offset(
    audio: &ActivitySignature,
    subtitle: &ActivitySignature,
    offset_ms: i64,
    config: &CorrelationConfig,
) -> f32 {
    let mut occupancy_raw = 0.0f32;
    let mut onset_raw = 0.0f32;
    let mut silence_raw = 0.0f32;
    let mut weighted_samples = 0.0f32;
    let bucket_ms = audio.bucket_ms as i64;

    for (i, a_value) in audio.occupancy.iter().enumerate() {
        let media_time_ms = audio.start_ms + i as i64 * bucket_ms;
        let source_subtitle_time_ms = media_time_ms - offset_ms;
        let relative = source_subtitle_time_ms - subtitle.start_ms;
        if relative < 0 {
            continue;
        }
        let subtitle_index = relative / subtitle.bucket_ms as i64;
        if subtitle_index < 0 || subtitle_index as usize >= subtitle.occupancy.len() {
            continue;
        }
        let idx = subtitle_index as usize;

        let a = a_value.clamp(0.0, 1.0);
        let s = subtitle.occupancy[idx].clamp(0.0, 1.0);
        let ao = audio.onsets[i].clamp(0.0, 1.0);
        let so = subtitle.onsets[idx].clamp(0.0, 1.0);

        occupancy_raw += a.min(s) - 0.90f32 * (a - s).abs();
        onset_raw += ao.min(so) - 0.65f32 * (ao - so).abs();
        silence_raw += (1.0f32 - a) * (1.0f32 - s);
        weighted_samples += 1.0f32;
    }

    if weighted_samples <= 0.0 {
        return 0.0;
    }
    let occupancy = normalize_signed(occupancy_raw / weighted_samples, -0.90, 1.0);
    let onset = normalize_signed(onset_raw / weighted_samples, -0.65, 1.0);
    let silence = (silence_raw / weighted_samples).clamp(0.0, 1.0);
    let total_weight = config.onset_weight.value()
        + config.occupancy_weight.value()
        + config.silence_weight.value();
    if total_weight <= 0.0 {
        return 0.0;
    }
    (config.onset_weight.value() * onset
        + config.occupancy_weight.value() * occupancy
        + config.silence_weight.value() * silence)
        / total_weight
}

fn normalize_signed(value: f32, min_value: f32, max_value: f32) -> f32 {
    ((value - min_value) / (max_value - min_value)).clamp(0.0, 1.0)
}

fn floor_to_step(value: i64, step: i64) -> i64 {
    value.div_euclid(step) * step
}

fn ceil_to_step(value: i64, step: i64) -> i64 {
    -(-value).div_euclid(step) * step
}
