package com.watermelon.subtitle.golden

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SyncAnchor
import com.watermelon.common.subtitle.sync.SyncMeasurement
import com.watermelon.common.subtitle.sync.ProbeCandidate
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.subtitle.bidi.BidiFormatter
import com.watermelon.subtitle.hash.OpenSubtitlesHasher
import com.watermelon.subtitle.parser.SrtParser
import com.watermelon.subtitle.sync.ActivityCorrelatorImpl
import com.watermelon.subtitle.sync.LinearDriftCorrector
import com.watermelon.subtitle.sync.OffsetConsensus
import com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl
import com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl
import com.watermelon.subtitle.sync.SyncModelTimeMapper
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/** Generates nine of the ten golden-vector files from the current Kotlin implementations. */
class SubtitleGoldenVectorGeneratorTest {

    @Test
    fun generate() {
        assumeTrue(GoldenJson.enabled())
        val dir = GoldenJson.dir()

        GoldenJson.write(dir, "srt_parse", GoldenJson.file("srt_parse", srtCases()))
        GoldenJson.write(dir, "bidi_format", GoldenJson.file("bidi_format", bidiCases()))
        GoldenJson.write(dir, "hash_opensubtitles", GoldenJson.file("hash_opensubtitles", hashCases()))
        GoldenJson.write(dir, "time_mapper", GoldenJson.file("time_mapper", timeMapperCases()))
        GoldenJson.write(dir, "drift_corrector", GoldenJson.file("drift_corrector", driftCases()))
        GoldenJson.write(dir, "correlator", GoldenJson.file("correlator", correlatorCases()))
        GoldenJson.write(dir, "consensus", GoldenJson.file("consensus", consensusCases()))
        GoldenJson.write(dir, "probe_select", GoldenJson.file("probe_select", probeCases()))
        GoldenJson.write(dir, "activity_builder", GoldenJson.file("activity_builder", activityCases()))
    }

    private fun srtCases(): List<String> {
        val inputs = listOf(
            "bom_and_crlf" to "\uFEFF1\r\n00:00:01,000 --> 00:00:02,000\r\nfirst\r\n",
            "dot_fraction_and_suffix" to "00:00:01.000 --> 00:00:02.000 align:start\nsecond\n",
            "rtl_mixed" to "00:00:01,000 --> 00:00:02,000\nسلام hello\n",
            "html_tags" to "00:00:03,000 --> 00:00:04,000\n<i>hello</i> <b>world</b>\n",
            "malformed_skipped" to "00:00:05,000 --> not-a-time\nbroken\n",
            "sorted_with_original_indices" to
                "00:00:05,000 --> 00:00:06,000\nlater\n\n00:00:01,000 --> 00:00:02,000\nearlier\n",
        )
        return inputs.map { (id, content) ->
            val parsed = SrtParser.parse(content, if (id == "rtl_mixed") "fa" else null)
            GoldenJson.case(
                id,
                GoldenJson.obj("content" to GoldenJson.str(content), "language" to GoldenJson.nullableStr(parsed.language)),
                GoldenJson.obj(
                    "language" to GoldenJson.nullableStr(parsed.language),
                    "cues" to GoldenJson.arr(parsed.cues.map(::cueJson)),
                ),
            )
        }
    }

    private fun bidiCases(): List<String> {
        val inputs = listOf(
            Triple("pure_ltr", "Watermelon 123", false),
            Triple("pure_rtl", "سلام دنیا", true),
            Triple("embedded_ltr", "سلام hello", true),
            Triple("embedded_rtl", "hello سلام", false),
            Triple("trailing_neutral", "שלום abc ", true),
            Triple("neutral_attach", "سلام! 123", false),
            Triple("empty", "", true),
        )
        return inputs.map { (id, text, baseRtl) ->
            GoldenJson.case(
                id,
                GoldenJson.obj("text" to GoldenJson.str(text), "baseRtl" to GoldenJson.bool(baseRtl)),
                GoldenJson.obj(
                    "isRtl" to GoldenJson.bool(BidiFormatter.isRtl(text)),
                    "formatted" to GoldenJson.str(BidiFormatter.format(text, baseRtl)),
                ),
            )
        }
    }

    private fun hashCases(): List<String> = listOf(
        hashCase("zeros_128k", 131_072) {},
        hashCase("head_and_tail_words", 131_072) { bytes ->
            bytes[0] = 1
            bytes[65_536] = 2
        },
        hashCase("high_bit_head", 131_072) { bytes -> bytes[7] = 0x80.toByte() },
        hashCase("zeros_256k", 262_144) {},
    )

