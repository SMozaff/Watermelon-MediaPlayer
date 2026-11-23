package com.watermelon.app.migration

import uniffi.watermelon_ffi.srtParseJson
import uniffi.watermelon_ffi.uniffiEnsureInitialized

/** Native implementation of the D-4 comparison bridge, used only from local debug builds. */
internal object RustBridgeImpl : RustBridge {
    override fun bidiIsRtl(text: String): Boolean {
        uniffiEnsureInitialized()
        return uniffi.watermelon_ffi.bidiIsRtl(text)
    }

    override fun bidiFormat(text: String, baseRtl: Boolean): String {
        uniffiEnsureInitialized()
        return uniffi.watermelon_ffi.bidiFormat(text, baseRtl)
    }

    override fun srtParseJson(content: String, language: String?): String {
        uniffiEnsureInitialized()
        return srtParseJson(content, language)
    }
}
