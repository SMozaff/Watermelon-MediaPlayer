# Kotlin migration signatures and baseline discovery

**Repository:** `SMozaff/Watermelon-MediaPlayer`  
**Audited ref:** `main` at `da0bfe6b9d842b2a487613ac51eb25508b164653`  
**Scope:** the twelve planned Rust-port candidates, their interfaces and direct call paths, plus the Stage 1 supporting coordinator, app wiring, settings, scripts, and tests.

This document records the source as read. It does not change production behavior or assert that the proposed Rust API in the migration blueprint is final.

> Rust-only update (2026-11-23): this is a historical audit of the ten Kotlin ports that were
> deleted under D-7/D-8. Call-site tables below describe the pre-cutover tree. The current seams are
> `common-interfaces/.../SubtitlePorts.kt` and the UniFFI adapters in
> `app/.../migration/RustAdapters.kt`; the frozen vectors in `testdata/golden/` remain the oracle.

## 1. Contracts and shared types

Source: `common-interfaces/src/main/kotlin/com/watermelon/common/subtitle/sync/SubtitleSyncContracts.kt`

### Data and sealed types

| Type | Exact constructor/property types and behavior |
|---|---|
| `ActivitySignature` | `data class ActivitySignature(val startMs: Long, val bucketMs: Int, val occupancy: FloatArray, val onsets: FloatArray)`. Init requires `bucketMs > 0` and equal array sizes; both failures are Kotlin `require` failures (`IllegalArgumentException`). `durationMs: Long` is computed as `occupancy.size.toLong() * bucketMs`. |
| `SubtitleSyncModel` | Sealed interface: `data object Identity`; `data class Offset(val offsetMs: Long)`; `data class Affine(val scale: Double, val offsetMs: Long)`; `data class Piecewise(val anchors: List<SyncAnchor>)`. |
| `SyncAnchor` | `data class SyncAnchor(val subtitleTimeMs: Long, val mediaTimeMs: Long)`. |
| `SubtitleSyncRequest` | `mediaId: String`, `mediaUri: String`, `mediaFileSize: Long`, `mediaDurationMs: Long`, `subtitleFingerprint: String`, `subtitleLanguage: String?`, `subtitle: ParsedSubtitle`, `playbackSessionId: Long`. No default parameters. |
| `SyncMeasurement` | `subtitleProbeStartMs: Long`, `audioProbeStartMs: Long`, `estimatedOffsetMs: Long`, `correlationScore: Float`, `peakMargin: Float`, `speechCoverage: Float`, `transitionCount: Int`, `probeQuality: Float`. |
| `SubtitleSyncResult` | Sealed interface: `Synchronized(model: SubtitleSyncModel, confidence: Float, measurements: List<SyncMeasurement>)`; `ComplexDriftDetected(measurements)`; `LowConfidence(measurements)`; objects `Unsupported`, `ResourceDenied`, `Cancelled`; and `Failed(reason: String)`. |
| `SyncSource` | Enum values `AUTO`, `MANUAL`. |
| `SyncStatus` | Enum values `IDLE`, `CHECKING_CACHE`, `ANALYZING`, `SYNCHRONIZED`, `LOW_CONFIDENCE`, `COMPLEX_DRIFT`, `UNSUPPORTED`, `RESOURCE_DENIED`, `FAILED`. |
| `SubtitleSyncProfile` | `mediaId: String`, `mediaFileSize: Long`, `subtitleFingerprint: String`, `subtitleLanguage: String? = null`, `autoModel: SubtitleSyncModel? = null`, `autoConfidence: Float? = null`, `autoEngineVersion: Int? = null`, `autoUpdatedAt: Long? = null`, `manualOffsetMs: Long? = null`, `manualUpdatedAt: Long? = null`. |
| `ProbeCandidate` | `subtitleStartMs: Long`, `durationMs: Long`, `distinctiveness: Float`. |
| `CorrelationConfig` | Required `minOffsetMs: Long`, `maxOffsetMs: Long`, `stepMs: Long`; defaults: `onsetWeight: Float = 0.60f`, `occupancyWeight: Float = 0.35f`, `silenceWeight: Float = 0.05f`, `independentPeakDistanceMs: Long = 1_000L`. No init validation in this type. |
| `CorrelationResult` | `offsetMs: Long`, `peakScore: Float`, `secondPeakScore: Float`, `peakMargin: Float`. |

