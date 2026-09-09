package com.watermelon.subtitle.source

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.VideoQuery

/**
 * Offline sidecar-subtitle lookup seam.
 *
 * Extracted so [com.watermelon.subtitle.repository.SubtitleRepositoryImpl] can
 * be constructed with a fake in pure-JVM unit tests (the real
 * [LocalSidecarSourceImpl] needs an Android Context).
 */
interface SidecarSource {
    suspend fun findAndParse(query: VideoQuery): ParsedSubtitle?
}
