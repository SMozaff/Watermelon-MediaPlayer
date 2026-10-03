#!/usr/bin/env python3
"""Fail if user-facing Compose copy is hardcoded in Kotlin instead of being a string resource.

Watermelon is RTL-native (Manifest 1.1) and ships `values-fa` / `values-ar` translations, but
almost all of its UI copy is an English literal written straight into the composables. That is
invisible to Android's resource tooling: `lint` cannot flag it, so the gap grows silently and
translation coverage silently rots.

A full extraction is a large, incremental refactor. This gate makes the remaining work visible
and bounded instead of open-ended: it counts user-facing literals per file, compares against a
committed baseline, and fails only when the count *increases*. Extracting strings therefore
always lowers the number and is rewarded, while adding new hardcoded copy is a CI failure.

Deliberately narrow, to avoid false positives that would train people to ignore it:

  * only Kotlin sources under the Compose UI module and the app shell are scanned;
  * only literals bound to a known user-facing parameter are counted, so log messages,
    route names, persistence keys, intent extras and drawable names are ignored;
  * test sources are excluded.

Usage:
  python3 scripts/verify_no_hardcoded_strings.py            # enforce the ratchet
  python3 scripts/verify_no_hardcoded_strings.py --report   # per-file detail, no gate
  python3 scripts/verify_no_hardcoded_strings.py --update   # rewrite the baseline (deliberate)
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "scripts" / "hardcoded_strings_baseline.json"

SCANNED_MODULES = ("ui-presentation", "app")

# Parameter / call names whose string literal is shown to a user.
USER_FACING_NAMES = (
    "text",
    "contentDescription",
    "label",
    "title",
    "summary",
    "supportingText",
    "placeholder",
    "hint",
    "message",
    "detail",
    "snackbarMessage",
    "confirmLabel",
    "dismissLabel",
    "emptyTitle",
    "emptyMessage",
    "errorMessage",
    "valueDescription",
    "sectionLabel",
)

# name = "literal"            (named argument)
_ASSIGN = re.compile(
    r"\b(?:%s)\s*=\s*\"([^\"]{2,})\"" % "|".join(USER_FACING_NAMES)
)
# Text("literal") / WatermelonGlyph(..., "literal", ...)
_POSITIONAL = re.compile(
    r"\b(?:Text|WatermelonGlyph|WatermelonGlassButton|ColorItem)\s*\(\s*\"([^\"]{2,})\""
)

# Literals that are identifiers rather than copy.
#
# `label` is a real user-facing parameter in this codebase (ToggleRow, NavRow, TextFieldRow) but
# is ALSO the standard name of the Compose animation label, e.g.
# `animateFloatAsState(progress, label = "lockSlide")`. Those are debug labels, not copy.
_ANIMATION_LABEL = re.compile(r"\banimate|\bupdateTransition|\bTransition\b")

# An identifier rather than prose: a path, URI, route or resource name. All-lowercase
# identifiers containing a separator (`video-loader`, `seed-ring`, `folder-loader`,
# `watermelon-pulse`) are animation/Lottie names; `Paths`/`URIs` catch the rest.
_IDENTIFIER = re.compile(r"[a-z0-9]+(?:[._-][a-z0-9]+)+")
_PATH_OR_URI = re.compile(r"[/\\:]")


def is_copy(literal: str) -> bool:
    stripped = literal.strip()
    if not stripped or stripped.isdigit():
        return False
    if _PATH_OR_URI.search(stripped):
        return False
    # All-lowercase, separator-bearing tokens are asset/testTag names, not prose. Note this
    # deliberately keeps single all-lowercase words such as "scan" counted -- on a user-facing
    # parameter a bare lowercase word is far more often copy than an identifier.
    if _IDENTIFIER.fullmatch(stripped):
        return False
    return True


def count_file(path: Path) -> int:
    text = path.read_text(encoding="utf-8")

    assigns = [
        m
        for m in _ASSIGN.finditer(text)
        if not _ANIMATION_LABEL.search(text[text.rfind("\n", 0, m.start()) + 1 : m.end()])
    ]
    found = [m.group(1) for m in assigns]
    found += [m.group(1) for m in _POSITIONAL.finditer(text)]
    return sum(1 for literal in found if is_copy(literal))


def collect() -> dict[str, int]:
    per_file: dict[str, int] = {}
    for module in SCANNED_MODULES:
        for path in sorted((ROOT / module / "src").rglob("*.kt")):
            parts = path.relative_to(ROOT).parts
            if "test" in parts or "androidTest" in parts:
                continue
            total = count_file(path)
            if total:
                per_file[str(path.relative_to(ROOT))] = total
    return per_file


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--update", action="store_true", help="rewrite the baseline file")
    parser.add_argument("--report", action="store_true", help="print per-file detail")
    args = parser.parse_args()

    current = collect()
    total = sum(current.values())

    if args.update:
        BASELINE.write_text(
            json.dumps({"total": total, "files": current}, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(f"Baseline updated: {total} hardcoded user-facing literals across {len(current)} files.")
        return 0

    if args.report:
        for name, count in sorted(current.items(), key=lambda kv: (-kv[1], kv[0])):
            print(f"  {count:4d}  {name}")
        print(f"\nTotal: {total} hardcoded user-facing literals across {len(current)} files.")
        return 0

    if not BASELINE.exists():
        print(
            "Hardcoded-string gate FAILED: baseline is missing.\n"
            "  Create it with: python3 scripts/verify_no_hardcoded_strings.py --update"
        )
        return 1

    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    allowed = baseline["files"]

    regressions = []
    for name, count in current.items():
        ceiling = allowed.get(name, 0)
        if count > ceiling:
            regressions.append((name, ceiling, count))
    # A file that disappeared from the scan is progress, not a regression, so only files
    # still present are compared.

    if regressions:
        print("Hardcoded-string gate FAILED: user-facing copy must not be added as Kotlin literals.")
        for name, ceiling, count in sorted(regressions, key=lambda r: r[2] - r[1], reverse=True):
            print(f"  - {name}: {count} literals (baseline allows {ceiling})")
        print("")
        print("Move user-facing copy into res/values/strings.xml and reference it with")
        print("stringResource(R.string....). See scripts/verify_no_hardcoded_strings.py.")
        return 1

    print(
        f"Hardcoded-string gate passed: {total} literals "
        f"(baseline {baseline['total']}). "
        + (f"{baseline['total'] - total} fewer than baseline." if total < baseline["total"] else "")
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())