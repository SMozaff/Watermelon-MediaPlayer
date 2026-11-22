package com.watermelon.mediatools.subtitle.sync

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Generates the tenth golden-vector file from the current Kotlin PCM accumulator. */
class SpeechAccumulatorGoldenVectorGeneratorTest {

    @Test
    fun generate() {
        assumeTrue(System.getProperty(GENERATE_PROPERTY) == "true")
        val path = System.getProperty(DIR_PROPERTY) ?: error("Set -D$DIR_PROPERTY=<absolute directory>")
        val dir = File(path)
        if (!dir.isDirectory && !dir.mkdirs()) error("Cannot create golden directory: $dir")

        val cases = listOf(
            accumulatorCase("pcm16_bursts", PcmSampleEncoding.PCM_16),
            accumulatorCase("pcm_float_bursts", PcmSampleEncoding.PCM_FLOAT),
        )
        val body = obj(
            "component" to q("speech_accumulator"),
            "generator" to q("kotlin"),
            "cases" to arr(cases),
        )
        File(dir, "speech_accumulator.json").writeText(body + "\n", Charsets.UTF_8)
    }

    private fun accumulatorCase(id: String, encoding: PcmSampleEncoding): String {
        val sampleRate = 16_000
        val durationMs = 8_000L
        val bucketMs = 40
        val sampleCount = sampleRate * (durationMs / 1_000L).toInt()
        val bytesPerSample = encoding.bytesPerSample
        val buffer = ByteBuffer.allocate(sampleCount * bytesPerSample).order(ByteOrder.nativeOrder())

        for (sample in 0 until sampleCount) {
            val timeMs = sample * 1_000L / sampleRate
            val inBurst = (1_000L..2_000L).contains(timeMs) ||
                (3_000L..4_000L).contains(timeMs) ||
                (5_000L..6_000L).contains(timeMs)
            val value = if (inBurst) 0.3 * sin(2.0 * Math.PI * 200.0 * sample / sampleRate) else 0.0
            when (encoding) {
                PcmSampleEncoding.PCM_16 -> buffer.putShort((value * 32_767.0).toInt().toShort())
                PcmSampleEncoding.PCM_FLOAT -> buffer.putFloat(value.toFloat())
            }
        }

        val accumulator = SpeechLikelihoodAccumulator(0L, durationMs, bucketMs)
        accumulator.consume(
            buffer = buffer,
            bufferOffset = 0,
            bufferSize = sampleCount * bytesPerSample,
            presentationTimeUs = 0L,
            sampleRate = sampleRate,
            channelCount = 1,
            encoding = encoding,
        )
        val signature = accumulator.finish()

        return case(
            id,
            obj(
                "targetStartUs" to num(0L),
                "durationMs" to num(durationMs),
                "bucketMs" to num(bucketMs),
                "sampleRate" to num(sampleRate),
                "channelCount" to num(1),
                "encoding" to q(encoding.name),
                "pattern" to q("three 1 s sine bursts at 200 Hz, 1-2 s / 3-4 s / 5-6 s"),
                "sampleCount" to num(sampleCount.toLong()),
            ),
            obj(
                "startMs" to num(signature.startMs),
                "bucketMs" to num(signature.bucketMs),
                "decodedSampleCount" to num(accumulator.decodedSampleCount),
                "occupancy" to floats(signature.occupancy),
                "onsets" to floats(signature.onsets),
            ),
        )
    }

    private fun q(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u"); append(ch.code.toString(16).padStart(4, '0'))
            } else append(ch)
        }
        append('"')
    }

    private fun num(value: Long): String = value.toString()
    private fun num(value: Int): String = value.toString()
    private fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${q(key)}:$value" }

    private fun arr(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]")

    private fun f32(value: Float): String = obj(
        "decimal" to q(value.toString()),
        "bits" to q(java.lang.Integer.toHexString(java.lang.Float.floatToRawIntBits(value)).padStart(8, '0')),
    )

    private fun floats(values: FloatArray): String = arr(values.map(::f32))

    private fun case(id: String, input: String, output: String): String =
        obj("id" to q(id), "input" to input, "output" to output)

    private companion object {
        const val GENERATE_PROPERTY = "watermelon.generateGolden"
        const val DIR_PROPERTY = "watermelon.goldenDir"
    }
}
