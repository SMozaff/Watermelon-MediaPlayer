use crate::contracts::ActivitySignature;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PcmSampleEncoding {
    Pcm16,
    PcmFloat,
}

impl PcmSampleEncoding {
    pub fn bytes_per_sample(self) -> usize {
        match self {
            PcmSampleEncoding::Pcm16 => 2,
            PcmSampleEncoding::PcmFloat => 4,
        }
    }

    pub fn from_golden(value: &str) -> Result<Self, String> {
        match value {
            "PCM_16" => Ok(PcmSampleEncoding::Pcm16),
            "PCM_FLOAT" => Ok(PcmSampleEncoding::PcmFloat),
            other => Err(format!("unknown PCM encoding: {other}")),
        }
    }
}

/// Streaming PCM speech-likelihood accumulator. The `&[u8]` boundary mirrors Kotlin's ByteBuffer;
/// UniFFI will pass a Vec<u8> through the same entry point for the D-2 copy-cost spike.
pub struct SpeechLikelihoodAccumulator {
    target_start_us: i64,
    bucket_ms: i32,
    bucket_count: usize,
    sum_squares: Vec<f64>,
    diff_squares: Vec<f64>,
    zero_crossings: Vec<i64>,
    sample_counts: Vec<i64>,
    prior_sample: Vec<f64>,
    has_prior: Vec<bool>,
    pub decoded_sample_count: i64,
}

impl SpeechLikelihoodAccumulator {
    pub fn new(target_start_us: i64, duration_ms: i64, bucket_ms: i32) -> Self {
        let bucket_count = ((duration_ms as f64 / bucket_ms as f64).ceil() as i32).max(1) as usize;
        Self {
            target_start_us,
            bucket_ms,
            bucket_count,
            sum_squares: vec![0.0; bucket_count],
            diff_squares: vec![0.0; bucket_count],
            zero_crossings: vec![0; bucket_count],
            sample_counts: vec![0; bucket_count],
            prior_sample: vec![0.0; bucket_count],
            has_prior: vec![false; bucket_count],
            decoded_sample_count: 0,
        }
    }

    pub fn consume(
        &mut self,
        buffer: &[u8],
        buffer_offset: usize,
        buffer_size: usize,
        presentation_time_us: i64,
        sample_rate: i32,
        channel_count: i32,
        encoding: PcmSampleEncoding,
    ) {
        if sample_rate <= 0 || channel_count <= 0 || buffer_size == 0 {
            return;
        }
        let frame_bytes = encoding.bytes_per_sample() * channel_count as usize;
        let frame_count = buffer_size / frame_bytes;
        if frame_count == 0 {
            return;
        }
        let limit = buffer.len();
        let bucket_us = self.bucket_ms as i64 * 1_000;

        for frame in 0..frame_count {
            let frame_time_us = presentation_time_us
                + (frame as i64 * 1_000_000i64) / sample_rate as i64;
            let relative_us = frame_time_us - self.target_start_us;
            if relative_us < 0 {
                continue;
            }
            let bucket = relative_us / bucket_us;
            if bucket < 0 || bucket >= self.bucket_count as i64 {
                break;
            }
            let bucket = bucket as usize;
            let base = buffer_offset + frame * frame_bytes;
            if base + frame_bytes > limit {
                continue;
            }

            // The golden generator writes with Java ByteOrder.nativeOrder(); CI hosts are LE.
            let mut mono = 0.0f64;
            for channel in 0..channel_count as usize {
                let index = base + channel * encoding.bytes_per_sample();
                let sample = match encoding {
                    PcmSampleEncoding::Pcm16 => {
                        let raw = i16::from_ne_bytes(buffer[index..index + 2].try_into().unwrap());
                        raw as f64 / 32768.0
                    }
                    PcmSampleEncoding::PcmFloat => {
                        let raw = f32::from_ne_bytes(buffer[index..index + 4].try_into().unwrap());
                        (raw as f64).clamp(-1.0, 1.0)
                    }
                };
                mono += sample;
            }
            let mono = (mono / channel_count as f64).clamp(-1.0, 1.0);

            self.sum_squares[bucket] += mono * mono;
            if self.has_prior[bucket] {
                let diff = mono - self.prior_sample[bucket];
                self.diff_squares[bucket] += diff * diff;
                if (mono >= 0.0) != (self.prior_sample[bucket] >= 0.0) {
                    self.zero_crossings[bucket] += 1;
                }
            }
            self.prior_sample[bucket] = mono;
            self.has_prior[bucket] = true;
            self.sample_counts[bucket] += 1;
            self.decoded_sample_count += 1;
        }
    }

