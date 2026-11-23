const CHUNK_SIZE: usize = 64 * 1024;
const MIN_FILE_SIZE: usize = CHUNK_SIZE * 2;

/// The golden corpus stores deterministic synthetic patterns rather than copyrighted media.
pub fn hash_synthetic(size: usize, pattern: &str) -> Result<String, String> {
    if size < MIN_FILE_SIZE {
        return Err(format!(
            "File must be at least {MIN_FILE_SIZE} bytes for OpenSubtitles hashing"
        ));
    }
    let mut bytes = vec![0u8; size];
    match pattern {
        "zeros_128k" | "zeros_256k" => {}
        "head_and_tail_words" => {
            bytes[0] = 1;
            bytes[CHUNK_SIZE] = 2;
        }
        "high_bit_head" => bytes[7] = 0x80,
        other => return Err(format!("unknown golden hash pattern: {other}")),
    }
    Ok(hash_bytes(&bytes))
}

pub fn hash_bytes(bytes: &[u8]) -> String {
    let file_size = bytes.len() as u64;
    let head = sum_chunk(bytes, 0);
    let tail = sum_chunk(bytes, bytes.len() - CHUNK_SIZE);
    let hash = file_size.wrapping_add(head).wrapping_add(tail);
    format!("{hash:016x}")
}

/// Production entry point for content:// URIs: callers pass only the first and last 64 KiB
/// so a multi-gigabyte video never has to be read into memory. Short chunks are zero-padded,
/// matching the FileChannel implementation that produced the frozen Kotlin oracle.
pub fn hash_from_chunks(file_size: u64, head: &[u8], tail: &[u8]) -> Result<String, String> {
    if file_size < MIN_FILE_SIZE as u64 {
        return Err(format!(
            "File must be at least {MIN_FILE_SIZE} bytes for OpenSubtitles hashing"
        ));
    }
    let hash = file_size
        .wrapping_add(sum_chunk_padded(head))
        .wrapping_add(sum_chunk_padded(tail));
    Ok(format!("{hash:016x}"))
}

fn sum_chunk_padded(chunk: &[u8]) -> u64 {
    let mut padded = [0u8; CHUNK_SIZE];
    let len = chunk.len().min(CHUNK_SIZE);
    padded[..len].copy_from_slice(&chunk[..len]);
    sum_chunk(&padded, 0)
}

fn sum_chunk(bytes: &[u8], offset: usize) -> u64 {
    let mut sum = 0u64;
    for i in 0..(CHUNK_SIZE / 8) {
        let start = offset + i * 8;
        let word = u64::from_le_bytes(bytes[start..start + 8].try_into().unwrap());
        sum = sum.wrapping_add(word);
    }
    sum
}
