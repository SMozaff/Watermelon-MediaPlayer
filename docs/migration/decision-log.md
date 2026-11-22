# Watermelon Kotlin-to-Rust migration decision log

Owner decisions only. Blank choices remain unapproved; implementation must not infer them.

| # | Decision | Options | Chosen | Date | Owner |
|---|---|---|---|---|---|
| D-1 | Continue past G0? | Continue / stop | Continue, conditional on G0 being met; G0 evidence recorded in oracle run `45074382455` and JVM baseline run `45075503837` | 2026-11-23 | SMozaff (repository owner) |
| D-2 | Binding tool | UniFFI / hand-written JNI | UniFFI, with a time-boxed spike before committing to all twelve ports; measure `SpeechLikelihoodAccumulator` ByteBuffer copy cost | 2026-11-23 | SMozaff (repository owner) |
| D-3 | Float tolerance | 1e-6 / bit-exact | 1e-6 for derived floating-point values; bit-exact for integer, Long and hash outputs; raw bits remain diagnostic | 2026-11-23 | SMozaff (repository owner) |
| D-4 | Shadow-mode logging policy | Local only / internal builds / opt-in | Local only: opt-in debug logging of Kotlin vs Rust outputs, no telemetry and no upload | 2026-11-23 | SMozaff (repository owner) |
| D-5 | MP3 encoder | Keep Kotlin / other | Keep the pure-Java encoder in `media-tools` | 2026-11-23 | SMozaff (repository owner) |
| D-6 | Migration owner and reviewer | Name owner + independent reviewer | Approve the process requirement: one implementation owner and one independent reviewer to be named by the owner before Stage 3 review | 2026-11-23 | SMozaff (repository owner) |

D-1–D-6 were approved by the repository owner on 2026-11-23 as the proposals in
[proposed-decisions.md](proposed-decisions.md). They were previously blank and were not inferred
from code, reports or the proposal file.

**Still open under D-6:** the names of the implementation owner and independent reviewer have not
been supplied. Stage 3 implementation may proceed, but its review cannot be signed off until those
names are recorded here.
