package com.farcast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark only, intentionally — not "dark mode as an option," this IS the
 * design (a night-ops console doesn't have a light variant any more than a
 * cockpit instrument panel does). MaterialTheme's light/dark switching
 * machinery isn't used; FarcastColorScheme is the only scheme.
 */
private val FarcastColorScheme = darkColorScheme(
    background = FarcastBackground,
    surface = FarcastPanel,
    surfaceVariant = FarcastPanelRaised,
    primary = FarcastAmber,
    secondary = FarcastCyan,
    error = FarcastBrick,
    onBackground = FarcastTextPrimary,
    onSurface = FarcastTextPrimary,
    onPrimary = FarcastBackground,   // text on an amber button: dark-on-amber for contrast, not white-on-amber
    outline = FarcastHairline
)

@Composable
fun FarcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FarcastColorScheme,
        typography = FarcastTypography,
        shapes = FarcastShapes,
        content = content
    )
}
