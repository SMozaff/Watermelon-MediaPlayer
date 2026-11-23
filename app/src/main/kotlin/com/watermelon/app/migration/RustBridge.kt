package com.watermelon.app.migration

/**
 * The minimum UniFFI surface used by the D-4 local shadow comparator. The generated Kotlin API
 * lives in the `uniffi.watermelon_ffi` package and requires JNA plus a packaged `.so`; both are
 * present, but native calls happen only under the opt-in shadow flags.
 *
 * Keeping the seam here means the comparator can be reviewed and tested without changing
 * production behavior; `RustCutover` is the separate Stage 5 path that can return Rust results.
 */
internal interface RustBridge {
    fun bidiIsRtl(text: String): Boolean
    fun bidiFormat(text: String, baseRtl: Boolean): String
    fun srtParseJson(content: String, language: String?): String
}

/** Fallback bridge for tests or builds without native artifacts: every comparison is unavailable. */
internal object UnavailableRustBridge : RustBridge {
    override fun bidiIsRtl(text: String): Boolean = unavailable("bidi_is_rtl")

    override fun bidiFormat(text: String, baseRtl: Boolean): String = unavailable("bidi_format")

    override fun srtParseJson(content: String, language: String?): String = unavailable("srt_parse")

    private fun unavailable(component: String): Nothing =
        throw UnsupportedOperationException("$component Rust bridge is not packaged")
}
