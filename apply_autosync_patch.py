#!/usr/bin/env python3
"""Apply the Watermelon automatic subtitle sync implementation to a main-branch source tree."""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

PATCH_ROOT = Path(__file__).resolve().parent

NEW_FILES = [
    "common-interfaces/src/main/kotlin/com/watermelon/common/subtitle/sync/SubtitleSyncContracts.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleSyncConfig.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleActivityBuilderImpl.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleProbeSelectorImpl.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/ActivityCorrelatorImpl.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/OffsetConsensus.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleFingerprintProvider.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleTimeMapper.kt",
    "subtitle-engine/src/main/kotlin/com/watermelon/subtitle/sync/SubtitleSyncCoordinator.kt",
    "subtitle-engine/src/test/kotlin/com/watermelon/subtitle/sync/SubtitleSyncCoreTest.kt",
    "media-tools/src/main/kotlin/com/watermelon/mediatools/subtitle/sync/SparseSpeechProbeSource.kt",
    "media-tools/src/main/kotlin/com/watermelon/mediatools/subtitle/sync/SpeechLikelihoodAccumulator.kt",
    "media-tools/src/test/kotlin/com/watermelon/mediatools/subtitle/sync/SpeechLikelihoodAccumulatorTest.kt",
    "library-storage/src/main/kotlin/com/watermelon/storage/db/migrations/MigrationV11ToV12.kt",
    "library-storage/src/main/kotlin/com/watermelon/storage/repository/SubtitleSyncRepositoryImpl.kt",
]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path} but found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def copy_new_files(repo: Path) -> None:
    for rel in NEW_FILES:
        src = PATCH_ROOT / rel
        dst = repo / rel
        if not src.is_file():
            raise RuntimeError(f"Patch source missing: {src}")
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)


def patch_database(repo: Path) -> None:
    p = repo / "library-storage/src/main/kotlin/com/watermelon/storage/db/WatermelonDatabase.kt"
    replace_once(
        p,
        "import com.watermelon.storage.db.migrations.MigrationV10ToV11\n",
        "import com.watermelon.storage.db.migrations.MigrationV10ToV11\n"
        "import com.watermelon.storage.db.migrations.MigrationV11ToV12\n",
    )
    replace_once(
        p,
        "                10 -> MigrationV10ToV11.migrate(db)\n",
        "                10 -> MigrationV10ToV11.migrate(db)\n"
        "                11 -> MigrationV11ToV12.migrate(db)\n",
    )
    replace_once(p, "const val DATABASE_VERSION = 11", "const val DATABASE_VERSION = 12")


def patch_settings(repo: Path) -> None:
    settings = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/screens/SettingsScreen.kt"
    replace_once(
        settings,
        "    val folderVisibility: Map<String, Boolean> = emptyMap(),\n"
        "    val subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),\n",
        "    val folderVisibility: Map<String, Boolean> = emptyMap(),\n"
        "    /** Runs sparse, local speech-timing probes after a subtitle is loaded. */\n"
        "    val autoSubtitleSyncEnabled: Boolean = true,\n"
        "    val subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),\n",
    )
    replace_once(
        settings,
        "                    ToggleRow(\n"
        "                        label = \"Enable subtitles\",\n"
        "                        checked = st.enabled\n"
        "                    ) { up(st.copy(enabled = it)) }\n",
        "                    ToggleRow(\n"
        "                        label = \"Automatically synchronize subtitles\",\n"
        "                        checked = state.autoSubtitleSyncEnabled\n"
        "                    ) { onStateChange(state.copy(autoSubtitleSyncEnabled = it)) }\n\n"
        "                    ToggleRow(\n"
        "                        label = \"Enable subtitles\",\n"
        "                        checked = st.enabled\n"
        "                    ) { up(st.copy(enabled = it)) }\n",
    )

    tv = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/tv/TvSettingsScreen.kt"
    replace_once(
        tv,
        "        val subtitleStyle = state.subtitleStyle\n"
        "        item {\n"
        "            TvToggleSetting(\n"
        "                label = \"Enable subtitles\",\n",
        "        val subtitleStyle = state.subtitleStyle\n"
        "        item {\n"
        "            TvToggleSetting(\n"
        "                label = \"Automatic subtitle sync\",\n"
        "                enabled = state.autoSubtitleSyncEnabled,\n"
        "                supportingText = \"Uses short local audio probes; audio is never uploaded.\",\n"
        "                onClick = {\n"
        "                    onStateChange(state.copy(autoSubtitleSyncEnabled = !state.autoSubtitleSyncEnabled))\n"
        "                }\n"
        "            )\n"
        "        }\n"
        "        item {\n"
        "            TvToggleSetting(\n"
        "                label = \"Enable subtitles\",\n",
    )

    persistence = repo / "app/src/main/kotlin/com/watermelon/app/SettingsPersistence.kt"
    replace_once(
        persistence,
        "    isPremiumUnlocked = prefs.getBoolean(\"mt_premium_unlocked\", true),\n"
        "    subtitleStyle = SubtitleStyle(\n",
        "    isPremiumUnlocked = prefs.getBoolean(\"mt_premium_unlocked\", true),\n"
        "    autoSubtitleSyncEnabled = prefs.getBoolean(\"subtitle_auto_sync_enabled\", true),\n"
        "    subtitleStyle = SubtitleStyle(\n",
    )
    replace_once(
        persistence,
        "        .putBoolean(\"mt_premium_unlocked\", state.isPremiumUnlocked)\n"
        "        .putBoolean(\"subtitle_enabled\", state.subtitleStyle.enabled)\n",
        "        .putBoolean(\"mt_premium_unlocked\", state.isPremiumUnlocked)\n"
        "        .putBoolean(\"subtitle_auto_sync_enabled\", state.autoSubtitleSyncEnabled)\n"
        "        .putBoolean(\"subtitle_enabled\", state.subtitleStyle.enabled)\n",
    )


