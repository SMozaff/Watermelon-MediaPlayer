package com.watermelon.subtitle.provider

/**
 * Represents a resolved download link from a [SubtitleProvider].
 *
 * @property url The HTTPS download URL (validated by the provider)
 * @property fileName Suggested filename for the downloaded subtitle
 */
data class SubtitleDownloadLink(
    val url: String,
    val fileName: String
)