Additional public methods on `SubtitleSyncProfile`:

- `effectiveModel(): SubtitleSyncModel`: manual offset wins, then auto model, then `Identity`.
- `effectiveOffsetMs(): Long`: Identity and Piecewise return zero; Offset and Affine return their `offsetMs`.

### Interface signatures

- `SubtitleActivityBuilder.build(subtitle: ParsedSubtitle, startMs: Long, endMs: Long, bucketMs: Int): ActivitySignature`
- `SubtitleProbeSelector.select(subtitle: ParsedSubtitle, mediaDurationMs: Long, maxCount: Int): List<ProbeCandidate>`
- `ActivityCorrelator.correlate(audio: ActivitySignature, subtitleContext: ActivitySignature, config: CorrelationConfig): CorrelationResult`
- `SpeechProbeSource.suspend probe(mediaUri: String, targetPositionMs: Long, durationMs: Long, bucketMs: Int): SpeechProbeResult`
- `SubtitleSyncRepository.suspend get(mediaId: String, mediaFileSize: Long, subtitleFingerprint: String): SubtitleSyncProfile?`
- `SubtitleSyncRepository.suspend saveAuto(mediaId: String, mediaFileSize: Long, subtitleFingerprint: String, subtitleLanguage: String?, model: SubtitleSyncModel, confidence: Float, engineVersion: Int): Unit`
- `SubtitleSyncRepository.suspend setManualOffset(mediaId: String, mediaFileSize: Long, subtitleFingerprint: String, offsetMs: Long): Unit`
- `SubtitleSyncRepository.suspend clearManualOffset(mediaId: String, mediaFileSize: Long, subtitleFingerprint: String): Unit`
- `SubtitleSyncRepository.suspend clearAutoResult(mediaId: String, mediaFileSize: Long, subtitleFingerprint: String): Unit`

`SpeechProbeResult` is a sealed interface with `Success(signature: ActivitySignature, actualStartMs: Long, actualDurationMs: Long)`, objects `Unsupported`, `ResourceDenied`, and `Cancelled`, plus `Failure(reason: String)`.

## 2. The twelve planned port candidates

### M-01: `ActivityCorrelatorImpl.kt`

- Public declaration: `class ActivityCorrelatorImpl : ActivityCorrelator`.
- Public override: `correlate(audio: ActivitySignature, subtitleContext: ActivitySignature, config: CorrelationConfig): CorrelationResult`.
- Private helpers: `scoreAtOffset(audio: ActivitySignature, subtitle: ActivitySignature, offsetMs: Long, config: CorrelationConfig): Float`; `normalizeSigned(value: Float, minValue: Float, maxValue: Float): Float`; `floorToStep(value: Long, step: Long): Long`; `ceilToStep(value: Long, step: Long): Long`.
- Numeric behavior: timestamps, offsets, bucket-relative times and loop offsets are `Long`; bucket indexes are `Int`; occupancy, onset, weighted samples and scores are `Float`; candidate scores are `Pair<Long, Float>`.
- Explicit exceptions: `require` checks equal bucket sizes, `maxOffsetMs >= minOffsetMs`, and a step that is at least one bucket and an exact bucket multiple. These throw `IllegalArgumentException`. No fallback result is returned for invalid preconditions.
- Notable defaults live in `CorrelationConfig`, not in this implementation.

### M-02: `OffsetConsensus.kt`

- Public declaration: `class OffsetConsensus(private val config: SubtitleSyncConfig = SubtitleSyncConfig())`.
- Public methods: `resolve(measurements: List<SyncMeasurement>): SubtitleSyncResult`; `hasUsefulProvisionalOffset(measurements: List<SyncMeasurement>): Boolean`.
- Private methods: `isEligible(it: SyncMeasurement): Boolean`; `weightedMedian(measurements: List<SyncMeasurement>): Long`; `isLikelyLinearDrift(measurements: List<SyncMeasurement>): Boolean`.
- Numeric behavior: offsets and probe positions are `Long`; eligible counts and indexes are `Int`; confidence, thresholds and peak values are `Float`; the weighted-median total/running weights, means, sums of squares, regression slope/intercept, R-squared and predicted drift calculations use `Double`.
- No explicit `require` or declared exception contract. It returns `LowConfidence` when there are too few eligible measurements or evidence fails its thresholds. The sort operations use Kotlin `sortedBy`.

### M-03: `SubtitleProbeSelectorImpl.kt`

