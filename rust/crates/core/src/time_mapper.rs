use crate::contracts::{kotlin_round_to_long, SyncModel};

pub fn map(model: &SyncModel, timestamp_ms: i64) -> i64 {
    let mapped = match model {
        SyncModel::Identity => timestamp_ms,
        SyncModel::Offset { offset_ms } => timestamp_ms + offset_ms,
        SyncModel::Affine { scale, offset_ms } => {
            kotlin_round_to_long(timestamp_ms as f64 * scale.value()) + offset_ms
        }
        SyncModel::Piecewise { anchors } => map_piecewise(timestamp_ms, anchors),
    };
    mapped.max(0)
}

fn map_piecewise(time_ms: i64, anchors: &[crate::contracts::SyncAnchor]) -> i64 {
    let mut anchors = anchors.to_vec();
    anchors.sort_by_key(|anchor| anchor.subtitle_time_ms);
    if anchors.is_empty() {
        return time_ms;
    }
    if anchors.len() == 1 {
        return time_ms + (anchors[0].media_time_ms - anchors[0].subtitle_time_ms);
    }
    if time_ms <= anchors[0].subtitle_time_ms {
        let a = &anchors[0];
        return time_ms + (a.media_time_ms - a.subtitle_time_ms);
    }
    if time_ms >= anchors[anchors.len() - 1].subtitle_time_ms {
        let a = &anchors[anchors.len() - 1];
        return time_ms + (a.media_time_ms - a.subtitle_time_ms);
    }
    let right_index = anchors
        .iter()
        .position(|anchor| anchor.subtitle_time_ms >= time_ms)
        .unwrap();
    let left = &anchors[right_index - 1];
    let right = &anchors[right_index];
    let span = (right.subtitle_time_ms - left.subtitle_time_ms).max(1);
    let t = (time_ms - left.subtitle_time_ms) as f64 / span as f64;
    kotlin_round_to_long(
        left.media_time_ms as f64 + t * (right.media_time_ms - left.media_time_ms) as f64,
    )
}
