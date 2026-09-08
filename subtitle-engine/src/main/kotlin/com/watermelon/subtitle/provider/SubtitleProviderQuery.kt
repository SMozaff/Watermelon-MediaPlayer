package com.watermelon.subtitle.provider

import com.watermelon.common.model.SubtitleTrack

/**
 * Query parameters for searching subtitles from a [SubtitleProvider].
 *
 * Contains the metadata needed to perform an accurate search:
 * - hash/size for OpenSubtitles matching
 * - displayName as fallback label
 * - preferredLanguages for filtering/ordering results
 */
data class SubtitleProviderQuery(
    val movieHash: String? = null,
    val fileSize: Long = 0L,
    val displayName: String,
    val preferredLanguages: List<String>
)