- Public declaration: `class SubtitleProbeSelectorImpl(private val config: SubtitleSyncConfig = SubtitleSyncConfig()) : SubtitleProbeSelector`.
- Public override: `select(subtitle: ParsedSubtitle, mediaDurationMs: Long, maxCount: Int): List<ProbeCandidate>`.
- Private methods: `scoreWindow(subtitle: ParsedSubtitle, start: Long, duration: Long, mediaDurationMs: Long, edgeGuard: Long): ProbeCandidate?`; `normalizedGapEntropy(gaps: List<Long>): Float`; `looksNonDialogue(text: String): Boolean`.
- Numeric behavior: cue/window times, duration and gaps are `Long`; counts and indexes are `Int`; entropy, logarithm calculations, probabilities and means use `Double`; occupancy, transition quality, distinctiveness and returned score are `Float`.
- Empty cues, non-positive media duration or non-positive max count return an empty list. The implementation has no explicit `require` checks. It uses `sortedByDescending` and `distinct`.

### M-04: `SubtitleActivityBuilderImpl.kt`

- Public declaration: `class SubtitleActivityBuilderImpl(private val config: SubtitleSyncConfig = SubtitleSyncConfig()) : SubtitleActivityBuilder`.
- Public override: `build(subtitle: ParsedSubtitle, startMs: Long, endMs: Long, bucketMs: Int): ActivitySignature`.
- Internal helpers: `isLikelyNonDialogue(cue: SubtitleCue): Boolean`; `isLikelyNonDialogue(text: String): Boolean`.
- Numeric behavior: cue and window timestamps are `Long`; bucket size, bucket count, blur bucket count, indexes and blur-loop displacement are `Int`; divisions used for bucket sizing pass through `Double`; occupancy arrays, onset arrays, weights and kernels are `Float`.
- Explicit exceptions: `require(endMs > startMs)` and `require(bucketMs > 0)`, both `IllegalArgumentException`.

### M-05: `SubtitleTimeMapper.kt`

- Public interface: `SubtitleTimeMapper.map(originalTimestampMs: Long): Long`.
- Public implementation: `SyncModelTimeMapper(private val model: SubtitleSyncModel) : SubtitleTimeMapper`.
- Private helper: `mapPiecewise(timeMs: Long, model: SubtitleSyncModel.Piecewise): Long`.
- Numeric behavior: timestamps/anchor values/offsets/spans are `Long`; affine scale and affine interpolation ratio are `Double`; affine and piecewise interpolated values are rounded with Kotlin `roundToLong()`. Final mapped output is clamped with `coerceAtLeast(0L)`.
- No explicit validation or declared exceptions. The mapper handles empty and single-anchor Piecewise models without throwing by using identity or a constant anchor offset; multiple anchors use sorted order and linear interpolation.

### M-06: `LinearDriftCorrector.kt`

- Public declaration: `class LinearDriftCorrector`.
- Nested public data class: `Cue(startMs: Long, endMs: Long, text: String)`.
- Public methods: `apply(cues: List<Cue>, offsetMs: Long): List<Cue>`; `applyToTimestamp(timestampMs: Long, offsetMs: Long): Long`.
- Numeric behavior: times and offset are `Long`; shifted cue fields are computed with Long addition and clamped at zero.
- No explicit validation or declared exceptions. Kotlin Long arithmetic is used as written; no overflow guard is present.

### M-07: `SubtitleSyncConfig.kt`

- Public data class constructor defaults:

| Property | Type | Default |
|---|---|---:|
| `bucketMs` | `Int` | `40` |
| `probeDurationMs` | `Long` | `8_000L` |
| `maxProbeCount` | `Int` | `3` |
| `primarySearchMs` | `Long` | `30_000L` |
| `expandedSearchMs` | `Long` | `180_000L` |
| `expandedSearchStepMs` | `Long` | `200L` |
| `refinementRadiusMs` | `Long` | `2_000L` |
| `subtitleOnsetBlurMs` | `Long` | `200L` |
| `minimumTransitions` | `Int` | `3` |
| `maximumConsensusSpreadMs` | `Long` | `300L` |
| `identityToleranceMs` | `Long` | `150L` |
| `autoApplyThreshold` | `Float` | `0.86f` |
| `minimumGoodProbes` | `Int` | `2` |
| `minimumProbeQuality` | `Float` | `0.50f` |
| `minimumCorrelationScore` | `Float` | `0.40f` |
| `minimumPeakMargin` | `Float` | `0.035f` |
| `sdhWeight` | `Float` | `0.25f` |

