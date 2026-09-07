#!/usr/bin/env python3
"""Fail fast if the project regresses from AGP 9 built-in Kotlin.

This is intentionally a static configuration gate. AGP 9 owns Kotlin support in Android
modules; applying org.jetbrains.kotlin.android again is a configuration error. The gate also
rejects the legacy android.kotlinOptions DSL so a future dependency bump cannot silently
reintroduce the exact incompatibility that broke CI.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_BUILD_FILES = (
    "app/build.gradle.kts",
    "playback-engine/build.gradle.kts",
    "library-storage/build.gradle.kts",
    "subtitle-engine/build.gradle.kts",
    "media-tools/build.gradle.kts",
    "ui-presentation/build.gradle.kts",
    "benchmarks/build.gradle.kts",
)
MEDIA3_OPT_IN_MODULES = (
    "app/build.gradle.kts",
    "playback-engine/build.gradle.kts",
    "media-tools/build.gradle.kts",
)

errors: list[str] = []

for rel in ANDROID_BUILD_FILES:
    text = (ROOT / rel).read_text(encoding="utf-8")
    if "libs.plugins.kotlin.android" in text or "org.jetbrains.kotlin.android" in text:
        errors.append(f"{rel}: legacy kotlin-android plugin is applied")
    if "kotlinOptions" in text:
        errors.append(f"{rel}: legacy kotlinOptions DSL is still present")

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if "libs.plugins.kotlin.android" in root_build or "org.jetbrains.kotlin.android" in root_build:
    errors.append("build.gradle.kts: legacy kotlin-android plugin is still declared")

catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
if re.search(r"(?m)^\s*kotlin-android\s*=", catalog) or "org.jetbrains.kotlin.android" in catalog:
    errors.append("gradle/libs.versions.toml: kotlin-android plugin alias is still declared")

properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
if re.search(r"(?m)^\s*android\.builtInKotlin\s*=\s*false\s*$", properties):
    errors.append("gradle.properties: android.builtInKotlin=false opts out of the required AGP 9 mode")

for rel in MEDIA3_OPT_IN_MODULES:
    text = (ROOT / rel).read_text(encoding="utf-8")
    if "compilerOptions" not in text:
        errors.append(f"{rel}: Media3 compiler opt-in was not migrated to compilerOptions")
    if 'optIn.add("androidx.media3.common.util.UnstableApi")' not in text:
        errors.append(f"{rel}: Media3 UnstableApi opt-in is missing")

if errors:
    print("AGP 9 built-in Kotlin configuration gate FAILED:")
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

print("AGP 9 built-in Kotlin configuration gate passed.")
