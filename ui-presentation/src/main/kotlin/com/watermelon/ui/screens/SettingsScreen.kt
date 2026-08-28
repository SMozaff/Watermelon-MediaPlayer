package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonIcon
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

data class SettingsState(
    val pureDark: Boolean = true,
    val forcedRtl: Boolean = false,
    val gridDefault: Boolean = false,
    val showThumbnails: Boolean = true,
    val showDurations: Boolean = true,
    val showFileSize: Boolean = false,
    val vhsEnabled: Boolean = true,
    val vhsIntensity: VhsIntensity = VhsIntensity.MED,
    val tunerSeekBarEnabled: Boolean = true,
    /** Seconds moved per tick on the VHS tuner seek bar (1-20). Each tick crossing the
     *  center pointer moves playback by exactly this many seconds — a fixed, predictable
     *  step regardless of video length, rather than a free-form pixel-drag mapping (which
     *  made long videos practically unreachable: a fixed px-per-ms ratio meant reaching
     *  the end of a 2-hour video required dragging tens of thousands of pixels in one
     *  continuous gesture). */
    val tunerSeekStepSeconds: Int = 5,
    val memorySafety: Boolean = false,
    val fullFolderAccess: Boolean = false,
    val screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    val folderVisibility: Map<String, Boolean> = emptyMap(),
    val subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),
    /** When false, Continue Watching is hidden from the Playlists screen — but resume
     *  positions keep being recorded in the background regardless, so nothing is lost if
     *  the user re-enables it later. */
    val continueWatchingEnabled: Boolean = true,
    /** media-tools output paths (MediaStore RELATIVE_PATH strings). See OutputFileStore's
     *  doc for the API<29 limitation: custom subfolders are silently ignored on those OS
     *  versions, so the settings UI should surface that rather than pretend it works. */
    val mp3OutputPath: String = "Music/Watermelon",
    val compressedOutputPath: String = "Movies/Watermelon/compressed",
    val trimmedOutputPath: String = "Movies/Watermelon/trimmed",
    /** Phase 5 gating flag -- gating itself is currently disabled at every call site
     *  (product decision: full-featured for now), so this defaults to true to match
     *  actual behavior. Toggle still persists/works if you want to test the gated UI
     *  path later without re-wiring the checks. */
    val isPremiumUnlocked: Boolean = true,
)

enum class VhsIntensity { OFF, LOW, MED, HIGH }
enum class ScreenshotMode { SINGLE, BURST }

