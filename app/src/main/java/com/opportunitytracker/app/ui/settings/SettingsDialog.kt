package com.opportunitytracker.app.ui.settings

import androidx.compose.runtime.Composable

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    SettingsScreen(
        onBack = onDismiss
    )
}
