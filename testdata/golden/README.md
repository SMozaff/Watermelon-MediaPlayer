# Kotlin golden-vector oracle

Generated from the Kotlin baseline by `.github/workflows/migration-golden-vectors.yml`
on **2026-11-22**, workflow run [`45074382455`](https://github.com/SMozaff/Watermelon-MediaPlayer/actions/runs/45074382455),
from generator commit `bf9af08`.

- The workflow ran generation twice into separate directories and proved the two outputs are
  byte-identical (`diff -u golden-1.sha256 golden-2.sha256` passed).
- Total corpus size: 111,890 bytes (limit 5 MB).
- `SHA256SUMS` is the run-1 checksum set; run 2 matched it exactly.
- Every floating-point result is stored twice: a decimal string and the raw IEEE-754 bit
  pattern, so D-3 (float tolerance) can be decided without regenerating the corpus.
- Production behavior changes that affect these vectors are recorded in
  [`docs/migration/defect-fixes.md`](../../docs/migration/defect-fixes.md). Regenerate from a
  corrected baseline when behavior changes, never by editing these files by hand.

The schema is reconstructed from the migration stage reports (the original Stage 2 brief was
not committed); owner confirmation is recorded as an open input in
[`docs/migration/proposed-decisions.md`](../../docs/migration/proposed-decisions.md).
