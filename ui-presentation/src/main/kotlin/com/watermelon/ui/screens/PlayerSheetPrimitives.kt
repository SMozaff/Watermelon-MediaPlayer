package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Shared visual language for the player's modal sheets ([PlayerActionsSheet], [QuickToolsSheet],
 * [FileActionsSheet], [OnlineSubtitlesSheet]).
 *
 * These five composables were previously declared `private` **twice** — once in
 * `PlayerControlPanel.kt` and again, byte-for-byte, in `OnlineSubtitlesSheet.kt`. They compiled
 * only because `private` scopes them to the file, which meant the player had two independent
 * copies of the same sheet chrome that could silently drift apart.
 *
 * They now live here as `internal`, so the player has exactly one definition. Sizes come from
 * the [WatermelonTypography] scale rather than hardcoded `sp` literals, which is what let the two
 * copies disagree in the first place.
 */

/** Sheet headline. Reads the `titleLarge` slot, so it matches every other sheet and dialog title. */
@Composable
internal fun SheetTitle(text: String) {
    Text(
        text = text,
        style = WatermelonTypography.typography.titleLarge,
        color = PlayerColors.current.textPrimary,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
    )
}

/** Group heading inside a sheet, above a run of related actions. */
@Composable
internal fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = WatermelonTypography.typography.bodyMedium,
        color = PlayerColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
    )
}

/** Hairline separator between sheet groups. */
@Composable
internal fun SheetDivider() {
    HorizontalDivider(
        color = PlayerColors.current.textPrimary.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = WatermelonSpacing.xs),
    )
}

/**
 * A full-width sheet row: label with an optional explanatory second line.
 *
 * [destructive] tints the label with the brand accent so irreversible entries ("Delete from
 * device") are distinguishable by more than their wording.
 */
@Composable
internal fun SheetAction(
    label: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    color = when {
                        destructive -> PlayerColors.current.accent
                        enabled -> PlayerColors.current.textPrimary
                        else -> PlayerColors.current.iconInactive
                    },
                )
            },
            supportingContent = {
                Text(
                    text = detail,
                    color = if (enabled) {
                        PlayerColors.current.textSecondary
                    } else {
                        PlayerColors.current.iconInactive
                    },
                )
            },
        )
    }
}

/** Trailing inset so the last row clears the sheet's bottom edge and system gesture area. */
@Composable
internal fun SheetBottomSpace() {
    Column(modifier = Modifier.padding(bottom = WatermelonSpacing.lg)) {}
}