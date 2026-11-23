# Behavior changes applied before the golden oracle is frozen

**Date:** 2026-11-23
**Authorization:** Owner decision recorded in [proposed-decisions.md](proposed-decisions.md),
"Defect disposition — fix in Kotlin first". These are deliberate production behavior changes,
not migration parity work.

## 1. BidiFormatter trailing neutrals

**File:** `subtitle-engine/src/main/kotlin/com/watermelon/subtitle/bidi/BidiFormatter.kt`

`format(text, baseRtl)` previously consumed neutral characters into an opposite-direction
isolate and never moved trailing neutrals back out, contradicting its own KDoc. The run loop now
collects neutrals separately and appends them after PDI.

| Input | Before | After |
|---|---|---|
| `format("שלום abc ", true)` | `שלום \u2068abc \u2069` | `שלום \u2068abc\u2069 ` |
| `format("سلام! 123", false)` | `\u2068سلام! \u2069` + `123` | `\u2068سلام!\u2069 123` |

Tests updated: `BidiFormatterTest`, `BidiFormatterCharacterizationTest`. An `isRtl` boundary test
was added (strict RTL majority; 4-vs-4 is LTR) and the SRT mixed-script fixture documents why it
uses `abc` rather than `hello`.

## 2. OpenSubtitlesHasher minimum file size

**File:** `subtitle-engine/src/main/kotlin/com/watermelon/subtitle/hash/OpenSubtitlesHasher.kt`

Both public overloads accepted files from 65,536 bytes, where the head and tail 64 KiB chunks
overlap and the same bytes are counted twice. The published OSHash reference (README and Rust
implementation in `opensubtitles/oshash`) rejects files smaller than 131,072 bytes. The minimum
is now `MIN_FILE_SIZE = CHUNK_SIZE * 2`, and the KDoc states the 128 KB requirement.

Tests updated: `OpenSubtitlesHasherTest` now checks that 65,535, 65,536 and 131,071 bytes are
rejected, that the 131,072-byte minimum hashes correctly, and that head/tail words are summed
without overlap.

**Call-site impact:** `SubtitleRepositoryImpl` already wraps fingerprinting in
`runCatching { hashFor(mediaItem) }.getOrNull()`, so media under 128 KB simply loses the hash;
there is no new crash path.

## Consequence for the migration

The golden vectors must be generated **from this corrected Kotlin baseline**. The stage reports
and G0 report keep their historical wording; this document is the current behavior record.
No golden corpus has been generated or committed yet.
