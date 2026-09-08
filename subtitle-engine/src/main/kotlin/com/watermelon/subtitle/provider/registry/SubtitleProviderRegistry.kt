package com.watermelon.subtitle.provider.registry

import com.watermelon.common.model.SubtitleTrack
import com.watermelon.subtitle.provider.SubtitleDownloadLink
import com.watermelon.subtitle.provider.SubtitleProvider
import com.watermelon.subtitle.provider.SubtitleProviderQuery

/**
 * Registry/coordinator for subtitle providers.
 *
 * Allows the repository layer to search across multiple providers without
 * being hard-coded to any specific implementation.
 */
class SubtitleProviderRegistry(
    private val providers: List<SubtitleProvider>
) {
    /**
     * Get all configured providers.
     */
    fun configuredProviders(): List<SubtitleProvider> {
        return providers.filter { it.isConfigured }
    }

    /**
     * Search across all configured providers and merge results.
     * Results are ordered by provider priority (list order), then by individual ranking.
     */
    suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack> {
        return configuredProviders().flatMap { provider ->
            try {
                provider.search(query)
            } catch (e: Exception) {
                emptyList() // Silently skip unavailable providers
            }
        }.sortedWith(
            compareBy<SubtitleTrack>(
                { track -> providers.indexOfFirst { it.id == track.providerId }.takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                { track -> if (track.hashMatched) 0 else 1 },
                { track -> -track.rating },
                { track -> -track.downloadCount },
                { track -> track.label },
            )
        )
    }

    /**
     * Find the provider with the given ID.
     */
    fun providerFor(providerId: String): SubtitleProvider? {
        return providers.find { it.id == providerId }
    }

    /**
     * Resolve a download using the appropriate provider.
     */
    suspend fun resolveDownload(track: SubtitleTrack): SubtitleDownloadLink {
        val providerId = track.providerId
            ?: throw IllegalArgumentException("Track missing providerId")

        val provider = providerFor(providerId)
            ?: throw IllegalArgumentException("No provider found with id: $providerId")

        if (!provider.isConfigured) {
            throw IllegalStateException("Provider $providerId is not configured")
        }

        return provider.resolveDownload(track)
    }

    /**
     * Close all providers (release resources).
     */
    fun close() {
        providers.forEach { it.close() }
    }
}