    pub fn finish(&self) -> ActivitySignature {
        let mut rms = vec![0.0f64; self.bucket_count];
        let mut diff_rms = vec![0.0f64; self.bucket_count];
        let mut zcr = vec![0.0f64; self.bucket_count];
        for i in 0..self.bucket_count {
            let n = self.sample_counts[i];
            if n <= 0 {
                continue;
            }
            rms[i] = (self.sum_squares[i] / n as f64).sqrt();
            diff_rms[i] = (self.diff_squares[i] / n.max(1) as f64).sqrt();
            zcr[i] = self.zero_crossings[i] as f64 / n.max(1) as f64;
        }

        let mut valid_rms: Vec<f64> = (0..self.bucket_count)
            .filter(|i| self.sample_counts[*i] > 0)
            .map(|i| rms[i])
            .collect();
        valid_rms.sort_by(|a, b| a.total_cmp(b));
        let p20 = if valid_rms.is_empty() {
            0.0
        } else {
            valid_rms[((valid_rms.len() - 1) as f64 * 0.20) as usize]
        };
        let noise_floor = 0.003f64.max(p20);
        let mut raw = vec![0.0f32; self.bucket_count];

        for i in 0..self.bucket_count {
            if self.sample_counts[i] == 0 {
                continue;
            }
            let energy_ratio = (rms[i] + 1e-9) / (noise_floor * 1.25 + 1e-9);
            let energy = (energy_ratio.ln() / 5.0f64.ln()).clamp(0.0, 1.0);
            if energy < 0.08 {
                continue;
            }
            let zcr_score = trapezoid_score(zcr[i], 0.008, 0.025, 0.22, 0.38);
            let derivative_ratio = diff_rms[i] / (rms[i] + 1e-9);
            let derivative_score = trapezoid_score(derivative_ratio, 0.03, 0.10, 1.10, 1.90);
            raw[i] = (energy * (0.65 + 0.20 * zcr_score + 0.15 * derivative_score)) as f32;
            raw[i] = raw[i].clamp(0.0, 1.0);
        }

        let occupancy = smooth(&raw);
        let mut onsets = vec![0.0f32; self.bucket_count];
        for i in 0..self.bucket_count {
            let previous = if i == 0 { 0.0f32 } else { occupancy[i - 1] };
            let rise = (occupancy[i] - previous).max(0.0);
            if rise < 0.12f32 {
                continue;
            }
            for d in -3..=3 {
                let target = i as i32 + d;
                if target < 0 || target >= self.bucket_count as i32 {
                    continue;
                }
                let kernel = match d.abs() {
                    0 => 1.0f32,
                    1 => 0.65f32,
                    2 => 0.35f32,
                    _ => 0.15f32,
                };
                onsets[target as usize] = onsets[target as usize].max(rise * kernel);
            }
        }

        ActivitySignature {
            start_ms: self.target_start_us / 1_000,
            bucket_ms: self.bucket_ms,
            occupancy,
            onsets,
        }
    }
}

fn smooth(input: &[f32]) -> Vec<f32> {
    if input.len() < 3 {
        return input.to_vec();
    }
    let mut out = vec![0.0f32; input.len()];
    for i in 0..input.len() {
        let mut weighted = input[i] * 0.50f32;
        let mut total = 0.50f32;
        if i > 0 {
            weighted += input[i - 1] * 0.25f32;
            total += 0.25f32;
        }
        if i + 1 < input.len() {
            weighted += input[i + 1] * 0.25f32;
            total += 0.25f32;
        }
        out[i] = (weighted / total).clamp(0.0, 1.0);
    }
    for i in 1..out.len() - 1 {
        if out[i] > 0.45 && out[i - 1] < 0.10 && out[i + 1] < 0.10 {
            out[i] *= 0.35;
        }
    }
    out
}

fn trapezoid_score(value: f64, low0: f64, low1: f64, high1: f64, high0: f64) -> f64 {
    let score = if value <= low0 || value >= high0 {
        0.0
    } else if value < low1 {
        (value - low0) / (low1 - low0)
    } else if value <= high1 {
        1.0
    } else {
        (high0 - value) / (high0 - high1)
    };
    score.clamp(0.0, 1.0)
}

/// Reproduce the deterministic golden PCM signal without committing copyrighted media.
pub fn synthesize_golden_pcm(sample_rate: i32, sample_count: i64, encoding: PcmSampleEncoding) -> Vec<u8> {
    let mut bytes = Vec::with_capacity((sample_count as usize) * encoding.bytes_per_sample());
    for sample in 0..sample_count {
        let time_ms = sample * 1_000 / sample_rate as i64;
        let in_burst = (1_000..=2_000).contains(&time_ms)
            || (3_000..=4_000).contains(&time_ms)
            || (5_000..=6_000).contains(&time_ms);
        let value = if in_burst {
            0.3f64 * (2.0f64 * std::f64::consts::PI * 200.0 * sample as f64 / sample_rate as f64).sin()
        } else {
            0.0
        };
        match encoding {
            PcmSampleEncoding::Pcm16 => {
                let raw = (value * 32_767.0) as i16;
                bytes.extend_from_slice(&raw.to_ne_bytes());
            }
            PcmSampleEncoding::PcmFloat => {
                bytes.extend_from_slice(&(value as f32).to_ne_bytes());
            }
        }
    }
    bytes
}