    private fun hashCase(id: String, size: Int, pattern: (ByteArray) -> Unit): String {
        val bytes = ByteArray(size)
        pattern(bytes)
        val file = Files.createTempFile("wm-golden-hash-", ".bin").toFile()
        try {
            file.writeBytes(bytes)
            return GoldenJson.case(
                id,
                GoldenJson.obj("size" to GoldenJson.num(size.toLong()), "pattern" to GoldenJson.str(id)),
                GoldenJson.obj("hash" to GoldenJson.str(OpenSubtitlesHasher.hash(file))),
            )
        } finally {
            file.delete()
        }
    }

    private fun timeMapperCases(): List<String> {
        val rows = listOf(
            Triple("identity", SubtitleSyncModel.Identity, 1_234L),
            Triple("negative_clamped", SubtitleSyncModel.Identity, -5L),
            Triple("offset", SubtitleSyncModel.Offset(2_000L), 1_000L),
            Triple("affine_rounding", SubtitleSyncModel.Affine(1.5, 100L), 1L),
            Triple("piecewise_empty", SubtitleSyncModel.Piecewise(emptyList<SyncAnchor>()), 500L),
            Triple(
                "piecewise_interpolated",
                SubtitleSyncModel.Piecewise(
                    listOf(SyncAnchor(1_000L, 2_000L), SyncAnchor(0L, 0L)),
                ),
                500L,
            ),
        )
        return rows.map { (id, model, timestampMs) ->
            GoldenJson.case(
                id,
                GoldenJson.obj("model" to modelJson(model), "timestampMs" to GoldenJson.num(timestampMs)),
                GoldenJson.obj("mappedMs" to GoldenJson.num(SyncModelTimeMapper(model).map(timestampMs))),
            )
        }
    }

    private fun driftCases(): List<String> {
        val cues = listOf(
            LinearDriftCorrector.Cue(1_000L, 2_000L, "first"),
            LinearDriftCorrector.Cue(3_000L, 4_000L, "second"),
        )
        val shifted = LinearDriftCorrector().apply(cues, 500L)
        val clamped = LinearDriftCorrector().apply(cues, -5_000L)
        return listOf(
            GoldenJson.case(
                "positive_offset",
                GoldenJson.obj("cues" to GoldenJson.arr(cues.map(::correctorCueJson)), "offsetMs" to GoldenJson.num(500L)),
                GoldenJson.obj("cues" to GoldenJson.arr(shifted.map(::correctorCueJson))),
            ),
            GoldenJson.case(
                "clamped_at_zero",
                GoldenJson.obj("cues" to GoldenJson.arr(cues.map(::correctorCueJson)), "offsetMs" to GoldenJson.num(-5_000L)),
                GoldenJson.obj("cues" to GoldenJson.arr(clamped.map(::correctorCueJson))),
            ),
            GoldenJson.case(
                "single_timestamp",
                GoldenJson.obj("timestampMs" to GoldenJson.num(1_000L), "offsetMs" to GoldenJson.num(-2_000L)),
                GoldenJson.obj("mappedMs" to GoldenJson.num(LinearDriftCorrector().applyToTimestamp(1_000L, -2_000L))),
            ),
        )
    }

    private fun correlatorCases(): List<String> {
        val audio = signature(1_000L, 40, 20)
        val config = CorrelationConfig(
            minOffsetMs = -400L,
            maxOffsetMs = 400L,
            stepMs = 40L,
            onsetWeight = 0.60f,
            occupancyWeight = 0.35f,
            silenceWeight = 0.05f,
        )
        return listOf(400L, -400L, 0L).map { shift ->
            val context = signature(1_000L - shift, 40, 20)
            val result = ActivityCorrelatorImpl().correlate(audio, context, config)
            GoldenJson.case(
                "shift_${shift}",
                GoldenJson.obj(
                    "audio" to signatureJson(audio),
                    "subtitleContext" to signatureJson(context),
                    "config" to configJson(config),
                ),
                GoldenJson.obj(
                    "offsetMs" to GoldenJson.num(result.offsetMs),
                    "peakScore" to GoldenJson.f32(result.peakScore),
                    "secondPeakScore" to GoldenJson.f32(result.secondPeakScore),
                    "peakMargin" to GoldenJson.f32(result.peakMargin),
                ),
            )
        }
    }

