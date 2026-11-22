package com.watermelon.subtitle.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubtitleSyncConfigTest {

    @Test
    fun exposesDocumentedDefaultsAndEngineVersion() {
        val config = SubtitleSyncConfig()
        assertEquals(40, config.bucketMs)
        assertEquals(8_000L, config.probeDurationMs)
        assertEquals(3, config.maxProbeCount)
        assertEquals(30_000L, config.primarySearchMs)
        assertEquals(180_000L, config.expandedSearchMs)
        assertEquals(200L, config.expandedSearchStepMs)
        assertEquals(0.86f, config.autoApplyThreshold)
        assertEquals(1, SUBTITLE_SYNC_ENGINE_VERSION)
    }

    @Test
    fun rejectsOutOfRangeConstructorArguments() {
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(bucketMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(probeDurationMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(maxProbeCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(maxProbeCount = 9) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(primarySearchMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) {
            SubtitleSyncConfig(primarySearchMs = 10_000L, expandedSearchMs = 9_000L)
        }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(expandedSearchStepMs = 39L) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(autoApplyThreshold = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { SubtitleSyncConfig(autoApplyThreshold = 1.1f) }
    }

    @Test
    fun acceptsInclusiveValidationBoundaries() {
        assertEquals(1, SubtitleSyncConfig(maxProbeCount = 1).maxProbeCount)
        assertEquals(8, SubtitleSyncConfig(maxProbeCount = 8).maxProbeCount)
        val equalSearch = SubtitleSyncConfig(primarySearchMs = 10_000L, expandedSearchMs = 10_000L)
        assertEquals(10_000L, equalSearch.expandedSearchMs)
        assertEquals(40L, SubtitleSyncConfig(expandedSearchStepMs = 40L).expandedSearchStepMs)
        assertEquals(0f, SubtitleSyncConfig(autoApplyThreshold = 0f).autoApplyThreshold)
        assertEquals(1f, SubtitleSyncConfig(autoApplyThreshold = 1f).autoApplyThreshold)
    }
}
