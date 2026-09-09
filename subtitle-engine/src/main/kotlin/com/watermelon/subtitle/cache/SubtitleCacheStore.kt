package com.watermelon.subtitle.cache

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * File-backed cache for downloaded subtitles.
 *
 * Layout (format v2 — hierarchical, unambiguous):
 *
 *   <cacheDir>/<mediaSha>/<language>/<encodedProvider>/<remoteId>.srt
 *
 * Every metadata field lives in its own path segment, so no delimiter parsing
 * is required and provider IDs containing dots (e.g. the real
 * `opensubtitles.com`) round-trip exactly. The previous flat
 * `<sha>.<lang>.<provider>.<remoteId>.srt` layout split filenames on '.' and
 * corrupted such IDs (`providerId="opensubtitles"`, `remoteFileId=null`).
 * Files in the old flat layout are ignored by [list] but still removed by
 * [clear].
 *
 * Pure JVM — no Android APIs — so this store is unit-testable without Robolectric.
 */
class SubtitleCacheStore(
    private val cacheDir: File
) {
    companion object {
        private const val MAX_SUBTITLE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MiB
        private const val SUBTITLE_EXTENSION = ".srt"
    }

    init {
        cacheDir.mkdirs()
    }

    private fun mediaKey(uri: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Path-segment sanitizer. Unlike the old filename sanitizer this excludes
     * '.', so a delimiter can never appear inside a field value.
     */
    private fun sanitizePathComponent(component: String): String {
        return component
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(64)
    }

    private fun encodeProvider(providerId: String): String {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(providerId.toByteArray(Charsets.UTF_8))
            .take(64)
    }

    private fun decodeProvider(encoded: String): String {
        return String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
    }

    private fun trackFile(
        mediaItem: MediaItem,
        track: SubtitleTrack,
        providerId: String
    ): File {
        val mediaSha = mediaKey(mediaItem.uri)
        val language = sanitizePathComponent(track.language)
        val safeProvider = encodeProvider(providerId)

        val remoteId = track.remoteFileId?.toString()
            ?: sanitizePathComponent(track.label)

        return File(
            File(File(File(cacheDir, mediaSha), language), safeProvider),
            "$remoteId$SUBTITLE_EXTENSION"
        )
    }

    /**
     * List all cached subtitles for [mediaItem], filtered and ordered by [preferredLanguages].
     *
     * @return List of SubtitleTrack pointing to cached files, ordered by language preference
     */
    fun list(mediaItem: MediaItem, preferredLanguages: List<String>): List<SubtitleTrack> {
        val mediaDir = File(cacheDir, mediaKey(mediaItem.uri))
        if (!mediaDir.isDirectory) return emptyList()

        val tracks = mutableListOf<SubtitleTrack>()
        val languageDirs = mediaDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        for (languageDir in languageDirs) {
            val lang = languageDir.name
            if (lang.isEmpty()) continue
            if (preferredLanguages.isNotEmpty() && lang !in preferredLanguages) continue

            val providerDirs = languageDir.listFiles { file -> file.isDirectory } ?: continue
            for (providerDir in providerDirs) {
                val providerId = runCatching { decodeProvider(providerDir.name) }.getOrNull()
                    ?: continue
                val files = providerDir.listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(SUBTITLE_EXTENSION, ignoreCase = true) &&
                        !file.name.endsWith(".part", ignoreCase = true)
                } ?: continue
                for (file in files) {
                    val remoteIdStr = file.nameWithoutExtension
                    tracks += SubtitleTrack(
                        language = lang,
                        label = file.name,
                        downloadUrl = file.toURI().toString(),
                        rating = 0f,
                        providerId = providerId,
                        remoteFileId = remoteIdStr.toLongOrNull()
                    )
                }
            }
        }

        return tracks.sortedWith(
            compareBy(
                { if (preferredLanguages.isEmpty()) 0 else preferredLanguages.indexOf(it.language).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                { -it.rating }
            )
        )
    }

    /**
     * Get the first cached subtitle for [mediaItem] in [preferredLanguages] order.
     *
     * @return First matching cached file, or null if none found
     */
    fun first(mediaItem: MediaItem, preferredLanguages: List<String>): File? {
        val tracks = list(mediaItem, preferredLanguages)
        if (tracks.isEmpty()) return null

        // downloadUrl is a file: URI produced by File.toURI() — parse it with
        // java.net so this store stays off Android APIs (pure-JVM testable).
        val firstTrack = tracks.first()
        return runCatching {
            val uri = java.net.URI(firstTrack.downloadUrl)
            if (uri.scheme == "file") File(uri) else null
        }.getOrNull()
    }

    /**
     * Write a subtitle to cache atomically.
     *
     * @param mediaItem The owning media item (used for cache key)
     * @param track The subtitle track metadata
     * @param providerId The provider that supplied this subtitle
     * @param bytes The raw subtitle file bytes (must be SRT format)
     * @return The exact cached File after successful write
     * @throws IllegalArgumentException if bytes exceed maximum size
     * @throws IllegalStateException if write fails
     */
    fun write(
        mediaItem: MediaItem,
        track: SubtitleTrack,
        providerId: String,
        bytes: ByteArray
    ): File {
        require(bytes.size <= MAX_SUBTITLE_SIZE_BYTES) {
            "Subtitle file too large: ${bytes.size} bytes (max: $MAX_SUBTITLE_SIZE_BYTES)"
        }

        val targetFile = trackFile(mediaItem, track, providerId)
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")

        try {
            tempFile.writeBytes(bytes)

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw IllegalStateException("Failed to rename temporary file to $targetFile")
            }

            return targetFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    fun isCached(mediaItem: MediaItem, track: SubtitleTrack, providerId: String): Boolean {
        return trackFile(mediaItem, track, providerId).exists()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
