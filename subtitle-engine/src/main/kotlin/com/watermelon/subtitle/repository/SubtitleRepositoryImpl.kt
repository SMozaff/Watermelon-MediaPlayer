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
import com.watermelon.common.util.FileLogger
import com.watermelon.subtitle.cache.SubtitleCacheStore
import com.watermelon.subtitle.hash.OpenSubtitlesHasher
import com.watermelon.subtitle.parser.SrtParser
import com.watermelon.subtitle.provider.SubtitleProvider
import com.watermelon.subtitle.provider.SubtitleProviderQuery
import com.watermelon.subtitle.provider.registry.SubtitleProviderRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * [SubtitleRepository] implementation with offline-first design.
 *
 * S1 priority order:
 *   0. Local sidecar — scan folder beside the video for matching .srt files (offline)
 *   1. Local cache   — previously downloaded subtitles keyed by media URI (offline)
 *   2. Online search — explicit user action only, never automatic on video open
 *
 * [parsedFor] provides the render-ready [ParsedSubtitle] for the player (offline only).
 * [searchOnlineSubtitles] is the explicit network path requiring user consent.
 */
class SubtitleRepositoryImpl(
    private val context: Context,
    private val providerRegistry: SubtitleProviderRegistry,
    private val apiKey: String = "",
    private val userAgent: String = "Watermelon/1.0"
) : SubtitleRepository {

    private val sidecarSource = LocalSidecarSourceImpl(context)
    private val cacheStore: SubtitleCacheStore by lazy {
        SubtitleCacheStore(File(context.cacheDir, "subtitles"))
    }
    private val downloadClient: HttpClient by lazy { HttpClient(Android) }

    // ── SubtitleRepository interface ────────────────────────────────────────────

    override suspend fun findSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        // Offline only: cache lookup
        val cached = cacheStore.list(mediaItem, preferredLanguages)
        if (cached.isNotEmpty()) {
            FileLogger.i("Subtitle", "cache hit: ${cached.size} track(s) for ${mediaItem.displayName}")
            return@withContext cached
        }
        emptyList()
    }

    override suspend fun searchOnlineSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): OnlineSubtitleSearchResult = withContext(Dispatchers.IO) {
        // Check if any provider is configured
        val configuredProviders = providerRegistry.configuredProviders()
        if (configuredProviders.isEmpty()) {
            return@withContext OnlineSubtitleSearchResult.ProviderNotConfigured
        }

        // Check network connectivity
        if (!isNetworkAvailable()) {
            return@withContext OnlineSubtitleSearchResult.Offline
        }

        // Compute hash for accurate matching
        val hash = runCatching { hashFor(mediaItem) }.getOrNull()
        if (hash == null) {
            return@withContext OnlineSubtitleSearchResult.Failure("Failed to compute media hash")
        }

        val query = SubtitleProviderQuery(
            movieHash = hash,
            fileSize = mediaItem.fileSize,
            displayName = mediaItem.displayName,
            preferredLanguages = preferredLanguages
        )

        try {
            val tracks = providerRegistry.search(query)
            if (tracks.isEmpty()) {
                OnlineSubtitleSearchResult.NoResults
            } else {
                OnlineSubtitleSearchResult.Success(tracks)
            }
        } catch (e: Exception) {
            FileLogger.e("Subtitle", "Online search failed: ${e.message}")
            OnlineSubtitleSearchResult.Failure(e.message ?: "Unknown error")
        }
    }

    override suspend fun downloadSubtitle(
        mediaItem: MediaItem,
        track: SubtitleTrack
    ): DownloadedSubtitle = withContext(Dispatchers.IO) {
        val providerId = track.providerId
            ?: throw IllegalArgumentException("Track missing providerId")

        // Resolve download link via provider
        val downloadLink = try {
            providerRegistry.resolveDownload(track)
        } catch (e: Exception) {
            throw when (e) {
                is IllegalArgumentException -> e
                else -> RuntimeException("Failed to resolve download: ${e.message}", e)
            }
        }

        // Validate download URL before fetching
        validateDownloadUrl(downloadLink.url)

        // Download the subtitle file
        val bytes = try {
            downloadClient.get(downloadLink.url).bodyAsChannel().toByteArray()
        } catch (e: Exception) {
            throw RuntimeException("Download failed: ${e.message}", e)
        }

        // Enforce size limit
        val maxSize = 5 * 1024 * 1024 // 5 MiB
        require(bytes.size <= maxSize) {
            "Subtitle file too large: ${bytes.size} bytes (max: $maxSize)"
        }

        // Write to cache atomically
        cacheStore.write(mediaItem, track, providerId, bytes)

        // Parse the downloaded subtitle
        val content = String(bytes, Charsets.UTF_8)
        val parsed = SrtParser.parse(content, track.language)
            ?: throw RuntimeException("Failed to parse downloaded subtitle as SRT")

        DownloadedSubtitle(
            localPath = cacheStore.first(mediaItem, listOf(track.language))?.absolutePath
                ?: File(context.cacheDir, "subtitles").listFiles()
                    ?.firstOrNull { it.name.endsWith(".srt") }?.absolutePath
                ?: throw RuntimeException("Cache write succeeded but file not found"),
            subtitle = parsed
        )
    }

    // ── S1 extension: parsed render-ready subtitle (offline only) ───────────────

    /**
     * Returns a [ParsedSubtitle] ready for the player, using the S1 offline-first strategy:
     *   1. Sidecar file in the video's folder
     *   2. Previously downloaded + cached file
     *
     * This method NEVER initiates network traffic.
     */
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
                FileLogger.i("Subtitle", "sidecar loaded: ${sidecar.cues.size} cues (${sidecar.language})")
                return@withContext sidecar
            }

            // Step 1: cached download
            val cachedFile = cacheStore.first(mediaItem, preferredLanguages)
            if (cachedFile != null) {
                FileLogger.i("Subtitle", "cache loaded: ${cachedFile.name}")
                return@withContext parseCachedFile(cachedFile)
            }

            FileLogger.i("Subtitle", "no local subtitle for ${mediaItem.displayName}")
            null
        }

    // ── Private helpers ─────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun validateDownloadUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Download URL must use HTTPS: $url"
        }
        val host = uri.host.lowercase()
        require(host.endsWith("opensubtitles.com") || host.endsWith("opensubtitles.org")) {
            "Download URL host not allowed: $host"
        }
    }

    private fun parseCachedFile(file: File): ParsedSubtitle? {
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val lang = file.nameWithoutExtension.substringAfterLast('.', "").ifEmpty { null }
        return when (file.extension.lowercase()) {
            "srt" -> runCatching { SrtParser.parse(content, lang) }.getOrNull()
            else -> null
        }
    }

    /**
     * Computes the real OpenSubtitles file hash for [mediaItem].
     */
    private fun hashFor(mediaItem: MediaItem): String {
        val uri = android.net.Uri.parse(mediaItem.uri)
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            requireNotNull(pfd) { "Unable to open file descriptor for ${mediaItem.uri}" }
            return OpenSubtitlesHasher.hash(pfd.fileDescriptor, mediaItem.fileSize)
        }
    }
}

// Helper extension to convert Channel to ByteArray
private suspend fun io.ktor.utils.io.ByteChannel.toByteArray(): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    this.copyTo(buffer)
    return buffer.toByteArray()
}