    private fun consensusCases(): List<String> {
        val agreeing = (0 until 3).map { measurement(10_000L + it * 40_000L, 2_400L) }
        val conflicting = listOf(
            measurement(10_000L, -60_000L),
            measurement(50_000L, 60_000L),
            measurement(90_000L, 120_000L),
        )
        return listOf("agreeing_3" to agreeing, "conflicting_3" to conflicting, "agreeing_1" to agreeing.take(1))
            .map { (id, measurements) ->
                GoldenJson.case(
                    id,
                    GoldenJson.obj("measurements" to GoldenJson.arr(measurements.map(::measurementJson))),
                    GoldenJson.obj("result" to resultJson(OffsetConsensus().resolve(measurements))),
                )
            }
    }

    private fun probeCases(): List<String> {
        val empty = ParsedSubtitle(emptyList())
        val short = cues(4, 0L, 700L, 500L)
        val long = cues(200, 0L, 2_000L, 1_200L)
        return listOf(
            Triple("empty", empty, 60_000L to 3),
            Triple("short_media", short, 3_000L to 3),
            Triple("long_media", long, 600_000L to 2),
        ).map { (id, subtitle, params) ->
            val selected = SubtitleProbeSelectorImpl().select(subtitle, params.first, params.second)
            GoldenJson.case(
                id,
                GoldenJson.obj(
                    "subtitle" to subtitleJson(subtitle),
                    "mediaDurationMs" to GoldenJson.num(params.first),
                    "maxCount" to GoldenJson.num(params.second),
                ),
                GoldenJson.obj("candidates" to GoldenJson.arr(selected.map(::candidateJson))),
            )
        }
    }

    private fun activityCases(): List<String> {
        val subtitle = cues(6, 0L, 2_000L, 900L)
        val withNonDialogue = ParsedSubtitle(
            listOf(
                SubtitleCue(0, 1_000L, 2_000L, "[door creaks]"),
                SubtitleCue(1, 3_000L, 4_000L, "Real dialogue"),
                SubtitleCue(2, 5_000L, 6_000L, "♪ music ♪"),
            ),
        )
        return listOf("dialogue" to subtitle, "non_dialogue_weighting" to withNonDialogue)
            .map { (id, input) ->
                val signature = SubtitleActivityBuilderImpl().build(input, 0L, 8_000L, 40)
                GoldenJson.case(
                    id,
                    GoldenJson.obj(
                        "subtitle" to subtitleJson(input),
                        "startMs" to GoldenJson.num(0L),
                        "endMs" to GoldenJson.num(8_000L),
                        "bucketMs" to GoldenJson.num(40),
                    ),
                    signatureJson(signature),
                )
            }
    }

    private fun cues(count: Int, start: Long, stride: Long, length: Long): ParsedSubtitle =
        ParsedSubtitle(
            (0 until count).map { index ->
                val cueStart = start + index * stride
                SubtitleCue(index, cueStart, cueStart + length, "Dialogue $index")
            },
        )

    private fun signature(startMs: Long, bucketMs: Int, count: Int): ActivitySignature =
        ActivitySignature(
            startMs,
            bucketMs,
            FloatArray(count) { ((it * 37) % 101) / 100f },
            FloatArray(count) { ((it * 17) % 67) / 66f },
        )

    private fun measurement(startMs: Long, offsetMs: Long): SyncMeasurement = SyncMeasurement(
        subtitleProbeStartMs = startMs,
        audioProbeStartMs = startMs + offsetMs,
        estimatedOffsetMs = offsetMs,
        correlationScore = 0.92f,
        peakMargin = 0.12f,
        speechCoverage = 0.42f,
        transitionCount = 8,
        probeQuality = 0.95f,
    )

    private fun cueJson(cue: SubtitleCue): String = GoldenJson.obj(
        "index" to GoldenJson.num(cue.index),
        "startMs" to GoldenJson.num(cue.startMs),
        "endMs" to GoldenJson.num(cue.endMs),
        "rawText" to GoldenJson.str(cue.rawText),
        "baseRtl" to GoldenJson.bool(cue.baseRtl),
        "displayText" to GoldenJson.str(cue.displayText),
    )

    private fun subtitleJson(subtitle: ParsedSubtitle): String = GoldenJson.obj(
        "language" to GoldenJson.nullableStr(subtitle.language),
        "sourceId" to GoldenJson.str(subtitle.sourceId),
        "offsetMs" to GoldenJson.num(subtitle.offsetMs),
        "cues" to GoldenJson.arr(subtitle.cues.map(::cueJson)),
    )

    private fun signatureJson(value: ActivitySignature): String = GoldenJson.obj(
        "startMs" to GoldenJson.num(value.startMs),
        "bucketMs" to GoldenJson.num(value.bucketMs),
        "occupancy" to GoldenJson.floats(value.occupancy),
        "onsets" to GoldenJson.floats(value.onsets),
    )

