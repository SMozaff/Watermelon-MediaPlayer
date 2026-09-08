package com.watermelon.subtitle.provider

import com.watermelon.common.model.SubtitleTrack

/**
 * A subtitle provider abstraction for discovering and resolving subtitle downloads.
 *
 * Implementations handle provider-specific API details (authentication, request format,
 * response parsing). The repository layer coordinates multiple providers without being
 * hard-coded to any single one.
 */
interface SubtitleProvider {
    /**
     * Unique identifier for this provider (e.g., "opensubtitles.com")
     */
    val id: String

    /**
     * True if the provider is properly configured and ready to use.
     * For OpenSubtitles, this checks for a valid API key.
     */
    val isConfigured: Boolean

    /**
     * Search for subtitles matching [query].
     *
     * @return List of subtitle tracks with metadata. May be empty if no results found.
     */
    suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack>

    /**
     * Resolve a download link for a specific [track] returned from [search].
     *
     * Modern providers like OpenSubtitles.com require a two-step process:
     * 1. Search returns metadata including a file_id
     * 2. POST to /download with file_id returns a temporary download URL
     *
     * @param track A subtitle track from a previous [search] call
     * @return Resolved download link with validated HTTPS URL
     * @throws ProviderException on authentication, quota, or network errors
     */
    suspend fun resolveDownload(track: SubtitleTrack): SubtitleDownloadLink

    /**
     * Clean up resources (close HTTP clients, etc.)
     */
    fun close() {}
}

/**
 * Exceptions thrown by [SubtitleProvider] implementations.
 */
sealed class ProviderException(message: String) : Exception(message)

class AuthenticationRequiredException(message: String = "Authentication required") : ProviderException(message)

class PermissionDeniedException(message: String = "Permission denied") : ProviderException(message)

class QuotaExceededException(message: String = "API quota exceeded") : ProviderException(message)

class ProviderUnavailableException(message: String = "Provider unavailable") : ProviderException(message)

class ProviderResponseException(message: String) : ProviderException(message)
