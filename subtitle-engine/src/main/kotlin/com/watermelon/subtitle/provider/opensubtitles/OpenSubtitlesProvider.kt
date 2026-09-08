package com.watermelon.subtitle.provider.opensubtitles

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
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Modern OpenSubtitles.com REST API client (api.opensubtitles.com).
 *
 * Implements the two-step flow:
 * 1. GET /api/v1/subtitles - search with hash/size/languages
 * 2. POST /api/v1/download - resolve file_id to temporary download URL
 *
 * Does NOT fall back to legacy opensubtitles.org mirrors.
 */
class OpenSubtitlesProvider(
    private val apiKey: String,
    private val userAgent: String,
    private val httpClient: io.ktor.client.HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SubtitleProvider {

    override val id: String = "opensubtitles.com"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack> {
        if (!isConfigured) throw ProviderUnavailableException("OpenSubtitles API key not configured")

        return try {
            val response = httpClient.get("${BASE_URL}/subtitles") {
                header("Api-Key", apiKey)
                header("User-Agent", userAgent)
                header("Accept", "application/json")
                
                query.movieHash?.let { parameter("moviehash", it) }
                query.fileSize.takeIf { it > 0 }?.let { parameter("moviebytesize", it.toString()) }
                if (query.preferredLanguages.isNotEmpty()) {
                    parameter("languages", query.preferredLanguages.joinToString(","))
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val subtitlesResponse: SubtitlesResponse = response.body()
                    subtitlesResponse.data.flatMap { item ->
                        item.attributes.toSubtitleTracks(id).filter { track ->
                            query.preferredLanguages.isEmpty() || track.language in query.preferredLanguages
                        }.sortedWith(
                            compareBy(
                                { if (query.preferredLanguages.isEmpty()) 0 else query.preferredLanguages.indexOf(it.language).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                                { if (it.hashMatched) 0 else 1 },
                                { -it.rating },
                                { -it.downloadCount }
                            )
                        )
                    }
                }
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw AuthenticationRequiredException("OpenSubtitles authentication failed")
                HttpStatusCode.TooManyRequests -> throw QuotaExceededException("OpenSubtitles API quota exceeded")
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> throw ProviderUnavailableException("OpenSubtitles service unavailable")
                else -> throw ProviderResponseException("OpenSubtitles search failed: ${response.status}")
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: Exception) {
            throw ProviderUnavailableException("OpenSubtitles search failed: ${e.message}")
        }
    }

    override suspend fun resolveDownload(track: SubtitleTrack): SubtitleDownloadLink {
        if (!isConfigured) {
            throw ProviderUnavailableException("OpenSubtitles API key not configured")
        }

        val remoteFileId = track.remoteFileId
            ?: throw IllegalArgumentException("Track missing remoteFileId")

        return try {
            val response = httpClient.post("${BASE_URL}/download") {
                header("Api-Key", apiKey)
                header("User-Agent", userAgent)
                header("Accept", "application/json")
                contentType(ContentType.Application.Json)
                setBody(DownloadRequest(fileId = remoteFileId, subFormat = "srt"))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val downloadResponse: DownloadResponse = response.body()
                    val downloadUrl = downloadResponse.link
                        ?: throw ProviderResponseException("Download response missing link")

                    // Validate the download URL
                    validateDownloadUrl(downloadUrl)

                    SubtitleDownloadLink(
                        url = downloadUrl,
                        fileName = track.remoteFileName ?: "subtitle_${track.language}.srt"
                    )
                }
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw AuthenticationRequiredException("OpenSubtitles authentication failed")
                HttpStatusCode.TooManyRequests -> throw QuotaExceededException("OpenSubtitles API quota exceeded")
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> throw ProviderUnavailableException("OpenSubtitles service unavailable")
                else -> throw ProviderResponseException("OpenSubtitles download resolution failed: ${response.status}")
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: Exception) {
            throw ProviderUnavailableException("OpenSubtitles download resolution failed: ${e.message}")
        }
    }

    override fun close() {
        // Caller is responsible for closing the shared HttpClient
    }

    private fun validateDownloadUrl(url: String) {
        val uri = java.net.URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Download URL must use HTTPS: $url"
        }
        val host = uri.host.lowercase()
        require(host == "opensubtitles.com" || host.endsWith(".opensubtitles.com")) {
            "Download URL host not allowed: $host"
        }
    }

    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    }
}

@Serializable
private data class SubtitlesResponse(
    val data: List<SubtitleItem> = emptyList()
)

@Serializable
private data class SubtitleItem(
    val attributes: SubtitleAttributes
)

@Serializable
private data class SubtitleAttributes(
    val language: String,
    val release: String? = null,
    val ratings: Float? = null,
    @SerialName("download_count")
    val downloadCount: Int? = null,
    @SerialName("moviehash_match")
    val moviehashMatch: Boolean? = null,
    val files: List<SubtitleFile>? = null
)

@Serializable
private data class SubtitleFile(
    @SerialName("file_id")
    val fileId: Long,
    @SerialName("file_name")
    val fileName: String? = null
)

@Serializable
private data class DownloadRequest(
    @SerialName("file_id") val fileId: Long,
    @SerialName("sub_format") val subFormat: String,
)

@Serializable
private data class DownloadResponse(
    val link: String? = null
)

private fun SubtitleAttributes.toSubtitleTracks(providerId: String): List<SubtitleTrack> {
    val filesList = files ?: return emptyList()
    
    return filesList.mapNotNull { file ->
        SubtitleTrack(
            language = language,
            label = release ?: file.fileName ?: "$language subtitle",
            downloadUrl = "", // Empty until resolved via POST /download
            rating = ratings ?: 0f,
            providerId = providerId,
            remoteFileId = file.fileId,
            remoteFileName = file.fileName,
            downloadCount = downloadCount ?: 0,
            hashMatched = moviehashMatch == true
        )
    }
}