- Init requirements: positive `bucketMs`, positive `probeDurationMs`, `maxProbeCount in 1..8`, positive `primarySearchMs`, `expandedSearchMs >= primarySearchMs`, `expandedSearchStepMs >= bucketMs`, and `autoApplyThreshold in 0f..1f`. Each uses `require` and therefore throws `IllegalArgumentException`.
- Top-level constant: `const val SUBTITLE_SYNC_ENGINE_VERSION = 1` (`Int`).

### M-08: `SubtitleSyncContracts.kt`

- All shared contracts and model types are listed in Section 1.
- The only constructor validation in these contracts is `ActivitySignature.init`. The remaining data classes and sealed data variants do not add explicit `require` checks in this file.
- Numeric type preservation matters: `FloatArray` values are used by the correlator and signatures; `Double` is used only for affine scale; times and byte/file-size-like identifiers are `Long`; counts and engine version are `Int`.

### M-09: `SpeechLikelihoodAccumulator.kt`

- Internal enum: `PcmSampleEncoding(val bytesPerSample: Int)` with `PCM_16(2)` and `PCM_FLOAT(4)`.
- Internal class constructor: `SpeechLikelihoodAccumulator(private val targetStartUs: Long, durationMs: Long, private val bucketMs: Int)`.
- Property: `decodedSampleCount: Long` with public getter and private setter.
- Methods: `consume(buffer: ByteBuffer, bufferOffset: Int, bufferSize: Int, presentationTimeUs: Long, sampleRate: Int, channelCount: Int, encoding: PcmSampleEncoding): Unit`; `finish(): ActivitySignature`.
- Numeric behavior: times are `Long`; rate/channel/buffer/bucket/frame/index/count-per-loop values are `Int` where declared; bucket and sample counters/zero-crossings are Long arrays; sample sums, prior samples, RMS, derivative RMS, ZCR, ratios, logarithms and feature scores are `Double`; the emitted occupancy/onset arrays are `FloatArray`.
- The accumulator computes bucket count using `ceil(durationMs.toDouble() / bucketMs).toInt().coerceAtLeast(1)`. The percentile expression is exactly `validRms[((validRms.size - 1) * 0.20).toInt()]`.
- Byte interpretation is `ByteOrder.nativeOrder()`; PCM16 uses `getShort(index) / 32768.0`, float PCM uses `getFloat(index).toDouble().coerceIn(-1.0, 1.0)`.
- There is no constructor `require`. `consume` returns for non-positive sample rate, channel count or buffer size; it also skips invalid frame bounds. Absolute ByteBuffer reads remain dependent on the computed bounds check.

### M-10: `SrtParser.kt`

- Singleton: `object SrtParser`.
- Public method: `parse(content: String, language: String? = null): ParsedSubtitle`.
- Private extension: `MatchResult.toMs(): Long`.
- Numeric behavior: hours/minutes/seconds/milliseconds parse through `String.toLong()`; assembled cue timestamps are `Long`; parser cue index is `Int`.
- No explicit `require`. Malformed blocks, absent timing lines, fewer than two timestamp matches and blank bodies are skipped. The function returns a `ParsedSubtitle` even when no valid cues remain.
- It removes a leading BOM, normalizes CRLF and CR to LF, splits blocks with `\n[ \t]*\n`, trims each line, strips matching HTML-like tags, runs bidi formatting, and sorts returned cues by start time. TIME uses the source regex `(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})`.

### M-11: `BidiFormatter.kt`

- Singleton: `object BidiFormatter`.
- Public methods: `isRtl(text: String): Boolean`; `format(text: String, baseRtl: Boolean): String`.
- Private enum: `Dir { RTL, LTR, NEUTRAL }`; private method: `directionOf(ch: Char): Dir`.
- Numeric behavior: Unicode code points are inspected as Kotlin `Char.code` (`Int`); RTL/LTR counts and string indexes are `Int`; output uses `StringBuilder`.
- No explicit validation or declared exceptions. It inserts U+2068 FSI and U+2069 PDI around maximal runs classified opposite to the base direction. Its implementation classifies Hebrew, Arabic and presentation-form ranges as RTL; ASCII Latin letters/digits and U+00C0..U+024F are LTR; other chars are neutral.
- Suspected comment/implementation mismatch recorded in the Stage 1 report: the method comment says trailing neutral characters are trimmed back out of isolates, but the loop consumes neutral characters into the isolate and does not move trailing whitespace back out. A test added in this stage pins the observed behavior without modifying production code.