    private fun configJson(config: CorrelationConfig): String = GoldenJson.obj(
        "minOffsetMs" to GoldenJson.num(config.minOffsetMs),
        "maxOffsetMs" to GoldenJson.num(config.maxOffsetMs),
        "stepMs" to GoldenJson.num(config.stepMs),
        "onsetWeight" to GoldenJson.f32(config.onsetWeight),
        "occupancyWeight" to GoldenJson.f32(config.occupancyWeight),
        "silenceWeight" to GoldenJson.f32(config.silenceWeight),
        "independentPeakDistanceMs" to GoldenJson.num(config.independentPeakDistanceMs),
    )

    private fun measurementJson(value: SyncMeasurement): String = GoldenJson.obj(
        "subtitleProbeStartMs" to GoldenJson.num(value.subtitleProbeStartMs),
        "audioProbeStartMs" to GoldenJson.num(value.audioProbeStartMs),
        "estimatedOffsetMs" to GoldenJson.num(value.estimatedOffsetMs),
        "correlationScore" to GoldenJson.f32(value.correlationScore),
        "peakMargin" to GoldenJson.f32(value.peakMargin),
        "speechCoverage" to GoldenJson.f32(value.speechCoverage),
        "transitionCount" to GoldenJson.num(value.transitionCount),
        "probeQuality" to GoldenJson.f32(value.probeQuality),
    )

    private fun candidateJson(value: ProbeCandidate): String = GoldenJson.obj(
        "subtitleStartMs" to GoldenJson.num(value.subtitleStartMs),
        "durationMs" to GoldenJson.num(value.durationMs),
        "distinctiveness" to GoldenJson.f32(value.distinctiveness),
    )

    private fun correctorCueJson(value: LinearDriftCorrector.Cue): String = GoldenJson.obj(
        "startMs" to GoldenJson.num(value.startMs),
        "endMs" to GoldenJson.num(value.endMs),
        "text" to GoldenJson.str(value.text),
    )

    private fun anchorJson(value: SyncAnchor): String = GoldenJson.obj(
        "subtitleTimeMs" to GoldenJson.num(value.subtitleTimeMs),
        "mediaTimeMs" to GoldenJson.num(value.mediaTimeMs),
    )

    private fun modelJson(model: SubtitleSyncModel): String = when (model) {
        SubtitleSyncModel.Identity -> GoldenJson.obj("kind" to GoldenJson.str("Identity"))
        is SubtitleSyncModel.Offset -> GoldenJson.obj(
            "kind" to GoldenJson.str("Offset"),
            "offsetMs" to GoldenJson.num(model.offsetMs),
        )
        is SubtitleSyncModel.Affine -> GoldenJson.obj(
            "kind" to GoldenJson.str("Affine"),
            "scale" to GoldenJson.f64(model.scale),
            "offsetMs" to GoldenJson.num(model.offsetMs),
        )
        is SubtitleSyncModel.Piecewise -> GoldenJson.obj(
            "kind" to GoldenJson.str("Piecewise"),
            "anchors" to GoldenJson.arr(model.anchors.map(::anchorJson)),
        )
    }

    private fun resultJson(result: SubtitleSyncResult): String = when (result) {
        is SubtitleSyncResult.Synchronized -> GoldenJson.obj(
            "kind" to GoldenJson.str("Synchronized"),
            "model" to modelJson(result.model),
            "confidence" to GoldenJson.f32(result.confidence),
            "measurements" to GoldenJson.arr(result.measurements.map(::measurementJson)),
        )
        is SubtitleSyncResult.ComplexDriftDetected -> GoldenJson.obj(
            "kind" to GoldenJson.str("ComplexDriftDetected"),
            "measurements" to GoldenJson.arr(result.measurements.map(::measurementJson)),
        )
        is SubtitleSyncResult.LowConfidence -> GoldenJson.obj(
            "kind" to GoldenJson.str("LowConfidence"),
            "measurements" to GoldenJson.arr(result.measurements.map(::measurementJson)),
        )
        SubtitleSyncResult.Unsupported -> GoldenJson.obj("kind" to GoldenJson.str("Unsupported"))
        SubtitleSyncResult.ResourceDenied -> GoldenJson.obj("kind" to GoldenJson.str("ResourceDenied"))
        SubtitleSyncResult.Cancelled -> GoldenJson.obj("kind" to GoldenJson.str("Cancelled"))
        is SubtitleSyncResult.Failed -> GoldenJson.obj(
            "kind" to GoldenJson.str("Failed"),
            "reason" to GoldenJson.str(result.reason),
        )
    }
}
