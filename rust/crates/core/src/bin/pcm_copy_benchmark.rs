use serde_json::Value;
use watermelon_core::speech;

const DEFAULT_SAMPLES: u32 = 20;
const DEFAULT_SAMPLE_COUNT: i64 = 128_000;

fn main() {
    let mut args = std::env::args().skip(1);
    let sample_count = parse_arg(&mut args, "--sample-count").unwrap_or(DEFAULT_SAMPLE_COUNT);
    let samples = parse_arg(&mut args, "--samples").unwrap_or(DEFAULT_SAMPLES as i64) as u32;

    let result = speech::measure_pcm_copy_cost(sample_count, samples);
    let warmup = result["warmup"].as_u64().unwrap_or(0);
    println!(
        "PCM spike: {} bytes, {} timed samples, {} warmup",
        result["bytes"].as_u64().unwrap_or(0),
        samples,
        warmup
    );
    print_bench(&result, "borrowed_ns", "pcm_copy_borrowed", warmup);
    print_bench(&result, "owned_copy_ns", "pcm_copy_owned", warmup);
}

fn parse_arg(args: &mut impl Iterator<Item = String>, name: &str) -> Option<i64> {
    let value = args.find(|arg| arg.starts_with(name))?;
    value.split_once('=').and_then(|(_, value)| value.parse().ok())
}

fn print_bench(result: &Value, key: &str, name: &str, warmup: u64) {
    let values: Vec<u64> = result[key]
        .as_array()
        .expect("benchmark samples")
        .iter()
        .map(|value| value.as_u64().expect("nanoseconds"))
        .collect();
    assert!(!values.is_empty(), "no samples for {key}");
    let mut sorted = values.clone();
    sorted.sort_unstable();
    let median = sorted[sorted.len() / 2];
    let mean = values.iter().copied().sum::<u64>() / values.len() as u64;
    let checksum = values.iter().copied().fold(0u64, u64::wrapping_add);
    println!(
        "BENCH name={name} warmup={warmup} timed={} raw_ns={} median_ns={median} mean_ns={mean} checksum={checksum}",
        values.len(),
        values
            .iter()
            .map(u64::to_string)
            .collect::<Vec<_>>()
            .join(",")
    );
}