def patch_phone_controls(repo: Path) -> None:
    panel = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/screens/PlayerControlPanel.kt"
    replace_once(
        panel,
        "    isBackground: Boolean,\n"
        "    hasSubtitleTrack: Boolean,\n"
        "    onSpeedChange: (Float) -> Unit,\n",
        "    isBackground: Boolean,\n"
        "    hasSubtitleTrack: Boolean,\n"
        "    subtitleOffsetMs: Long,\n"
        "    onSubtitleOffsetChange: (Long) -> Unit,\n"
        "    onSubtitleAutoSync: () -> Unit,\n"
        "    onSpeedChange: (Float) -> Unit,\n",
    )
    replace_once(
        panel,
        "        SheetAction(\n"
        "            label = \"Subtitle controls\",\n"
        "            detail = if (hasSubtitleTrack) {\n"
        "                \"Subtitle track is loaded; detailed controls are not available in this release.\"\n"
        "            } else {\n"
        "                \"No subtitle track is available for this video.\"\n"
        "            },\n"
        "            enabled = false,\n"
        "            onClick = {},\n"
        "        )\n",
        "        SheetDivider()\n"
        "        SheetSectionLabel(\"Subtitle timing\")\n"
        "        if (hasSubtitleTrack) {\n"
        "            SheetAction(\n"
        "                label = \"Subtitle −100 ms\",\n"
        "                detail = \"Current offset: ${formatSubtitleOffset(subtitleOffsetMs)}\",\n"
        "                onClick = { onSubtitleOffsetChange(subtitleOffsetMs - 100L) },\n"
        "            )\n"
        "            SheetAction(\n"
        "                label = \"Subtitle +100 ms\",\n"
        "                detail = \"Current offset: ${formatSubtitleOffset(subtitleOffsetMs)}\",\n"
        "                onClick = { onSubtitleOffsetChange(subtitleOffsetMs + 100L) },\n"
        "            )\n"
        "            SheetAction(\n"
        "                label = \"Auto-sync subtitle\",\n"
        "                detail = \"Clear the manual override and run sparse automatic synchronization.\",\n"
        "                onClick = onSubtitleAutoSync,\n"
        "            )\n"
        "        } else {\n"
        "            SheetAction(\n"
        "                label = \"Subtitle controls\",\n"
        "                detail = \"No subtitle track is available for this video.\",\n"
        "                enabled = false,\n"
        "                onClick = {},\n"
        "            )\n"
        "        }\n",
    )
    # Append a tiny formatter near existing sheet helpers.
    replace_once(
        panel,
        "@Composable\nprivate fun SheetTitle(text: String) {\n",
        "private fun formatSubtitleOffset(offsetMs: Long): String = when {\n"
        "    offsetMs > 0 -> \"+${offsetMs} ms\"\n"
        "    else -> \"${offsetMs} ms\"\n"
        "}\n\n"
        "@Composable\nprivate fun SheetTitle(text: String) {\n",
    )

    phone = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/screens/PhonePlayerScreen.kt"
    replace_once(
        phone,
        "    subtitleTrack: com.watermelon.common.model.ParsedSubtitle? = null,\n"
        "    subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),\n"
        "    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,\n",
        "    subtitleTrack: com.watermelon.common.model.ParsedSubtitle? = null,\n"
        "    subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),\n"
        "    onSubtitleOffsetChange: ((Long) -> Unit)? = null,\n"
        "    onSubtitleAutoSync: (() -> Unit)? = null,\n"
        "    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,\n",
    )
    replace_once(
        phone,
        "                    isBackground = isBackgroundEnabled,\n"
        "                    hasSubtitleTrack = subtitleTrack != null,\n"
        "                    onSpeedChange = { speed ->\n",
        "                    isBackground = isBackgroundEnabled,\n"
        "                    hasSubtitleTrack = subtitleTrack != null,\n"
        "                    subtitleOffsetMs = subtitleTrack?.offsetMs ?: 0L,\n"
        "                    onSubtitleOffsetChange = { onSubtitleOffsetChange?.invoke(it) },\n"
        "                    onSubtitleAutoSync = { onSubtitleAutoSync?.invoke() },\n"
        "                    onSpeedChange = { speed ->\n",
    )


