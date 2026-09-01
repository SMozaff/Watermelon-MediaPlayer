package com.watermelon.subtitle.sync

import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import kotlin.math.roundToLong

interface SubtitleTimeMapper {
    fun map(originalTimestampMs: Long): Long
}

class SyncModelTimeMapper(
    private val model: SubtitleSyncModel,
) : SubtitleTimeMapper {
    override fun map(originalTimestampMs: Long): Long = when (model) {
        SubtitleSyncModel.Identity -> originalTimestampMs
        is SubtitleSyncModel.Offset -> originalTimestampMs + model.offsetMs
        is SubtitleSyncModel.Affine -> (originalTimestampMs * model.scale).roundToLong() + model.offsetMs
        is SubtitleSyncModel.Piecewise -> mapPiecewise(originalTimestampMs, model)
    }.coerceAtLeast(0L)

    private fun mapPiecewise(timeMs: Long, model: SubtitleSyncModel.Piecewise): Long {
        val anchors = model.anchors.sortedBy { it.subtitleTimeMs }
        if (anchors.isEmpty()) return timeMs
        if (anchors.size == 1) return timeMs + (anchors[0].mediaTimeMs - anchors[0].subtitleTimeMs)
        if (timeMs <= anchors.first().subtitleTimeMs) {
            val a = anchors[0]
            return timeMs + (a.mediaTimeMs - a.subtitleTimeMs)
        }
        if (timeMs >= anchors.last().subtitleTimeMs) {
            val a = anchors.last()
            return timeMs + (a.mediaTimeMs - a.subtitleTimeMs)
        }
        val rightIndex = anchors.indexOfFirst { it.subtitleTimeMs >= timeMs }
        val left = anchors[rightIndex - 1]
        val right = anchors[rightIndex]
        val span = (right.subtitleTimeMs - left.subtitleTimeMs).coerceAtLeast(1L)
        val t = (timeMs - left.subtitleTimeMs).toDouble() / span.toDouble()
        return (left.mediaTimeMs + t * (right.mediaTimeMs - left.mediaTimeMs)).roundToLong()
    }
}
