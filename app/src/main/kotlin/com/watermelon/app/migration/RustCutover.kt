package com.watermelon.app.migration

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.subtitle.bidi.BidiFormatter
import com.watermelon.subtitle.parser.SrtParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uniffi.watermelon_ffi.bidiFormat
import uniffi.watermelon_ffi.bidiIsRtl
import uniffi.watermelon_ffi.srtParseJson
import uniffi.watermelon_ffi.uniffiEnsureInitialized

/**
 * Stage 5 cutover implementation. Installed as [SrtParser.backend] only when the app's opt-in
 * `watermelon.useRustCore` flag is enabled. Any native failure falls back to the Kotlin parser,
 * which is the documented rollback behavior; no exception is allowed to reach a user.
 */
internal object RustCutover : SrtParser.Backend {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RustCue(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val rawText: String,
        val baseRtl: Boolean,
        val displayText: String,
    )

    @Serializable
    private data class RustParsed(
        val language: String? = null,
        val cues: List<RustCue> = emptyList(),
    )

    fun installIfEnabled() {
        if (MigrationFlags.useRustCore) SrtParser.backend = this
    }

    override fun parse(content: String, language: String?): ParsedSubtitle {
        val kotlinResult = if (MigrationFlags.shadowMode || MigrationFlags.debugLogging) {
            runCatching { SrtParser.parseKotlin(content, language) }.getOrNull()
        } else {
            null
        }
        val rustResult = runCatching { parseRust(content, language) }
        return if (rustResult.isSuccess) {
            val rust = rustResult.getOrThrow()
            if (kotlinResult != null && MigrationFlags.debugLogging) {
                RustMigrationLog.compare("srt_parse", kotlinResult, rust)
            }
            rust
        } else {
            val reason = rustResult.exceptionOrNull()?.message ?: "unknown native failure"
            RustMigrationLog.unavailable("srt_parse", reason)
            kotlinResult ?: SrtParser.parseKotlin(content, language)
        }
    }

    fun isRtl(text: String): Boolean = runCatching {
        uniffiEnsureInitialized()
        bidiIsRtl(text)
    }.getOrElse { error ->
        RustMigrationLog.unavailable("bidi_is_rtl", error.message ?: "unknown native failure")
        BidiFormatter.isRtl(text)
    }

    fun format(text: String, baseRtl: Boolean): String = runCatching {
        uniffiEnsureInitialized()
        bidiFormat(text, baseRtl)
    }.getOrElse { error ->
        RustMigrationLog.unavailable("bidi_format", error.message ?: "unknown native failure")
        BidiFormatter.format(text, baseRtl)
    }

    private fun parseRust(content: String, language: String?): ParsedSubtitle {
        uniffiEnsureInitialized()
        val decoded = json.decodeFromString<RustParsed>(srtParseJson(content, language))
        return ParsedSubtitle(
            cues = decoded.cues.map { cue ->
                SubtitleCue(
                    index = cue.index,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    rawText = cue.rawText,
                    baseRtl = cue.baseRtl,
                    displayText = cue.displayText,
                )
            },
            language = decoded.language,
        )
    }
}
