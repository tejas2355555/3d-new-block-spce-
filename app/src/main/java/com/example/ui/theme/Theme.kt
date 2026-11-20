package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CosmicColorScheme = darkColorScheme(
    primary = VoxelEmerald,
    onPrimary = TextPrimary,
    primaryContainer = VoxelEmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = SkyCyan,
    onSecondary = DeepSpaceNavy,
    secondaryContainer = CosmicPanel,
    onSecondaryContainer = TextPrimary,
    tertiary = StarGold,
    onTertiary = StarGoldDark,
    background = DeepSpaceNavy,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicPanel,
    onSurfaceVariant = TextSecondary,
    error = EmberCoral,
    onError = TextPrimary,
    outline = CosmicBorder
)

private val CosmicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BlockSpaceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = BlockSpaceTypography,
        shapes = CosmicShapes,
        content = content
    )
}
