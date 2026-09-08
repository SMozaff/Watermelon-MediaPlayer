package com.watermelon.common.repository

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleTrack

/**
 * Result of an online subtitle search.
 */
sealed class OnlineSubtitleSearchResult {
    data class Success(val tracks: List<SubtitleTrack>) : OnlineSubtitleSearchResult()
    object NoResults : OnlineSubtitleSearchResult()
    object Offline : OnlineSubtitleSearchResult()
    object ProviderNotConfigured : OnlineSubtitleSearchResult()
    object AuthenticationRequired : OnlineSubtitleSearchResult()
    object PermissionDenied : OnlineSubtitleSearchResult()
    object QuotaExceeded : OnlineSubtitleSearchResult()
    data class Failure(val message: String) : OnlineSubtitleSearchResult()
}

/**
 * A successfully downloaded subtitle ready for activation.
 */
data class DownloadedSubtitle(
    val localPath: String,
    val subtitle: ParsedSubtitle
)

interface SubtitleRepository {
    /**
     * Find subtitles from local cache only (offline).
     * Does NOT initiate network traffic.
     */
    suspend fun findSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack>

    /**
     * Search for subtitles online via configured providers.
     * Requires explicit user action - never called automatically on video open.
     */
    suspend fun searchOnlineSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): OnlineSubtitleSearchResult

    /**
     * Download a subtitle and cache it under the owning media item.
     * Returns the local path and parsed subtitle for immediate activation.
     */
    suspend fun downloadSubtitle(
        mediaItem: MediaItem,
        track: SubtitleTrack
    ): DownloadedSubtitle
}
