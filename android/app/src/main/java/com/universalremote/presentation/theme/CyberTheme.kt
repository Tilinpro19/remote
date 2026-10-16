package com.universalremote.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CyberColorScheme = darkColorScheme(
    primary = CyberColors.Primary,
    onPrimary = CyberColors.OnPrimary,
    primaryContainer = CyberColors.PrimaryContainer,
    onPrimaryContainer = CyberColors.OnPrimaryContainer,
    inversePrimary = CyberColors.PrimaryContainer,

    secondary = CyberColors.Secondary,
    onSecondary = CyberColors.OnSecondary,
    secondaryContainer = CyberColors.SecondaryContainer,
    onSecondaryContainer = CyberColors.OnSecondaryContainer,

    tertiary = CyberColors.Tertiary,
    onTertiary = CyberColors.OnTertiary,
    tertiaryContainer = CyberColors.TertiaryContainer,
    onTertiaryContainer = CyberColors.OnTertiaryContainer,

    background = CyberColors.Surface,
    onBackground = CyberColors.OnSurface,
    surface = CyberColors.Surface,
    onSurface = CyberColors.OnSurface,
    surfaceVariant = CyberColors.SurfaceContainerHigh,
    onSurfaceVariant = CyberColors.OnSurfaceVariant,
    surfaceTint = CyberColors.SurfaceTint,

    outline = CyberColors.Outline,
    outlineVariant = CyberColors.OutlineVariant,

    error = CyberColors.Error,
    onError = CyberColors.OnError,
    errorContainer = CyberColors.ErrorContainer,
    onErrorContainer = CyberColors.OnErrorContainer,
)

val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CyberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = CyberTypography,
        shapes = CyberShapes,
        content = content,
    )
}
