package com.watermelon.subtitle.provider.registry

import com.watermelon.common.model.SubtitleTrack
import com.watermelon.subtitle.provider.AuthenticationRequiredException
import com.watermelon.subtitle.provider.PermissionDeniedException
import com.watermelon.subtitle.provider.ProviderException
import com.watermelon.subtitle.provider.ProviderResponseException
import com.watermelon.subtitle.provider.ProviderUnavailableException
import com.watermelon.subtitle.provider.QuotaExceededException
import com.watermelon.subtitle.provider.SubtitleDownloadLink
import com.watermelon.subtitle.provider.SubtitleProvider
import com.watermelon.subtitle.provider.SubtitleProviderQuery

class SubtitleProviderRegistry(
    private val providers: List<SubtitleProvider>
) {
    fun configuredProviders(): List<SubtitleProvider> {
        return providers.filter { it.isConfigured }
    }

    suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack> {
        return configuredProviders().flatMap { provider ->
            try {
                provider.search(query)
            } catch (e: ProviderException) {
                throw e
            } catch (e: Exception) {
                throw ProviderResponseException("Provider search failed: ${e.message}")
            }
        }.sortedWith(
            compareBy<SubtitleTrack>(
                { track ->
                    providers.indexOfFirst { provider ->
                        provider.id == track.providerId
                    }.takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                },
                { track -> if (track.hashMatched) 0 else 1 },
                { track -> -track.rating },
                { track -> -track.downloadCount },
                { track -> track.label.lowercase() },
            )
        )
    }

    fun providerFor(providerId: String): SubtitleProvider? {
        return providers.find { it.id == providerId }
    }

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

    fun close() {
        providers.forEach { it.close() }
    }
}