@Composable
fun SettingsScreen(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    onFolderVisibilityClick: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = "Settings",
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = WatermelonSpacing.md,
                vertical = WatermelonSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            item { SettingsIntro() }
            item {
                SettingsGroup(title = "Appearance", summary = "Theme and reading direction") {
                    ToggleRow(
                        label = "Pure dark theme",
                        checked = state.pureDark
                    ) { onStateChange(state.copy(pureDark = it)) }

                    ToggleRow(
                        label = "Force RTL overrides",
                        checked = state.forcedRtl
                    ) { onStateChange(state.copy(forcedRtl = it)) }
                }
            }

            item {
                SettingsGroup(title = "Library & browsing", summary = "How your media library is displayed") {
                    ToggleRow(
                        label = "Grid layout by default",
                        checked = state.gridDefault
                    ) { onStateChange(state.copy(gridDefault = it)) }

                    ToggleRow(
                        label = "Show thumbnails",
                        checked = state.showThumbnails
                    ) { onStateChange(state.copy(showThumbnails = it)) }

                    ToggleRow(
                        label = "Show durations",
                        checked = state.showDurations
                    ) { onStateChange(state.copy(showDurations = it)) }

                    ToggleRow(
                        label = "Show file size",
                        checked = state.showFileSize
                    ) { onStateChange(state.copy(showFileSize = it)) }
                }
            }

            item {
                SettingsGroup(title = "Continue watching", summary = "Resume and playlist behaviour") {
                    ToggleRow(
                        label = "Continue Watching playlist",
                        checked = state.continueWatchingEnabled
                    ) { onStateChange(state.copy(continueWatchingEnabled = it)) }
                }
            }

            item {
                SettingsGroup(title = "Media tools", summary = "Export destinations and premium tools") {
                    ToggleRow(
                        label = "Premium unlocked (placeholder -- no purchase flow yet)",
                        checked = state.isPremiumUnlocked
                    ) { onStateChange(state.copy(isPremiumUnlocked = it)) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        TextFieldRow(
                            label = "MP3 audio folder",
                            value = state.mp3OutputPath,
                            onValueChange = { onStateChange(state.copy(mp3OutputPath = it)) },
                        )
                        TextFieldRow(
                            label = "Compressed video folder",
                            value = state.compressedOutputPath,
                            onValueChange = { onStateChange(state.copy(compressedOutputPath = it)) },
                        )
                        TextFieldRow(
                            label = "Trimmed video folder",
                            value = state.trimmedOutputPath,
                            onValueChange = { onStateChange(state.copy(trimmedOutputPath = it)) },
                        )
                    } else {
                        // Custom RELATIVE_PATH subfolders are silently ignored by
                        // MediaStore.insert() below API 29 (see OutputFileStore's doc) --
                        // showing editable fields that silently fail to apply would be
                        // worse than not showing them, so this note replaces the fields
                        // entirely on those OS versions rather than accepting input we
                        // can't honor.
                        Text(
                            text = "Custom folders require Android 10 or later. Compressed and " +
                                "trimmed videos will save to the default Movies location on this device.",
                            style = WatermelonTypography.typography.bodySmall,
                            color = WatermelonColors.DarkOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Player", summary = "Playback controls and retro effects") {
                    ToggleRow(
                        label = "Burst screenshot (9 frames)",
                        checked = state.screenshotMode == ScreenshotMode.BURST
                    ) {
                        onStateChange(
                            state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
                        )
                    }

                    ToggleRow(
                        label = "VHS effect",
                        checked = state.vhsEnabled
                    ) { onStateChange(state.copy(vhsEnabled = it)) }

                    DropdownNavRow(
                        label = "VHS intensity",
                        value = state.vhsIntensity.name.lowercase().replaceFirstChar { it.uppercase() },
                        options = VhsIntensity.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    ) { selected ->
                        val next = VhsIntensity.values().first {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                        }
                        onStateChange(state.copy(vhsIntensity = next))
                    }

                    ToggleRow(
                        label = "Tuner-style seek bar",
                        checked = state.tunerSeekBarEnabled
                    ) { onStateChange(state.copy(tunerSeekBarEnabled = it)) }

                    if (state.tunerSeekBarEnabled) {
                        StepperRow(
                            label = "Tuner seek step",
                            value = "${state.tunerSeekStepSeconds}s",
                            onMinus = {
                                onStateChange(
                                    state.copy(tunerSeekStepSeconds = (state.tunerSeekStepSeconds - 1).coerceAtLeast(1))
                                )
                            },
                            onPlus = {
                                onStateChange(
                                    state.copy(tunerSeekStepSeconds = (state.tunerSeekStepSeconds + 1).coerceAtMost(20))
                                )
                            }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Subtitles", summary = "Text style and reading direction") {
                    val st = state.subtitleStyle
                    fun up(new: com.watermelon.common.model.SubtitleStyle) {
                        onStateChange(state.copy(subtitleStyle = new))
                    }

                    ToggleRow(
                        label = "Enable subtitles",
                        checked = st.enabled
                    ) { up(st.copy(enabled = it)) }

                    StepperRow(
                        label = "Text size",
                        value = "${st.sizeSp}sp",
                        onMinus = { up(st.copy(sizeSp = (st.sizeSp - 2).coerceAtLeast(12))) },
                        onPlus = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) }
                    )

                    DropdownNavRow(
                        label = "Text color",
                        value = subtitleColorName(st.textColorArgb),
                        options = SUBTITLE_COLORS.map { it.second }
                    ) { selected ->
                        val argb = SUBTITLE_COLORS.first { it.second == selected }.first
                        up(st.copy(textColorArgb = argb))
                    }

                    DropdownNavRow(
                        label = "Position",
                        value = st.position.name.lowercase().replaceFirstChar { it.uppercase() },
                        options = com.watermelon.common.model.SubtitlePosition.values()
                            .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitlePosition.values().first {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                        }
                        up(st.copy(position = next))
                    }

                    ToggleRow(label = "Bold", checked = st.bold) { up(st.copy(bold = it)) }
                    ToggleRow(label = "Italic", checked = st.italic) { up(st.copy(italic = it)) }
                    ToggleRow(label = "Underline", checked = st.underline) { up(st.copy(underline = it)) }

                    DropdownNavRow(
                        label = "Direction",
                        value = st.direction.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                        up(st.copy(direction = next))
                    }

                    DropdownNavRow(
                        label = "2nd sub direction",
                        value = st.secondaryDirection.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                        up(st.copy(secondaryDirection = next))
                    }
                }
            }

            item {
                SettingsGroup(title = "Library access", summary = "Choose which indexed folders appear in Watermelon") {
                    NavRow(
                        label = "Folder visibility",
                        value = "Manage",
                        onClick = onFolderVisibilityClick
                    )
                }
            }

            item {
                SettingsGroup(title = "Privacy", summary = "What network access is used for") {
                    Text(
                        text = stringResource(R.string.settings_privacy_internet_usage),
                        style = WatermelonTypography.typography.bodySmall,
                        color = WatermelonColors.DarkOnSurfaceVariant,
                        modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIntro() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = WatermelonColors.Accent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(WatermelonSpacing.lg)) {
            Text(
                text = "Make Watermelon yours",
                style = WatermelonTypography.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WatermelonColors.Palette.PaperWhite
            )
            Text(
                text = "Tune playback, subtitles, library browsing and exports from one place.",
                style = WatermelonTypography.typography.bodyMedium,
                color = WatermelonColors.Palette.PaperWhite.copy(alpha = 0.84f),
                modifier = Modifier.padding(top = WatermelonSpacing.xs)
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    summary: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary,
                        style = WatermelonTypography.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = WatermelonSpacing.xs / 2)
                    )
                }
                WatermelonIcon(
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else -90f }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Column(
                        modifier = Modifier.padding(
                            horizontal = WatermelonSpacing.md,
                            vertical = WatermelonSpacing.xs / 2
                        )
                    ) { content() }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(
                value = checked,
                onValueChange = onChange,
                role = Role.Switch
            )
            .padding(vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = WatermelonSpacing.md)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WatermelonColors.Palette.PaperWhite,
                checkedTrackColor = WatermelonColors.Accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = WatermelonSpacing.sm)) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WatermelonSpacing.xs),
            shape = RoundedCornerShape(12.dp)
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = WatermelonTypography.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WatermelonSpacing.xs)
            )
        }
    }
}

