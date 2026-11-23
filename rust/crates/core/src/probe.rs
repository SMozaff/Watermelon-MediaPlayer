use std::collections::BTreeMap;

use crate::contracts::{ParsedSubtitle, ProbeCandidate};

const PROBE_DURATION_MS: i64 = 8_000;
const MINIMUM_TRANSITIONS: usize = 3;

pub fn select(
    subtitle: &ParsedSubtitle,
    media_duration_ms: i64,
    max_count: usize,
) -> Vec<ProbeCandidate> {
    if subtitle.cues.is_empty() || media_duration_ms <= 0 || max_count == 0 {
        return Vec::new();
    }

    let duration = PROBE_DURATION_MS.min(media_duration_ms);
    let half = duration / 2;
    let edge_guard = 5_000i64.max((media_duration_ms as f64 * 0.02) as i64);
    let max_start = (media_duration_ms - duration).max(0);

    let mut starts: Vec<i64> = Vec::new();
    for cue in &subtitle.cues {
        let start = (cue.start_ms - half).clamp(0, max_start);
        if !starts.contains(&start) {
            starts.push(start);
        }
    }

    let mut scored: Vec<ProbeCandidate> = starts
        .into_iter()
        .filter_map(|start| score_window(subtitle, start, duration, media_duration_ms, edge_guard))
        .collect();
    // sort_by is stable, matching Kotlin's sortedByDescending tie behavior.
    scored.sort_by(|a, b| b.distinctiveness.total_cmp(&a.distinctiveness));
    if scored.is_empty() {
        return Vec::new();
    }

    let min_separation = 60_000i64
        .max((media_duration_ms as f64 * 0.18) as i64)
        .min((media_duration_ms / max_count as i64).max(duration));

    let mut selected: Vec<ProbeCandidate> = Vec::new();
    for candidate in &scored {
        if selected.iter().all(|chosen| {
            (chosen.subtitle_start_ms - candidate.subtitle_start_ms).abs() >= min_separation
        }) {
            selected.push(candidate.clone());
            if selected.len() == max_count {
                return selected;
            }
        }
    }
    for candidate in &scored {
        if !selected.contains(candidate) {
            selected.push(candidate.clone());
            if selected.len() == max_count {
                break;
            }
        }
    }
    selected
}

fn score_window(
    subtitle: &ParsedSubtitle,
    start: i64,
    duration: i64,
    media_duration_ms: i64,
    edge_guard: i64,
) -> Option<ProbeCandidate> {
    let end = start + duration;
    let cues: Vec<&crate::contracts::SubtitleCue> = subtitle
        .cues
        .iter()
        .filter(|cue| cue.end_ms > start && cue.start_ms < end)
        .collect();
    if cues.len() < MINIMUM_TRANSITIONS {
        return None;
    }

    let occupied_ms: f64 = cues
        .iter()
        .map(|cue| (cue.end_ms.min(end) - cue.start_ms.max(start)).max(0) as f64)
        .sum::<f64>()
        .min(duration as f64);
    let occupancy = (occupied_ms / duration as f64) as f32;
    if !(0.10..=0.85).contains(&occupancy) {
        return None;
    }

    let dialogue_ratio = cues
        .iter()
        .filter(|cue| !looks_non_dialogue(&cue.raw_text))
        .count() as f32
        / cues.len() as f32;
    let gaps: Vec<i64> = cues
        .windows(2)
        .map(|pair| (pair[1].start_ms - pair[0].end_ms).max(0))
        .collect();
    let entropy = normalized_gap_entropy(&gaps);
    let transition_quality = (cues.len() as f32 / 8.0f32).clamp(0.0, 1.0);
    let occupancy_balance = (1.0f32 - (occupancy - 0.45f32).abs() / 0.45f32).clamp(0.0, 1.0);
    let edge_quality = if start < edge_guard || end > media_duration_ms - edge_guard {
        0.45f32
    } else {
        1.0f32
    };
    let score = edge_quality
        * (0.35f32 * transition_quality
            + 0.25f32 * occupancy_balance
            + 0.25f32 * entropy
            + 0.15f32 * dialogue_ratio);
    Some(ProbeCandidate {
        subtitle_start_ms: start,
        duration_ms: duration,
        distinctiveness: score.clamp(0.0, 1.0),
    })
}

fn normalized_gap_entropy(gaps: &[i64]) -> f32 {
    if gaps.len() < 2 {
        return 0.0;
    }
    let mut bins: BTreeMap<i64, usize> = BTreeMap::new();
    for gap in gaps {
        *bins.entry((gap / 400).clamp(0, 10)).or_insert(0) += 1;
    }
    let total = gaps.len() as f64;
    let mut entropy = 0.0f64;
    for count in bins.values() {
        let p = *count as f64 / total;
        entropy -= p * p.ln();
    }
    let max_entropy = (gaps.len().min(11) as f64).ln().max(1e-6);
    (entropy / max_entropy).clamp(0.0, 1.0) as f32
}

fn looks_non_dialogue(text: &str) -> bool {
    let value = text.trim();
    value.is_empty()
        || (value.starts_with('[') && value.ends_with(']'))
        || (value.starts_with('(') && value.ends_with(')'))
        || value.contains('♪')
        || value.contains('♫')
}
