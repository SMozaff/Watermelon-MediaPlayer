use serde_json::{json, Value};

use crate::contracts::{SyncMeasurement, SyncModel};
use crate::json_util::{f32_value, measurement_value, model_value};

const MINIMUM_GOOD_PROBES: usize = 2;
const MAXIMUM_CONSENSUS_SPREAD_MS: i64 = 300;
const IDENTITY_TOLERANCE_MS: i64 = 150;
const AUTO_APPLY_THRESHOLD: f32 = 0.86;
const MINIMUM_PROBE_QUALITY: f32 = 0.50;
const MINIMUM_CORRELATION_SCORE: f32 = 0.40;
const MINIMUM_PEAK_MARGIN: f32 = 0.035;

pub fn resolve(measurements: &[SyncMeasurement]) -> Value {
    let mut good: Vec<SyncMeasurement> = measurements
        .iter()
        .filter(|m| is_eligible(m))
        .cloned()
        .collect();
    good.sort_by_key(|m| m.subtitle_probe_start_ms);

    if good.len() < MINIMUM_GOOD_PROBES {
        return low_confidence(measurements);
    }

    let offset = weighted_median(&good);
    let spread = good
        .iter()
        .map(|m| m.estimated_offset_ms)
        .max()
        .unwrap()
        - good.iter().map(|m| m.estimated_offset_ms).min().unwrap();

    if spread <= MAXIMUM_CONSENSUS_SPREAD_MS {
        let unique_peaks = good
            .iter()
            .filter(|m| m.peak_margin.value() >= MINIMUM_PEAK_MARGIN)
            .count();
        if (good.len() == 2 && unique_peaks < 2) || (good.len() >= 3 && unique_peaks < 1) {
            return low_confidence(measurements);
        }

        let ratio = spread as f32 / MAXIMUM_CONSENSUS_SPREAD_MS.max(1) as f32;
        let agreement = (1.0f32 - ratio * ratio).clamp(0.0, 1.0);
        let evidence = average_f32(good.iter().map(|m| m.probe_quality.value())).clamp(0.0, 1.0);
        let uniqueness = average_f32(
            good.iter()
                .map(|m| (m.peak_margin.value() / 0.20f32).clamp(0.0, 1.0)),
        );
        let multi_probe_bonus = if good.len() >= 3 { 0.05f32 } else { 0.0 };
        let confidence = (0.68f32 * agreement
            + 0.22f32 * evidence
            + 0.10f32 * uniqueness
            + multi_probe_bonus)
            .clamp(0.0, 1.0);
        if confidence < AUTO_APPLY_THRESHOLD {
            return low_confidence(measurements);
        }

        let model = if offset.abs() <= IDENTITY_TOLERANCE_MS {
            SyncModel::Identity
        } else {
            SyncModel::Offset { offset_ms: offset }
        };
        return json!({
            "kind": "Synchronized",
            "model": model_value(&model),
            "confidence": f32_value(confidence),
            "measurements": measurements.iter().map(measurement_value).collect::<Vec<_>>(),
        });
    }

    if good.len() >= 3
        && good
            .iter()
            .any(|m| m.peak_margin.value() >= MINIMUM_PEAK_MARGIN)
        && is_likely_linear_drift(&good)
    {
        return json!({
            "kind": "ComplexDriftDetected",
            "measurements": measurements.iter().map(measurement_value).collect::<Vec<_>>(),
        });
    }

    low_confidence(measurements)
}

pub fn has_useful_provisional_offset(measurements: &[SyncMeasurement]) -> bool {
    measurements
        .last()
        .map(|m| is_eligible(m) && m.peak_margin.value() >= MINIMUM_PEAK_MARGIN * 0.5f32)
        .unwrap_or(false)
}

fn low_confidence(measurements: &[SyncMeasurement]) -> Value {
    json!({
        "kind": "LowConfidence",
        "measurements": measurements.iter().map(measurement_value).collect::<Vec<_>>(),
    })
}

fn is_eligible(m: &SyncMeasurement) -> bool {
    m.probe_quality.value() >= MINIMUM_PROBE_QUALITY
        && m.correlation_score.value() >= MINIMUM_CORRELATION_SCORE
}

fn weighted_median(measurements: &[SyncMeasurement]) -> i64 {
    let mut ordered = measurements.to_vec();
    ordered.sort_by_key(|m| m.estimated_offset_ms);
    let total_weight = (ordered
        .iter()
        .map(|m| m.probe_quality.value() as f64)
        .sum::<f64>())
    .max(1e-6);
    let mut running = 0.0f64;
    for measurement in &ordered {
        running += measurement.probe_quality.value() as f64;
        if running >= total_weight / 2.0 {
            return measurement.estimated_offset_ms;
        }
    }
    ordered.last().unwrap().estimated_offset_ms
}

fn average_f32(values: impl Iterator<Item = f32>) -> f32 {
    let values: Vec<f32> = values.collect();
    (values.iter().map(|v| *v as f64).sum::<f64>() / values.len() as f64) as f32
}

fn is_likely_linear_drift(measurements: &[SyncMeasurement]) -> bool {
    let xs: Vec<f64> = measurements
        .iter()
        .map(|m| m.subtitle_probe_start_ms as f64)
        .collect();
    let ys: Vec<f64> = measurements
        .iter()
        .map(|m| m.estimated_offset_ms as f64)
        .collect();
    let x_mean = xs.iter().sum::<f64>() / xs.len() as f64;
    let y_mean = ys.iter().sum::<f64>() / ys.len() as f64;
    let ss_x = xs.iter().map(|x| (x - x_mean) * (x - x_mean)).sum::<f64>();
    if ss_x <= 0.0 {
        return false;
    }
    let slope = xs
        .iter()
        .zip(ys.iter())
        .map(|(x, y)| (x - x_mean) * (y - y_mean))
        .sum::<f64>()
        / ss_x;
    let intercept = y_mean - slope * x_mean;
    let ss_tot = ys.iter().map(|y| (y - y_mean) * (y - y_mean)).sum::<f64>();
    if ss_tot <= 0.0 {
        return false;
    }
    let ss_res = xs
        .iter()
        .zip(ys.iter())
        .map(|(x, y)| {
            let residual = y - (slope * x + intercept);
            residual * residual
        })
        .sum::<f64>();
    let r_squared = (1.0 - ss_res / ss_tot).clamp(0.0, 1.0);
    let max_x = xs.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    let min_x = xs.iter().copied().fold(f64::INFINITY, f64::min);
    let span_ms = max_x - min_x;
    r_squared >= 0.85 && (slope * span_ms).abs() >= (MAXIMUM_CONSENSUS_SPREAD_MS * 2) as f64
}
