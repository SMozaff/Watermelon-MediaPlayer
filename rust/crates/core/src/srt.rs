use std::sync::LazyLock;

use regex::Regex;

use crate::bidi;
use crate::contracts::{ParsedSubtitle, SubtitleCue};

static TIME: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})").unwrap());
static TAG: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"</?[a-zA-Z][^>]*>").unwrap());
static BLOCK_SPLIT: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\n[ \t]*\n").unwrap());

fn capture_to_ms(caps: &regex::Captures<'_>, offset: usize) -> i64 {
    let hours: i64 = caps[offset].parse().unwrap_or(0);
    let minutes: i64 = caps[offset + 1].parse().unwrap_or(0);
    let seconds: i64 = caps[offset + 2].parse().unwrap_or(0);
    let raw_ms = &caps[offset + 3];
    let padded = format!("{:0<3}", raw_ms);
    let millis: i64 = padded[..3.min(padded.len())].parse().unwrap_or(0);
    hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 + millis
}

/// Port of SrtParser.parse: BOM/CRLF tolerant, tag stripping, bidi resolution at parse time.
pub fn parse(content: &str, language: Option<&str>) -> ParsedSubtitle {
    let without_bom = content.strip_prefix('\u{feff}').unwrap_or(content);
    let text = without_bom.replace("\r\n", "\n").replace('\r', "\n");
    let mut cues = Vec::new();
    for block in BLOCK_SPLIT.split(&text) {
        let lines: Vec<&str> = block
            .split('\n')
            .map(str::trim)
            .filter(|line| !line.is_empty())
            .collect();
        let Some(timing_idx) = lines.iter().position(|line| line.contains("-->")) else {
            continue;
        };
        let times: Vec<regex::Captures<'_>> = TIME.captures_iter(lines[timing_idx]).collect();
        if times.len() < 2 {
            continue;
        }
        let start = capture_to_ms(&times[0], 1);
        let end = capture_to_ms(&times[1], 1);
        let body = lines[timing_idx + 1..].join("\n");
        if body.trim().is_empty() {
            continue;
        }
        let clean = TAG.replace_all(&body, "").trim().to_owned();
        let rtl = bidi::is_rtl(&clean);
        let display = clean
            .split('\n')
            .map(|line| bidi::format(line, rtl))
            .collect::<Vec<_>>()
            .join("\n");
        cues.push(SubtitleCue {
            index: cues.len() as i32,
            start_ms: start,
            end_ms: end,
            raw_text: clean,
            base_rtl: rtl,
            display_text: display,
        });
    }
    cues.sort_by_key(|cue| cue.start_ms);
    ParsedSubtitle {
        cues,
        language: language.map(str::to_owned),
        source_id: "local".to_owned(),
        offset_ms: 0,
    }
}
