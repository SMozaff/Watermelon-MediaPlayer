const FSI: char = '\u{2068}';
const PDI: char = '\u{2069}';

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Dir {
    Rtl,
    Ltr,
    Neutral,
}

fn direction_of(ch: char) -> Dir {
    let code = ch as u32;
    let rtl = (0x0590..=0x05FF).contains(&code)
        || (0x0600..=0x06FF).contains(&code)
        || (0x0750..=0x077F).contains(&code)
        || (0x08A0..=0x08FF).contains(&code)
        || (0xFB50..=0xFDFF).contains(&code)
        || (0xFE70..=0xFEFF).contains(&code);
    if rtl {
        return Dir::Rtl;
    }
    let ltr = ch.is_ascii_uppercase()
        || ch.is_ascii_lowercase()
        || ch.is_ascii_digit()
        || (0x00C0..=0x024F).contains(&code);
    if ltr {
        return Dir::Ltr;
    }
    Dir::Neutral
}

/// True when RTL-script characters dominate the strongly directional characters.
pub fn is_rtl(text: &str) -> bool {
    let mut rtl = 0usize;
    let mut ltr = 0usize;
    for ch in text.chars() {
        match direction_of(ch) {
            Dir::Rtl => rtl += 1,
            Dir::Ltr => ltr += 1,
            Dir::Neutral => {}
        }
    }
    rtl > ltr
}

/// Isolate maximal opposite-base runs in FSI..PDI, keeping trailing neutrals outside.
pub fn format(text: &str, base_rtl: bool) -> String {
    if text.is_empty() {
        return String::new();
    }
    let isolate_dir = if base_rtl { Dir::Ltr } else { Dir::Rtl };
    let chars: Vec<char> = text.chars().collect();
    let mut out = String::with_capacity(text.len() + 8);
    let mut i = 0usize;
    while i < chars.len() {
        if direction_of(chars[i]) == isolate_dir {
            let mut run = String::new();
            let mut trailing_neutrals = String::new();
            while i < chars.len() {
                match direction_of(chars[i]) {
                    dir if dir == isolate_dir => {
                        run.push_str(&trailing_neutrals);
                        trailing_neutrals.clear();
                        run.push(chars[i]);
                        i += 1;
                    }
                    Dir::Neutral => {
                        trailing_neutrals.push(chars[i]);
                        i += 1;
                    }
                    _ => break,
                }
            }
            out.push(FSI);
            out.push_str(&run);
            out.push(PDI);
            out.push_str(&trailing_neutrals);
        } else {
            out.push(chars[i]);
            i += 1;
        }
    }
    out
}