### M-12: `OpenSubtitlesHasher.kt`

- Singleton: `object OpenSubtitlesHasher`.
- Public overloads: `hash(file: File): String`; `hash(fd: FileDescriptor, fileSize: Long): String`.
- Private methods: `hash(channel: FileChannel, fileSize: Long): String`; `sumChunk(channel: FileChannel, offset: Long): Long`; `toHex(value: Long): String`.
- Constant: `CHUNK_SIZE = 64 * 1024` (`Int`).
- Numeric behavior: file sizes and chunk offsets are `Long`; hash and word sums are `Long`; 64 KiB / 8-byte iteration counts and ByteBuffer positions are `Int`; words are read in little-endian order. Long addition wraps naturally. Formatting is `String.format("%016x", value)`.
- Both public overloads require a file size of at least 64 KiB, otherwise throwing `IllegalArgumentException`. File/channel open/read failures can propagate as I/O failures. The FileDescriptor overload wraps it in `FileInputStream(fd).channel`; its documentation assigns descriptor ownership/closure to the caller.

## 3. Supporting orchestration, cancellation and wiring

### `SubtitleSyncCoordinator.kt`

Constructor dependencies and defaults:

- `repository: SubtitleSyncRepository`
- `probeSelector: SubtitleProbeSelector`
- `subtitleActivityBuilder: SubtitleActivityBuilder`
- `speechProbeSource: SpeechProbeSource`
- `correlator: ActivityCorrelator`
- `consensus: OffsetConsensus`
- `config: SubtitleSyncConfig = SubtitleSyncConfig()`

Public entry point: `suspend synchronize(request: SubtitleSyncRequest): SubtitleSyncResult`.

The method calls `currentCoroutineContext().ensureActive()` at entry and again at the beginning of each candidate-loop iteration. The Android-bound `SpeechProbeSource.probe` is suspend and is responsible for its own decoding/cancellation. `SparseSpeechProbeSource` checks `coroutineContext.ensureActive()` before decoding and during its output/decode loop. The coordinator does not wrap its method body in a catch for cancellation. The app-level `SubtitleSyncEngine.trigger` runs the operation inside its supplied scope and uses `runCatching` to map thrown failures to `SubtitleSyncResult.Failed`; it also drops any result whose captured playback session no longer matches the current session.

The coordinator applies manual stored offsets before cached auto results, then selects probes. The primary search uses `primarySearchMs` on both sides at `bucketMs` steps. If the primary correlation is not strong, the same decoded audio is searched over `expandedSearchMs` at `expandedSearchStepMs`, then refined by `refinementRadiusMs` at `bucketMs` steps. Accepted results are persisted through `SubtitleSyncRepository.saveAuto`.

### App construction: `AppDependencies.kt`

- `internal class AppDependencies(context: Context, app: WatermelonApplication, scope: CoroutineScope)`.
- Components are lazily created with Kotlin `by lazy`.
- `subtitleFingerprintProvider` constructs `SubtitleFingerprintProvider()`.
- `subtitleSyncRepository` constructs `SubtitleSyncRepositoryImpl(database)`.
- `subtitleSyncCoordinator` constructs `SubtitleSyncCoordinator` with `SubtitleProbeSelectorImpl()`, `SubtitleActivityBuilderImpl()`, `SparseSpeechProbeSource(appContext)`, `ActivityCorrelatorImpl()` and `OffsetConsensus()`.
- `subtitleSyncEngine` constructs `SubtitleSyncEngine(scope, fingerprintProvider, repository, coordinator)`.
- No feature-flag/shadow-mode construction or runtime choice is present in this composition root.

### App execution: `SubtitleSyncEngine.kt`