@Composable
private fun DropdownNavRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(role = Role.Button) { expanded = true }
                .padding(vertical = WatermelonSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = WatermelonTypography.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = WatermelonTypography.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WatermelonIcon(
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = WatermelonSpacing.xs)
                        .graphicsLayer(rotationZ = -90f)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = WatermelonTypography.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = WatermelonTypography.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            WatermelonIcon(
                icon = R.drawable.ic_arrow_back,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = WatermelonSpacing.xs)
                    .graphicsLayer(rotationZ = 180f)
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    text = "-",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = value,
                style = WatermelonTypography.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = WatermelonSpacing.sm)
            )
            TextButton(onClick = onPlus, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    text = "+",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFFL to "White",
    0xFFFFEB3BL to "Yellow",
    0xFF00E5FFL to "Cyan",
    0xFF69F0AEL to "Green",
    0xFFFF8A80L to "Coral",
    0xFF000000L to "Black"
)

private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }?.second ?: "Custom"

private fun com.watermelon.common.model.SubtitleDirection.label(): String = when (this) {
    com.watermelon.common.model.SubtitleDirection.AUTO -> "Auto"
    com.watermelon.common.model.SubtitleDirection.FORCE_RTL -> "Force RTL"
    com.watermelon.common.model.SubtitleDirection.FORCE_LTR -> "Force LTR"
}
