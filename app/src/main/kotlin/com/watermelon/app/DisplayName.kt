package com.watermelon.app

import androidx.activity.runCatching
import com.watermelon.common.model.MediaRepository
import com.watermelon.common.model.MediaRepositoryImpl
import com.watermelon.common.model.MediaItem

/**
 * Resolves display name for a media URI.
 * Uses [mediaRepository] to look up the display name, falling back to extracting
 * the last path segment from the URI itself.
 *
 * The duplicate pattern found across the codebase:
 *   runCatching { mediaRepository.getByUri(mediaUri)?.displayName }.getOrNull()
 *       ?: mediaUri.substringAfterLast('/')
 *
 * This function centralizes that logic in one place.
 */
fun String.resolveDisplayName(
    mediaRepository: MediaRepositoryImpl = com.watermelon.app.NavHostKt.defaultMediaRepository
): String {
    return runCatching {
        mediaRepository.getByUri(this)?.displayName
    }.getOrNull() ?: this.substringAfterLast('/')
}

/**
 * Overload that accepts a [MediaRepository] instance for cases where the repository
 * is not the default [MediaRepositoryImpl].
 */
fun String.resolveDisplayName(
    mediaRepository: MediaRepository
): String {
    return runCatching {
        mediaRepository.getByUri(this)?.displayName
    }.getOrNull() ?: this.substringAfterLast('/')
}