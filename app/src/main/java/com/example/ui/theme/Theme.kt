package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TorrentClawShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun TorrentClawTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  accentPalette: AccentPalette = AccentPalette.ELECTRIC_CYAN,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val isDark =
    when (themeMode) {
      ThemeMode.SYSTEM -> systemDark
      ThemeMode.DARK,
      ThemeMode.AMOLED -> true
      ThemeMode.LIGHT -> false
    }
  val isAmoled = themeMode == ThemeMode.AMOLED

  val colorScheme =
    if (isDark) {
      darkColorScheme(
        primary = accentPalette.darkPrimary,
        onPrimary = accentPalette.darkOnPrimary,
        primaryContainer = accentPalette.darkContainer,
        onPrimaryContainer = Color.White,
        secondary = accentPalette.darkSecondary,
        onSecondary = Color(0xFF061523),
        secondaryContainer = if (isAmoled) AmoledSurfaceHigh else ObsidianSurfaceHigh,
        onSecondaryContainer = ObsidianOnSurface,
        tertiary = Color(0xFFCE93D8),
        background = if (isAmoled) AmoledBg else ObsidianBg,
        onBackground = ObsidianOnSurface,
        surface = if (isAmoled) AmoledSurface else ObsidianSurface,
        onSurface = ObsidianOnSurface,
        surfaceVariant = if (isAmoled) AmoledSurfaceHigh else ObsidianSurfaceHigh,
        onSurfaceVariant = ObsidianOnSurfaceMuted,
        surfaceContainerLowest = if (isAmoled) AmoledBg else ObsidianBg,
        surfaceContainerLow = if (isAmoled) AmoledSurface else ObsidianSurface,
        surfaceContainer = if (isAmoled) AmoledSurface else ObsidianSurface,
        surfaceContainerHigh = if (isAmoled) AmoledSurfaceHigh else ObsidianSurfaceHigh,
        outline = if (isAmoled) AmoledOutline else ObsidianOutline,
        outlineVariant = if (isAmoled) Color(0xFF161E2C) else Color(0xFF1E293B),
        error = Color(0xFFFF5252),
        onError = Color(0xFF2B0000),
      )
    } else {
      lightColorScheme(
        primary = accentPalette.lightPrimary,
        onPrimary = accentPalette.lightOnPrimary,
        primaryContainer = accentPalette.lightContainer,
        onPrimaryContainer = Color(0xFF001F25),
        secondary = accentPalette.lightSecondary,
        onSecondary = Color.White,
        secondaryContainer = LightSurfaceHigh,
        onSecondaryContainer = LightOnSurface,
        tertiary = Color(0xFF7B1FA2),
        background = LightBg,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceHigh,
        onSurfaceVariant = LightOnSurfaceMuted,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = LightBg,
        surfaceContainer = LightSurface,
        surfaceContainerHigh = LightSurfaceHigh,
        outline = LightOutline,
        outlineVariant = Color(0xFFE2E8F0),
        error = Color(0xFFC62828),
        onError = Color.White,
      )
    }

  val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalTorrentClawColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = TorrentClawTypography,
      shapes = TorrentClawShapes,
      content = content,
    )
  }
}
