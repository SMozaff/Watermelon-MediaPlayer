package com.watermelon.subtitle.sync

data class SubtitleSyncConfig(
    val bucketMs: Int = 40,
    val probeDurationMs: Long = 8_000L,
    val maxProbeCount: Int = 3,
    val primarySearchMs: Long = 30_000L,
    val expandedSearchMs: Long = 180_000L,
    val expandedSearchStepMs: Long = 200L,
    val refinementRadiusMs: Long = 2_000L,
    val subtitleOnsetBlurMs: Long = 200L,
    val minimumTransitions: Int = 3,
    val maximumConsensusSpreadMs: Long = 300L,
    val identityToleranceMs: Long = 150L,
    val autoApplyThreshold: Float = 0.86f,
    val minimumGoodProbes: Int = 2,
    val minimumProbeQuality: Float = 0.50f,
    val minimumCorrelationScore: Float = 0.40f,
    val minimumPeakMargin: Float = 0.035f,
    val sdhWeight: Float = 0.25f,
) {
    init {
        require(bucketMs > 0)
        require(probeDurationMs > 0)
        require(maxProbeCount in 1..8)
        require(primarySearchMs > 0)
        require(expandedSearchMs >= primarySearchMs)
        require(expandedSearchStepMs >= bucketMs)
        require(autoApplyThreshold in 0f..1f)
    }
}

const val SUBTITLE_SYNC_ENGINE_VERSION = 1
