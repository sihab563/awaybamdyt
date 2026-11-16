package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusPurpleGlow,
    onPrimary = Color(0xFF120324),
    primaryContainer = NexusPurpleContainer,
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = NexusBlue,
    onSecondary = Color(0xFF031530),
    secondaryContainer = NexusBlueContainer,
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = NexusCyan,
    onTertiary = Color(0xFF032333),
    tertiaryContainer = Color(0xFF0D2E42),
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = NexusObsidianBg,
    onBackground = TextPrimary,
    surface = NexusChatBg,
    onSurface = TextPrimary,
    surfaceVariant = NexusElevatedSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = NexusObsidianBg,
    surfaceContainerLow = NexusSidebarBg,
    surfaceContainer = NexusChatBg,
    surfaceContainerHigh = NexusElevatedSurface,
    surfaceContainerHighest = NexusHighestSurface,
    outline = NexusOutline,
    outlineVariant = Color(0xFF211E36),
    error = NexusDanger,
    onError = Color.White,
    errorContainer = NexusDangerContainer,
    onErrorContainer = Color(0xFFFFD6D9)
)

private val NexusLightColorScheme = lightColorScheme(
    primary = NexusPurpleLight,
    onPrimary = Color.White,
    primaryContainer = NexusPurpleLightContainer,
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = NexusBlueLight,
    onSecondary = Color.White,
    secondaryContainer = NexusBlueLightContainer,
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = NexusCyanLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceHigh,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightSurfaceHigh,
    surfaceContainerHigh = DaylightSurfaceHighest,
    surfaceContainerHighest = Color(0xFFD2D6EB),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E5F3),
    error = NexusDangerLight,
    onError = Color.White,
    errorContainer = NexusDangerLightContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

val NexusShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GamerHubTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NexusDarkColorScheme else NexusLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GamerHubTypography,
        shapes = NexusShapes,
        content = content
    )
}
