package com.watermelon.app

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen permission prompt shown while media access is missing. Extracted from
 * MainActivity in Phase 2b. Swaps its action once the denial is permanent: the
 * system dialog would silently do nothing at that point, so the only recovery is
 * deep-linking into this app's system settings page.
 */
@Composable
internal fun PermissionPrompt(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.permission_permanently_denied_hint
                    else R.string.permission_rationale
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.permission_open_settings
                        else R.string.permission_grant
                    )
                )
            }
        }
    }
}

/** Deep-links into this app's page in system settings, the only path that can lift a
 *  permanently-denied media permission. */
internal fun ComponentActivity.openPermissionSettings() {
    runCatching {
        startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
