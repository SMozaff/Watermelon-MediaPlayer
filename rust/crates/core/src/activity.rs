use crate::contracts::{ActivitySignature, ParsedSubtitle};

const SUBTITLE_ONSET_BLUR_MS: i64 = 200;
const SDH_WEIGHT: f32 = 0.25;

pub fn build(
    subtitle: &ParsedSubtitle,
    start_ms: i64,
    end_ms: i64,
    bucket_ms: i32,
) -> Result<ActivitySignature, String> {
    if end_ms <= start_ms {
        return Err("endMs must be greater than startMs".to_owned());
    }
    if bucket_ms <= 0 {
        return Err("bucketMs must be > 0".to_owned());
    }

    let bucket_count = (((end_ms - start_ms) as f64 / bucket_ms as f64).ceil() as i32).max(1) as usize;
    let mut occupancy = vec![0.0f32; bucket_count];
    let mut onsets = vec![0.0f32; bucket_count];
    let blur_buckets = ((SUBTITLE_ONSET_BLUR_MS as f64 / bucket_ms as f64).ceil() as i32).max(1);

    for cue in &subtitle.cues {
        if cue.end_ms <= start_ms || cue.start_ms >= end_ms {
            continue;
        }
        let weight = if is_likely_non_dialogue(&cue.raw_text) {
            SDH_WEIGHT
        } else {
            1.0f32
        };

        let first = ((cue.start_ms.max(start_ms) - start_ms) as f64 / bucket_ms as f64)
            .floor() as i32;
        let first = first.clamp(0, bucket_count as i32 - 1) as usize;
        let last_exclusive = ((cue.end_ms.min(end_ms) - start_ms) as f64 / bucket_ms as f64)
            .ceil() as i32;
        let last_exclusive = last_exclusive
            .clamp(first as i32 + 1, bucket_count as i32) as usize;

        for i in first..last_exclusive {
            occupancy[i] = occupancy[i].max(weight);
        }

        let onset = ((cue.start_ms - start_ms) as f64 / bucket_ms as f64).floor() as i32;
        if onset >= 0 && onset < bucket_count as i32 {
            for d in -blur_buckets..=blur_buckets {
                let i = onset + d;
                if i < 0 || i >= bucket_count as i32 {
                    continue;
                }
                let kernel = 1.0f32 - d.abs() as f32 / (blur_buckets + 1) as f32;
                onsets[i as usize] = onsets[i as usize].max(weight * kernel);
            }
        }
    }

    Ok(ActivitySignature {
        start_ms,
        bucket_ms,
        occupancy,
        onsets,
    })
}

pub fn is_likely_non_dialogue(text: &str) -> bool {
    let value = text.trim();
    if value.is_empty() {
        return true;
    }
    if (value.starts_with('[') && value.ends_with(']'))
        || (value.starts_with('(') && value.ends_with(')'))
    {
        return true;
    }
    value.contains('♪') || value.contains('♫')
}
