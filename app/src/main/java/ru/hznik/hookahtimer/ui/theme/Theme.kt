package ru.hznik.hookahtimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GraphiteColorScheme = darkColorScheme(
    primary = PastelGreen,
    onPrimary = PastelGreenOnPrimary,
    primaryContainer = PastelGreenContainer,
    onPrimaryContainer = PastelGreenOnContainer,
    secondary = PastelSecondary,
    onSecondary = GraphiteBackground,
    secondaryContainer = PastelSecondaryContainer,
    onSecondaryContainer = GraphiteOnSurface,
    tertiary = PastelGreen,
    onTertiary = PastelGreenOnPrimary,
    background = GraphiteBackground,
    onBackground = GraphiteOnSurface,
    surface = GraphiteSurface,
    onSurface = GraphiteOnSurface,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = GraphiteOnSurfaceVariant,
    outline = GraphiteOutline,
    outlineVariant = GraphiteOutlineVariant,
    error = TimerError,
    onError = Color(0xFF690005),
    errorContainer = TimerErrorContainer,
    onErrorContainer = TimerOnErrorContainer,
    scrim = Color.Black,
)

@Composable
fun HookahTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraphiteColorScheme,
        typography = Typography,
        content = content,
    )
}
