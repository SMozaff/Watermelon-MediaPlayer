package com.watermelon.common.model

/** A subtitle candidate returned by the OpenSubtitles lookup. */
data class SubtitleTrack(
    val language: String,
    val label: String,
    val downloadUrl: String,
    val rating: Float,
    val providerId: String? = null,
    val remoteFileId: Long? = null,
    val remoteFileName: String? = null,
    val downloadCount: Int = 0,
    val hashMatched: Boolean = false
)
