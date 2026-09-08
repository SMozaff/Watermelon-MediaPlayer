package com.watermelon.subtitle.cache

import android.net.Uri
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import java.io.File
import java.security.MessageDigest

/**
 * Media-keyed subtitle cache store.
 *
 * Subtitles are stored under app-private cache directory with deterministic filenames:
 * <mediaSha256>.<language>.<providerId>.<remoteId-or-trackKey>.srt
 *
 * Key properties:
 * - Different videos can never share a subtitle merely because labels collide
 * - Same-video cache is discoverable after process restart
 * - Preferred-language ordering is deterministic
 * - Partial downloads are not treated as valid cache entries
 * - Writes use atomic rename from .part temporary file
 * - Maximum subtitle file size capped at 5 MiB
 */
class SubtitleCacheStore(
    private val cacheDir: File
) {
    companion object {
        private const val MAX_SUBTITLE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MiB
    }

    init {
        cacheDir.mkdirs()
    }

    /**
     * Compute SHA-256 hash of media URI for collision-resistant media keying.
     */
    private fun mediaKey(uri: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Sanitize a string component for use in filename.
     * Replaces unsafe characters with underscores, limits length.
     */
    private fun sanitizeFilenameComponent(component: String): String {
        return component
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(64) // Limit component length
    }

    /**
     * Generate cache filename for a subtitle track belonging to [mediaItem].
     * Format: <mediaSha256>.<language>.<providerId>.<remoteId>.srt
     */
    private fun buildCacheFilename(
        mediaItem: MediaItem,
        track: SubtitleTrack,
        providerId: String
    ): String {
        val mediaSha = mediaKey(mediaItem.uri)
        val language = sanitizeFilenameComponent(track.language)
        val safeProvider = sanitizeFilenameComponent(providerId)
        
        // Use remoteFileId if available, otherwise fall back to track label hash
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
        val files = cacheDir.listFiles { f -> 
            f.isFile && 
            f.name.startsWith(prefix) && 
            f.name.endsWith(".srt") && 
            !f.name.endsWith(".part")
        } ?: return emptyList()

        return files.mapNotNull { file ->
            // Parse filename: <mediaSha>.<language>.<provider>.<remoteId>.srt
            val parts = file.nameWithoutExtension.split('.')
            if (parts.size < 4) return@mapNotNull null
            
            val lang = parts.getOrElse(1) { "" }
            val providerId = parts.getOrElse(2) { "" }
            val remoteIdStr = parts.getOrElse(3) { "" }
            
            if (lang.isEmpty()) return@mapNotNull null
            
            // Filter by preferred languages if specified
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
            // Write to temporary file first
            tempFile.writeBytes(bytes)
            
            // Atomic rename to final location
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename temporary file to $targetFile")
            }
            
            return targetFile
        } catch (e: Exception) {
            // Clean up temporary file on failure
            tempFile.delete()
            throw e
        }
    }

    /**
     * Check if a specific subtitle is already cached.
     */
    fun isCached(mediaItem: MediaItem, track: SubtitleTrack, providerId: String): Boolean {
        val filename = buildCacheFilename(mediaItem, track, providerId)
        return File(cacheDir, filename).exists()
    }

    /**
     * Clear all cached subtitles (useful for testing or user-initiated cache clear).
     */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