- Internal class with constructor dependencies `scope: CoroutineScope`, `SubtitleFingerprintProvider`, `SubtitleSyncRepositoryImpl` and `SubtitleSyncCoordinator`.
- Compose state: `offsetMs` is `Long`; `status` is `SyncStatus`; private `session` is `Long`.
- Methods: `beginSession(): Unit`, `restoreOffset(offset: Long): Unit`, `suspend storedOffsetFor(mediaUri: String, fileSize: Long, subtitle: ParsedSubtitle): Long?`, `trigger(mediaUri: String, mediaItem: MediaItem?, subtitle: ParsedSubtitle, durationMs: Long): Unit`, `nudge(mediaUri: String, mediaItem: MediaItem?, subtitle: ParsedSubtitle, deltaMs: Long): Unit`.
- `trigger` captures the session before launching; a completed result is discarded if playback session changes. Coordinator exceptions inside the launch are converted to `Failed` using `runCatching`.

### Settings: `SettingsPersistence.kt`

Settings are stored in Android `SharedPreferences`, not DataStore. `loadSettingsState(prefs: SharedPreferences): SettingsState` reads each field using its defined fallback; `saveSettingsState(prefs: SharedPreferences, state: SettingsState): Unit` writes the state in a single `edit().apply()`. `readThemeMode(prefs: SharedPreferences): ThemeMode` performs legacy `pure_dark` to `theme_mode` conversion and removes the old key after writing the new one. Auto-sync is the boolean key `subtitle_auto_sync_enabled`, defaulting to `true`. This file contains no Kotlin/Rust implementation selector.

## 4. Object call sites and internal visibility

### Singleton objects have no existing interface seam

| Object | Call sites found in tracked Kotlin sources |
|---|---|
| `SrtParser` | `SubtitleRepositoryImpl.kt`: parse downloaded SRT and parse cached SRT (two call sites); `LocalSidecarSourceImpl.kt`: parse sidecar SRT (one call site). |
| `BidiFormatter` | `SrtParser.kt`: `isRtl(clean)` and `format(line, rtl)`. |
| `OpenSubtitlesHasher` | `SubtitleRepositoryImpl.kt`: `defaultHashFor(context, mediaItem)` calls `hash(fileDescriptor, mediaItem.fileSize)`. |

Each is declared as a Kotlin `object`, not an interface implementation. Moving these functions behind Rust therefore requires a deliberate new seam or an adapter call site; no existing interface currently provides it.

### Internal media-tools types

- `SpeechLikelihoodAccumulator` and `PcmSampleEncoding` are both `internal` in `media-tools`.
- Production use is in `SparseSpeechProbeSource.kt`, which constructs the accumulator and selects PCM16 vs PCM_FLOAT encoding.
- The test use is in `SpeechLikelihoodAccumulatorTest.kt`, which constructs the accumulator and uses `PcmSampleEncoding.PCM_16`.
- The benchmark test added in Stage 1 is in the same `media-tools` module/test source set and is also allowed to access internal declarations.

## 5. What `verify_interfaces.py` actually enforces

The script reads filenames from exactly these directories:

- `common-interfaces/src/main/kotlin/com/watermelon/common/repository`
- `common-interfaces/src/main/kotlin/com/watermelon/common/controller`

It recursively searches implementation files in exactly:

- `library-storage`
- `subtitle-engine`
- `playback-engine`

A file is counted when its filename ends in `Impl.kt` or `ControllerImpl.kt`. The script removes `.kt` and then replaces the substring `Impl` with an empty string to derive the implementation basename. It compares the interface file basenames with this set. There is no allow-list.

Limitations visible in the implementation: it does not parse Kotlin declarations or verify that a discovered class actually implements the matching interface; it only compares filename-derived names. It does not scan `common-interfaces/.../subtitle/sync`, nor does it scan `media-tools`. Therefore the Stage 1 subtitle-sync contracts in `common-interfaces/.../subtitle/sync` are not guarded by this script as it currently stands. No changes were made to the script.

`verify_no_hardcoded_strings.py` is a separate string-literal ratchet over the `ui-presentation` and `app` Compose/app source trees. It excludes tests, checks a committed JSON baseline and fails only when a scanned file adds more user-facing string literals. It is out of the Stage 1 change scope and was read but not modified.

## 6. Existing test coverage and direct gaps

### `SubtitleSyncCoreTest.kt`

The file contains tests for positive and negative constant-offset recovery, consensus agreement, linear-drift classification, conflicting consensus evidence, subtitle sound-description weighting, fingerprint stability/timing sensitivity, adaptive second-probe behavior with accepted-result persistence, and manual-profile precedence without audio probing.

Direct coverage present: correlator positive/negative offset scenarios; consensus accepted/drift/conflict scenarios; activity-builder SDH weighting; fingerprint behavior; coordinator end-to-end mocked probe behavior.

