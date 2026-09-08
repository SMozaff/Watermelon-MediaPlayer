package com.watermelon.subtitle.cache

import android.net.Uri
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import java.io.File
import java.security.MessageDigest

class SubtitleCacheStore(
    private val cacheDir: File
) {
    companion object {
        private const val MAX_SUBTITLE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MiB
    }

    init {
        cacheDir.mkdirs()
    }

    private fun mediaKey(uri: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeFilenameComponent(component: String): String {
        return component
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(64)
    }

    private fun buildCacheFilename(
        mediaItem: MediaItem,
        track: SubtitleTrack,
        providerId: String
    ): String {
        val mediaSha = mediaKey(mediaItem.uri)
        val language = sanitizeFilenameComponent(track.language)
        val safeProvider = sanitizeFilenameComponent(providerId)

        val remoteId = track.remoteFileId?.toString()
            ?: sanitizeFilenameComponent(track.label)

        return "$mediaSha.$language.$safeProvider.$remoteId.srt"
    }

    /**
     * List all cached subtitles for [mediaItem], filtered and ordered by [preferredLanguages].
     *
     * @return List of SubtitleTrack pointing to cached files, ordered by language preference
     */
    fun list(mediaItem: MediaItem, preferredLanguages: List<String>): List<SubtitleTrack> {
        val prefix = mediaKey(mediaItem.uri)
        val files = cacheDir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(prefix) &&
                file.name.endsWith(".srt", ignoreCase = true) &&
                !file.name.endsWith(".part", ignoreCase = true)
        } ?: return emptyList()

        return files.mapNotNull { file ->
            val parts = file.nameWithoutExtension.split('.')
            if (parts.size < 4) return@mapNotNull null

            val lang = parts.getOrElse(1) { "" }
            val providerId = parts.getOrElse(2) { "" }
            val remoteIdStr = parts.getOrElse(3) { "" }

            if (lang.isEmpty()) return@mapNotNull null

            if (preferredLanguages.isNotEmpty() && lang !in preferredLanguages) {
                return@mapNotNull null
            }

            SubtitleTrack(
                language = lang,
                label = file.name,
                downloadUrl = file.toURI().toString(),
                rating = 0f,
                providerId = providerId,
                remoteFileId = remoteIdStr.toLongOrNull()
            )
        }.sortedWith(
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

        val firstTrack = tracks.first()
        return Uri.parse(firstTrack.downloadUrl).let { uri ->
            when {
                uri.scheme == "file" -> File(uri.path!!)
                else -> null
            }
        }
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

        val filename = buildCacheFilename(mediaItem, track, providerId)
        val targetFile = File(cacheDir, filename)
        val tempFile = File(cacheDir, "$filename.part")

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
        val filename = buildCacheFilename(mediaItem, track, providerId)
        return File(cacheDir, filename).exists()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}