def patch_tv_controls(repo: Path) -> None:
    controls = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/tv/TvPlayerControls.kt"
    replace_once(
        controls,
        "    hasSubtitles: Boolean,\n"
        "    onIntent: (UserIntent) -> Unit,\n"
        "    onSkipPrevious: () -> Unit,\n"
        "    onSkipNext: () -> Unit,\n"
        "    onSubtitleNudge: (Long) -> Unit,\n"
        "    onSeek: (direction: Int) -> Unit,\n",
        "    hasSubtitles: Boolean,\n"
        "    subtitleOffsetMs: Long,\n"
        "    onIntent: (UserIntent) -> Unit,\n"
        "    onSkipPrevious: () -> Unit,\n"
        "    onSkipNext: () -> Unit,\n"
        "    onSubtitleOffsetChange: (Long) -> Unit,\n"
        "    onSubtitleAutoSync: () -> Unit,\n"
        "    onSeek: (direction: Int) -> Unit,\n",
    )
    replace_once(
        controls,
        "                TvFocusableButton(\n"
        "                    label = \"Subtitles −100 ms\",\n"
        "                    onClick = { onSubtitleNudge(-100L) }\n"
        "                )\n"
        "                TvFocusableButton(\n"
        "                    label = \"Subtitles +100 ms\",\n"
        "                    onClick = { onSubtitleNudge(+100L) }\n"
        "                )\n",
        "                TvFocusableButton(\n"
        "                    label = \"Subtitles −100 ms\",\n"
        "                    onClick = { onSubtitleOffsetChange(subtitleOffsetMs - 100L) }\n"
        "                )\n"
        "                TvFocusableButton(\n"
        "                    label = \"Timing ${formatSubtitleOffset(subtitleOffsetMs)}\",\n"
        "                    onClick = onSubtitleAutoSync\n"
        "                )\n"
        "                TvFocusableButton(\n"
        "                    label = \"Subtitles +100 ms\",\n"
        "                    onClick = { onSubtitleOffsetChange(subtitleOffsetMs + 100L) }\n"
        "                )\n",
    )
    replace_once(
        controls,
        "/**\n * Shared focus treatment for TV controls:",
        "private fun formatSubtitleOffset(offsetMs: Long): String =\n"
        "    if (offsetMs > 0) \"+${offsetMs} ms\" else \"${offsetMs} ms\"\n\n"
        "/**\n * Shared focus treatment for TV controls:",
    )

    screen = repo / "ui-presentation/src/main/kotlin/com/watermelon/ui/tv/TvPlayerScreen.kt"
    replace_once(screen, "import androidx.compose.runtime.mutableLongStateOf\n", "")
    replace_once(screen, "import androidx.compose.runtime.setValue\n", "")
    replace_once(
        screen,
        "    subtitleTrack: ParsedSubtitle? = null,\n"
        "    subtitleStyle: SubtitleStyle = SubtitleStyle(),\n"
        "    modifier: Modifier = Modifier\n",
        "    subtitleTrack: ParsedSubtitle? = null,\n"
        "    subtitleStyle: SubtitleStyle = SubtitleStyle(),\n"
        "    onSubtitleOffsetChange: ((Long) -> Unit)? = null,\n"
        "    onSubtitleAutoSync: (() -> Unit)? = null,\n"
        "    modifier: Modifier = Modifier\n",
    )
    replace_once(
        screen,
        "    // Local, render-time-only subtitle offset nudge (Up/Down). Not persisted — matches the\n"
        "    // scope of what TvPlayerControls exposes today; wiring this into the storage-backed\n"
        "    // SubtitleOffsets table is a separate change shared with the phone screen, which doesn't\n"
        "    // yet expose a live re-nudge control either.\n"
        "    var liveOffsetMs by remember(subtitleTrack) { mutableLongStateOf(subtitleTrack?.offsetMs ?: 0L) }\n"
        "    val effectiveSubtitle = remember(subtitleTrack, liveOffsetMs) {\n"
        "        subtitleTrack?.copy(offsetMs = liveOffsetMs)\n"
        "    }\n\n",
        "",
    )
    replace_once(
        screen,
        "            val activeCue = remember(effectiveSubtitle, positionMs) { effectiveSubtitle?.cueAt(positionMs) }\n",
        "            val activeCue = remember(subtitleTrack, positionMs) { subtitleTrack?.cueAt(positionMs) }\n",
    )
    replace_once(
        screen,
        "                hasSubtitles = subtitleTrack != null,\n"
        "                onIntent = viewModel::onIntent,\n"
        "                onSkipPrevious = onSkipPrevious,\n"
        "                onSkipNext = onSkipNext,\n"
        "                onSubtitleNudge = { deltaMs -> liveOffsetMs += deltaMs },\n"
        "                onSeek = { direction ->\n",
        "                hasSubtitles = subtitleTrack != null,\n"
        "                subtitleOffsetMs = subtitleTrack?.offsetMs ?: 0L,\n"
        "                onIntent = viewModel::onIntent,\n"
        "                onSkipPrevious = onSkipPrevious,\n"
        "                onSkipNext = onSkipNext,\n"
        "                onSubtitleOffsetChange = { onSubtitleOffsetChange?.invoke(it) },\n"
        "                onSubtitleAutoSync = { onSubtitleAutoSync?.invoke() },\n"
        "                onSeek = { direction ->\n",
    )


