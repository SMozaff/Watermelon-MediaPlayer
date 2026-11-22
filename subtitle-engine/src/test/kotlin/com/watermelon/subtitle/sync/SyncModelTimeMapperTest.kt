package com.watermelon.subtitle.sync

import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SyncAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModelTimeMapperTest {

    @Test
    fun identityPassesThroughAndClampsNegativeInput() {
        val mapper = SyncModelTimeMapper(SubtitleSyncModel.Identity)
        assertEquals(1_234L, mapper.map(1_234L))
        assertEquals(0L, mapper.map(-5L))
    }

    @Test
    fun offsetAddsAndClampsAtZero() {
        assertEquals(3_000L, SyncModelTimeMapper(SubtitleSyncModel.Offset(2_000L)).map(1_000L))
        assertEquals(0L, SyncModelTimeMapper(SubtitleSyncModel.Offset(-5_000L)).map(1_000L))
    }

    @Test
    fun affineScalesRoundsThenOffsetsAndClamps() {
        val mapper = SyncModelTimeMapper(SubtitleSyncModel.Affine(scale = 1.5, offsetMs = 100L))
        assertEquals(102L, mapper.map(1L)) // round(1.5) = 2, then + 100
        assertEquals(0L, SyncModelTimeMapper(SubtitleSyncModel.Affine(-1.0, 0L)).map(1L))
    }

    @Test
    fun affineRoundingBoundaryUsesHalfUp() {
        val mapper = SyncModelTimeMapper(SubtitleSyncModel.Affine(scale = 1.5, offsetMs = 0L))
        assertEquals(2L, mapper.map(1L)) // 1.5 -> 2
        assertEquals(5L, mapper.map(3L)) // 4.5 -> 5
    }

    @Test
    fun piecewiseEmptyAndSingleAnchorBehaveAsIdentityOrConstantOffset() {
        assertEquals(500L, SyncModelTimeMapper(SubtitleSyncModel.Piecewise(emptyList())).map(500L))
        val single = SubtitleSyncModel.Piecewise(listOf(SyncAnchor(1_000L, 2_500L)))
        assertEquals(2_000L, SyncModelTimeMapper(single).map(500L)) // +1,500 constant offset
        assertEquals(500L, SyncModelTimeMapper(single).map(-1_000L)) // clamped result of +500
    }

    @Test
    fun piecewiseInterpolatesInSortedAnchorOrderAndExtrapolatesAtEdges() {
        val mapper = SyncModelTimeMapper(
            SubtitleSyncModel.Piecewise(
                listOf(SyncAnchor(1_000L, 2_000L), SyncAnchor(0L, 0L)),
            ),
        )
        assertEquals(0L, mapper.map(-100L)) // before first anchor: +0 offset
        assertEquals(1_000L, mapper.map(500L))
        assertEquals(2_000L, mapper.map(1_000L))
        assertEquals(3_100L, mapper.map(2_100L)) // after last anchor: +1,000 offset
    }
}
