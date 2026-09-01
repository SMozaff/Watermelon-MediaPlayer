package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import java.security.MessageDigest

class SubtitleFingerprintProvider {
    fun fingerprint(subtitle: ParsedSubtitle): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("watermelon-subtitle-v1\n".toByteArray(Charsets.UTF_8))
        digest.update("${subtitle.cues.size}\n".toByteArray(Charsets.UTF_8))
        subtitle.cues.forEach { cue ->
            digest.update("${cue.startMs}|${cue.endMs}|".toByteArray(Charsets.UTF_8))
            digest.update(
                cue.rawText
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .trim()
                    .toByteArray(Charsets.UTF_8)
            )
            digest.update(byteArrayOf('\n'.code.toByte()))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