def patch_main_activity(repo: Path) -> None:
    p = repo / "app/src/main/kotlin/com/watermelon/app/MainActivity.kt"
    replace_once(
        p,
        "    private val subtitleRepository by lazy {\n"
        "        com.watermelon.subtitle.repository.SubtitleRepositoryImpl(applicationContext)\n"
        "    }\n"
        "    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }\n",
        "    private val subtitleRepository by lazy {\n"
        "        com.watermelon.subtitle.repository.SubtitleRepositoryImpl(applicationContext)\n"
        "    }\n"
        "    private val subtitleSyncRepository by lazy {\n"
        "        com.watermelon.storage.repository.SubtitleSyncRepositoryImpl(database)\n"
        "    }\n"
        "    private val subtitleSyncConfig by lazy { com.watermelon.subtitle.sync.SubtitleSyncConfig() }\n"
        "    private val subtitleFingerprintProvider by lazy {\n"
        "        com.watermelon.subtitle.sync.SubtitleFingerprintProvider()\n"
        "    }\n"
        "    private val subtitleSyncCoordinator by lazy {\n"
        "        val config = subtitleSyncConfig\n"
        "        com.watermelon.subtitle.sync.SubtitleSyncCoordinator(\n"
        "            repository = subtitleSyncRepository,\n"
        "            probeSelector = com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl(config),\n"
        "            subtitleActivityBuilder = com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl(config),\n"
        "            speechProbeSource = com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource(applicationContext),\n"
        "            correlator = com.watermelon.subtitle.sync.ActivityCorrelatorImpl(),\n"
        "            consensus = com.watermelon.subtitle.sync.OffsetConsensus(config),\n"
        "            config = config,\n"
        "        )\n"
        "    }\n"
        "    private val playbackSessionCounter = java.util.concurrent.atomic.AtomicLong(0L)\n"
        "    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }\n",
    )

    old_state = '''                    val subtitleTrackState = run {\n                        var track by remember(mediaUri) {\n                            mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null)\n                        }\n                        LaunchedEffect(mediaUri) {\n                            track = discoverSubtitle(mediaUri)\n                        }\n                        track\n                    }\n'''
    new_state = '''                    var subtitleTrackState by remember(mediaUri) {\n                        mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null)\n                    }\n                    var subtitleFingerprintState by remember(mediaUri) { mutableStateOf<String?>(null) }\n                    var subtitleMediaState by remember(mediaUri) {\n                        mutableStateOf<com.watermelon.common.model.MediaItem?>(null)\n                    }\n                    var autoSyncRetryNonce by remember(mediaUri) { androidx.compose.runtime.mutableLongStateOf(0L) }\n                    val playbackSessionId = remember(mediaUri) { playbackSessionCounter.incrementAndGet() }\n\n                    LaunchedEffect(mediaUri) {\n                        subtitleTrackState = null\n                        subtitleFingerprintState = null\n                        subtitleMediaState = null\n                        val item = runCatching { mediaRepository.getByUri(mediaUri) }.getOrNull()\n                        val track = discoverSubtitle(mediaUri)\n                        if (item != null && track != null) {\n                            val fingerprint = subtitleFingerprintProvider.fingerprint(track)\n                            val profile = subtitleSyncRepository.get(item.uri, item.fileSize, fingerprint)\n                            val storedOffset = profile?.manualOffsetMs ?: if (\n                                profile?.autoEngineVersion == com.watermelon.subtitle.sync.SUBTITLE_SYNC_ENGINE_VERSION\n                            ) {\n                                when (val model = profile.autoModel) {\n                                    com.watermelon.common.subtitle.sync.SubtitleSyncModel.Identity -> 0L\n                                    is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Offset -> model.offsetMs\n                                    else -> null\n                                }\n                            } else null\n                            subtitleMediaState = item\n                            subtitleFingerprintState = fingerprint\n                            subtitleTrackState = track.copy(offsetMs = storedOffset ?: track.offsetMs)\n                        }\n                    }\n'''
    replace_once(p, old_state, new_state)

    anchor = '''                    if (isTelevision) {\n                        // TV is a separate composition (Manifest §8) — D-pad-first, no touch\n'''
    sync_block = '''                    LaunchedEffect(\n                        mediaUri,\n                        subtitleFingerprintState,\n                        settingsState.autoSubtitleSyncEnabled,\n                        autoSyncRetryNonce,\n                        durationMs,\n                    ) {\n                        val track = subtitleTrackState ?: return@LaunchedEffect\n                        val item = subtitleMediaState ?: return@LaunchedEffect\n                        val fingerprint = subtitleFingerprintState ?: return@LaunchedEffect\n                        if (!settingsState.autoSubtitleSyncEnabled && autoSyncRetryNonce == 0L) {\n                            return@LaunchedEffect\n                        }\n\n                        val profile = subtitleSyncRepository.get(item.uri, item.fileSize, fingerprint)\n                        if (profile?.manualOffsetMs != null) return@LaunchedEffect\n                        if (profile?.autoEngineVersion == com.watermelon.subtitle.sync.SUBTITLE_SYNC_ENGINE_VERSION &&\n                            profile.autoModel != null && autoSyncRetryNonce == 0L\n                        ) return@LaunchedEffect\n\n                        val mediaDuration = durationMs.takeIf { it > 0L } ?: item.durationMs\n                        if (mediaDuration <= 0L) return@LaunchedEffect\n\n                        val result = subtitleSyncCoordinator.synchronize(\n                            com.watermelon.common.subtitle.sync.SubtitleSyncRequest(\n                                mediaId = item.uri,\n                                mediaUri = item.uri,\n                                mediaFileSize = item.fileSize,\n                                mediaDurationMs = mediaDuration,\n                                subtitleFingerprint = fingerprint,\n                                subtitleLanguage = track.language,\n                                subtitle = track.copy(offsetMs = 0L),\n                                playbackSessionId = playbackSessionId,\n                            )\n                        )\n                        if (playbackSessionId != playbackSessionCounter.get()) return@LaunchedEffect\n\n                        when (result) {\n                            is com.watermelon.common.subtitle.sync.SubtitleSyncResult.Synchronized -> {\n                                val offset = when (val model = result.model) {\n                                    com.watermelon.common.subtitle.sync.SubtitleSyncModel.Identity -> 0L\n                                    is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Offset -> model.offsetMs\n                                    else -> null\n                                }\n                                if (offset != null) {\n                                    subtitleTrackState = subtitleTrackState?.copy(offsetMs = offset)\n                                }\n                            }\n                            is com.watermelon.common.subtitle.sync.SubtitleSyncResult.ComplexDriftDetected ->\n                                com.watermelon.common.util.FileLogger.i(\n                                    \"SubtitleAutoSync\", \"complex drift detected; constant offset not applied\"\n                                )\n                            is com.watermelon.common.subtitle.sync.SubtitleSyncResult.LowConfidence ->\n                                com.watermelon.common.util.FileLogger.i(\n                                    \"SubtitleAutoSync\", \"low confidence; subtitle left unchanged\"\n                                )\n                            else -> Unit\n                        }\n                    }\n\n                    val onSubtitleOffsetChange: (Long) -> Unit = { newOffset ->\n                        subtitleTrackState = subtitleTrackState?.copy(offsetMs = newOffset)\n                        val item = subtitleMediaState\n                        val fingerprint = subtitleFingerprintState\n                        if (item != null && fingerprint != null) {\n                            lifecycleScope.launch {\n                                runCatching {\n                                    subtitleSyncRepository.setManualOffset(\n                                        item.uri, item.fileSize, fingerprint, newOffset\n                                    )\n                                }.onFailure { error ->\n                                    com.watermelon.common.util.FileLogger.e(\n                                        \"SubtitleAutoSync\",\n                                        \"manual offset persistence failed: ${error.message}\"\n                                    )\n                                }\n                            }\n                        }\n                    }\n                    val onSubtitleAutoSync: () -> Unit = {\n                        val item = subtitleMediaState\n                        val fingerprint = subtitleFingerprintState\n                        if (item != null && fingerprint != null) {\n                            subtitleTrackState = subtitleTrackState?.copy(offsetMs = 0L)\n                            lifecycleScope.launch {\n                                runCatching {\n                                    subtitleSyncRepository.clearManualOffset(item.uri, item.fileSize, fingerprint)\n                                    subtitleSyncRepository.clearAutoResult(item.uri, item.fileSize, fingerprint)\n                                }.onSuccess {\n                                    autoSyncRetryNonce += 1L\n                                }.onFailure { error ->\n                                    com.watermelon.common.util.FileLogger.e(\n                                        \"SubtitleAutoSync\",\n                                        \"auto-sync reset failed: ${error.message}\"\n                                    )\n                                }\n                            }\n                        }\n                    }\n\n'''
    replace_once(p, anchor, sync_block + anchor)

    replace_once(
        p,
        "                            subtitleTrack = subtitleTrackState,\n"
        "                            subtitleStyle = settingsState.subtitleStyle,\n"
        "                            surface = { modifier ->\n",
        "                            subtitleTrack = subtitleTrackState,\n"
        "                            subtitleStyle = settingsState.subtitleStyle,\n"
        "                            onSubtitleOffsetChange = onSubtitleOffsetChange,\n"
        "                            onSubtitleAutoSync = onSubtitleAutoSync,\n"
        "                            surface = { modifier ->\n",
    )
    replace_once(
        p,
        "                        subtitleTrack = subtitleTrackState,\n"
        "                        uri = mediaUri,\n",
        "                        subtitleTrack = subtitleTrackState,\n"
        "                        onSubtitleOffsetChange = onSubtitleOffsetChange,\n"
        "                        onSubtitleAutoSync = onSubtitleAutoSync,\n"
        "                        uri = mediaUri,\n",
    )


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_autosync_patch.py /path/to/Watermelon-MediaPlayer-main")
    repo = Path(sys.argv[1]).resolve()
    if not (repo / "settings.gradle.kts").is_file():
        raise SystemExit(f"Not a Watermelon source root: {repo}")

    copy_new_files(repo)
    patch_database(repo)
    patch_settings(repo)
    patch_phone_controls(repo)
    patch_tv_controls(repo)
    patch_main_activity(repo)
    print(f"Watermelon Auto Sync patch applied to {repo}")


if __name__ == "__main__":
    main()
