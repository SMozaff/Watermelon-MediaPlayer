package com.watermelon.app.migration

/**
 * The minimum UniFFI surface a future native bridge would implement. The generated Kotlin API
 * lives in the `uniffi.watermelon_ffi` package and requires JNA plus a packaged `.so`; neither is
 * enabled by this migration stage.
 *
 * Keeping the seam here means the shadow comparator can be reviewed and tested without changing
 * production behavior, and no Kotlin call site currently depends on Rust.
 */
internal interface RustBridge {
    fun bidiIsRtl(text: String): Boolean
    fun bidiFormat(text: String, baseRtl: Boolean): String
    fun srtParseJson(content: String, language: String?): String
}

/** Default bridge: Rust is not packaged, so every comparison is explicitly unavailable. */
internal object UnavailableRustBridge : RustBridge {
    override fun bidiIsRtl(text: String): Boolean = unavailable("bidi_is_rtl")

    override fun bidiFormat(text: String, baseRtl: Boolean): String = unavailable("bidi_format")

    override fun srtParseJson(content: String, language: String?): String = unavailable("srt_parse")

    private fun unavailable(component: String): Nothing =
        throw UnsupportedOperationException("$component Rust bridge is not packaged")
}
