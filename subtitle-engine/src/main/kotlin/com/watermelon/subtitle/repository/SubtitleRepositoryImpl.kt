package com.watermelon.subtitle.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.model.VideoQuery
import com.watermelon.common.repository.DownloadedSubtitle
import com.watermelon.common.repository.OnlineSubtitleSearchResult
import com.watermelon.common.repository.SubtitleRepository
import com.watermelon.subtitle.cache.SubtitleCacheStore
import com.watermelon.subtitle.hash.OpenSubtitlesHasher
import com.watermelon.subtitle.parser.SrtParser
import com.watermelon.subtitle.provider.AuthenticationRequiredException
import com.watermelon.subtitle.provider.PermissionDeniedException
import com.watermelon.subtitle.provider.ProviderException
import com.watermelon.subtitle.provider.ProviderResponseException
import com.watermelon.subtitle.provider.ProviderUnavailableException
import com.watermelon.subtitle.provider.QuotaExceededException
import com.watermelon.subtitle.provider.SubtitleProviderQuery
import com.watermelon.subtitle.provider.registry.SubtitleProviderRegistry
import com.watermelon.subtitle.source.LocalSidecarSourceImpl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SubtitleRepositoryImpl(
    private val context: Context,
    private val providerRegistry: SubtitleProviderRegistry,
) : SubtitleRepository {

    private val sidecarSource = LocalSidecarSourceImpl(context)
    private val downloadClient: HttpClient by lazy { HttpClient(Android) }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "subtitles").apply { mkdirs() }
    }
    private val cacheStore: SubtitleCacheStore = SubtitleCacheStore(cacheDir)

    companion object {
        private const val MAX_SUBTITLE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MiB
    }

    override suspend fun findSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        cachedTracks(mediaItem, preferredLanguages)
    }

    private suspend fun cachedTracks(mediaItem: MediaItem, preferredLanguages: List<String>): List<SubtitleTrack> {
        val tracks = cacheStore.list(mediaItem, preferredLanguages)
        if (tracks.isNotEmpty()) {
            return tracks
        }
        val sidecarTracks = sidecarSource.findAndParse(VideoQuery(
            displayName = mediaItem.displayName,
            parentFolder = mediaItem.parentFolder,
            sizeBytes = mediaItem.fileSize,
            durationMs = mediaItem.durationMs,
            languages = preferredLanguages
        ))
        return sidecarTracks ?: emptyList()
    }

    override suspend fun searchOnlineSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): OnlineSubtitleSearchResult = withContext(Dispatchers.IO) {
        val configuredProviders = providerRegistry.configuredProviders()
        if (configuredProviders.isEmpty()) {
            return@withContext OnlineSubtitleSearchResult.ProviderNotConfigured
        }

        if (!isNetworkAvailable()) {
            return@withContext OnlineSubtitleSearchResult.Offline
        }

        val hash = runCatching { hashFor(mediaItem) }.getOrNull()
            ?: return@withContext OnlineSubtitleSearchResult.Failure("Failed to compute media hash")

        val query = SubtitleProviderQuery(
            movieHash = hash,
            fileSize = mediaItem.fileSize,
            displayName = mediaItem.displayName,
            preferredLanguages = preferredLanguages
        )

        try {
            val tracks = providerRegistry.search(query)
            if (tracks.isEmpty()) {
                return@withContext OnlineSubtitleSearchResult.NoResults
            }
            return@withContext OnlineSubtitleSearchResult.Success(tracks)
        } catch (e: AuthenticationRequiredException) {
            return@withContext OnlineSubtitleSearchResult.AuthenticationRequired
        } catch (e: PermissionDeniedException) {
            return@withContext OnlineSubtitleSearchResult.PermissionDenied
        } catch (e: QuotaExceededException) {
            return@withContext OnlineSubtitleSearchResult.QuotaExceeded
        } catch (e: ProviderUnavailableException) {
            return@withContext OnlineSubtitleSearchResult.Failure(e.message)
        } catch (e: ProviderResponseException) {
            return@withContext OnlineSubtitleSearchResult.Failure(e.message)
        } catch (e: ProviderException) {
            return@withContext OnlineSubtitleSearchResult.Failure(e.message)
        } catch (e: Exception) {
            return@withContext OnlineSubtitleSearchResult.Failure(e.message ?: "Unknown error")
        }
    }

    override suspend fun downloadSubtitle(
        mediaItem: MediaItem,
        track: SubtitleTrack
    ): DownloadedSubtitle = withContext(Dispatchers.IO) {
        // Resolve the download URL through the provider registry
        val downloadLink = providerRegistry.resolveDownload(track)
        
        // Validate the resolved URL
        require(isAllowedDownloadUrl(downloadLink.url)) {
            "Refusing to download subtitle from untrusted URL: ${downloadLink.url}"
        }

        val response = downloadClient.get(downloadLink.url)
        if (!response.status.isSuccess()) {
            throw RuntimeException("Subtitle download failed: HTTP ${response.status.value}")
        }

        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_SUBTITLE_SIZE_BYTES) {
            throw RuntimeException("Subtitle file too large: $contentLength bytes (max: $MAX_SUBTITLE_SIZE_BYTES)")
        }

        val bytes = response.body<ByteArray>()
        if (bytes.size > MAX_SUBTITLE_SIZE_BYTES) {
            throw RuntimeException("Subtitle file too large: ${bytes.size} bytes (max: $MAX_SUBTITLE_SIZE_BYTES)")
        }

        // Validate the final redirected URL
        val finalUrl = response.request.url.toString()
        require(isAllowedDownloadUrl(finalUrl)) {
            "Final redirect URL not allowed: $finalUrl"
        }

        val cachedFile = cacheStore.write(
            mediaItem = mediaItem,
            track = track,
            providerId = track.providerId ?: "opensubtitles.com",
            bytes = bytes
        )

        val content = String(bytes, Charsets.UTF_8)
        val parsed = SrtParser.parse(content, track.language)
        if (parsed.cues.isEmpty()) {
            throw RuntimeException("Downloaded SRT contains no valid cues")
        }

        DownloadedSubtitle(
            localPath = cachedFile.absolutePath,
            subtitle = parsed
        )
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        val host = parsed?.host?.lowercase()
        return parsed?.scheme?.equals("https", ignoreCase = true) == true &&
            (host == "opensubtitles.com" || host?.endsWith(".opensubtitles.com") == true)
    }

    @Suppress("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun hashFor(mediaItem: MediaItem): String {
        val uri = android.net.Uri.parse(mediaItem.uri)
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            requireNotNull(pfd) { "Unable to open file descriptor for ${mediaItem.uri}" }
            return OpenSubtitlesHasher.hash(pfd.fileDescriptor, mediaItem.fileSize)
        }
    }

    // ── S1 extension: parsed render-ready subtitle (offline only) ───────────────

    suspend fun parsedFor(mediaItem: MediaItem, preferredLanguages: List<String>): ParsedSubtitle? =
        withContext(Dispatchers.IO) {
            // Step 0: local sidecar (no network)
            val query = VideoQuery(
                displayName = mediaItem.displayName,
                parentFolder = mediaItem.parentFolder,
                sizeBytes = mediaItem.fileSize,
                durationMs = mediaItem.durationMs,
                languages = preferredLanguages
            )
            val sidecar = sidecarSource.findAndParse(query)
            if (sidecar != null) {
                return@withContext sidecar
            }

            // Step 1: cached download
            val cachedFile = cacheStore.first(mediaItem, preferredLanguages)
            if (cachedFile != null) {
                return@withContext parseCachedFile(cachedFile)
            }

            null
        }

    private fun parseCachedFile(file: File): ParsedSubtitle? {
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@parseCachedFile null
        val lang = file.nameWithoutExtension.substringAfterLast('.', "").ifEmpty { null }
        return when (file.extension.lowercase()) {
            "srt" -> runCatching { SrtParser.parse(content, lang) }.getOrNull()
            else -> null
        }
    }
}

object SubtitleRepositoryImpl {
    const val MAX_SUBTITLE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MiB
}