No direct test in this file for: `SubtitleProbeSelectorImpl.select` scoring/spacing/short-media fallback; `SubtitleTimeMapper` Identity/Offset/Affine/Piecewise mapping and rounding boundaries; `LinearDriftCorrector`; `SubtitleSyncConfig` validation/default boundaries; most `ActivitySignature` constructor validation; correlator invalid configurations, no-overlap, tie and exact-boundary cases; or the full coordinator cancellation/error/result matrix.

### `SpeechLikelihoodAccumulatorTest.kt`

One direct test synthesizes 8 seconds of 16 kHz mono PCM16 with silence and speech-like bursts, then checks low occupancy in silence, sufficient active occupancy and onset count.

Not directly covered by that test: PCM_FLOAT, multi-channel downmix, non-native byte order, buffers split across calls/bucket boundaries, partial frames, invalid sample rate/channel/buffer-size behavior, no-data completion, clipping/edge cases, and deterministic numeric outputs over reference vectors.

### `SubtitleRepositoryImplTest.kt`

The tests cover offline-first/cache behavior, sidecar and cache lookups, provider configuration and search/error mapping, download URL validation, HTTP status and content-size rejection, valid and cue-less downloaded SRT, and cache reopen/isolation behavior. The valid and invalid SRT cases exercise the parser indirectly through repository calls.

Not directly covered: parser-specific BOM/newline/block/timestamp-width/separator/tag/malformed-timing and Persian/Arabic behavior; bidi direction and isolate boundaries; or OpenSubtitles hash output, byte order, high-bit wrap, exact 64 KiB handling and FileDescriptor semantics. The repository test supplies a `hashFor` test lambda, so it does not invoke the production hasher.

### Direct-test inventory across the twelve files

| Candidate | Direct test evidence found |
|---|---|
| M-01 `ActivityCorrelatorImpl` | Yes, positive and negative offset tests; boundary/precondition cases absent. |
| M-02 `OffsetConsensus` | Yes, agreement, drift and conflict tests; small sample counts and threshold boundaries are not directly covered. |
| M-03 `SubtitleProbeSelectorImpl` | No direct test found. |
| M-04 `SubtitleActivityBuilderImpl` | Yes, SDH down-weighting; cue/window boundaries and malformed ranges are not directly covered. |
| M-05 `SyncModelTimeMapper` / `SubtitleTimeMapper` | No direct test found. |
| M-06 `LinearDriftCorrector` | No direct test found. |
| M-07 `SubtitleSyncConfig` | No direct test found for constructor validation/default boundaries. |
| M-08 `SubtitleSyncContracts.kt` | No dedicated contract test found; a few contract values are exercised by the core tests. |
| M-09 `SpeechLikelihoodAccumulator` | One PCM16 mono synthetic-burst test; substantial format/boundary cases absent. |
| M-10 `SrtParser` | No direct test found; only indirect basic valid/invalid repository paths. |
| M-11 `BidiFormatter` | No direct test found before this stage; one characterization test added in Stage 1. |
| M-12 `OpenSubtitlesHasher` | No direct test found; repository tests inject a hash stub. |

## 7. Stage 1 benchmark shape derived from source

The benchmark harness uses only existing JUnit APIs. The workload dimensions are derived from current source values:

- One selected audio probe is `probeDurationMs = 8_000L` with `bucketMs = 40`, therefore 200 audio buckets.
- The selector defaults to at most 3 probes.
- Primary correlation searches +/- 30,000 ms with 40 ms steps: 1,501 candidate offsets. The synthetic subtitle context is 68,000 ms / 40 ms = 1,700 buckets.
- Expanded correlation searches +/- 180,000 ms with 200 ms steps: 1,801 candidate offsets. Its context is 368,000 ms / 40 ms = 9,200 buckets.
- Refinement searches +/- 2,000 ms at 40 ms steps: 101 candidate offsets.
- Consensus is measured with three and five `SyncMeasurement` instances, matching the requested representative counts.
- Parser input contains exactly 2,000 generated SRT cues.
- Accumulator input is an 8-second, 16 kHz, mono, PCM16 buffer, matching the shape already used in `SpeechLikelihoodAccumulatorTest`.

The harness is opt-in with `watermelon.benchmarks=true`; it is skipped by default. Detailed machine/JVM limitation and benchmark execution status are in [baseline-benchmarks.md](baseline-benchmarks.md).
