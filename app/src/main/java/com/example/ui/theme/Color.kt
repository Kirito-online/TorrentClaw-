package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  DARK("Dark"),
  LIGHT("Light"),
  AMOLED("AMOLED Black"),
}

enum class AccentPalette(
  val label: String,
  val darkPrimary: Color,
  val darkOnPrimary: Color,
  val darkContainer: Color,
  val darkSecondary: Color,
  val lightPrimary: Color,
  val lightOnPrimary: Color,
  val lightContainer: Color,
  val lightSecondary: Color,
) {
  ELECTRIC_CYAN(
    label = "Electric Cyan",
    darkPrimary = Color(0xFF00E5FF),
    darkOnPrimary = Color(0xFF002026),
    darkContainer = Color(0xFF004F5B),
    darkSecondary = Color(0xFF64B5F6),
    lightPrimary = Color(0xFF006876),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFF9DF0FF),
    lightSecondary = Color(0xFF1565C0),
  ),
  AMBER_GOLD(
    label = "Cinema Gold",
    darkPrimary = Color(0xFFFFB74D),
    darkOnPrimary = Color(0xFF2B1700),
    darkContainer = Color(0xFF5C3500),
    darkSecondary = Color(0xFFFF8A65),
    lightPrimary = Color(0xFF8C4F00),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDDB7),
    lightSecondary = Color(0xFFBF360C),
  ),
  CRIMSON_CORAL(
    label = "Crimson Coral",
    darkPrimary = Color(0xFFFF6B6B),
    darkOnPrimary = Color(0xFF330006),
    darkContainer = Color(0xFF660012),
    darkSecondary = Color(0xFFFFB74D),
    lightPrimary = Color(0xFFBA1A2D),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDAD9),
    lightSecondary = Color(0xFFB26A00),
  ),
  EMERALD_MINT(
    label = "Emerald Mint",
    darkPrimary = Color(0xFF2EE59D),
    darkOnPrimary = Color(0xFF002112),
    darkContainer = Color(0xFF005132),
    darkSecondary = Color(0xFF4DD0E1),
    lightPrimary = Color(0xFF006C44),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFF7FF8BC),
    lightSecondary = Color(0xFF006874),
  ),
  VIOLET_NEON(
    label = "Violet Pulse",
    darkPrimary = Color(0xFFB388FF),
    darkOnPrimary = Color(0xFF21005D),
    darkContainer = Color(0xFF4527A0),
    darkSecondary = Color(0xFF80D8FF),
    lightPrimary = Color(0xFF5E35B1),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE9DDFF),
    lightSecondary = Color(0xFF0277BD),
  ),
}

// Base Neutral Surfaces
val ObsidianBg = Color(0xFF0B111E)
val ObsidianSurface = Color(0xFF111A2C)
val ObsidianSurfaceHigh = Color(0xFF1A263D)
val ObsidianOutline = Color(0xFF2B3A55)
val ObsidianOnSurface = Color(0xFFEAF0F9)
val ObsidianOnSurfaceMuted = Color(0xFF94A3B8)

val AmoledBg = Color(0xFF000000)
val AmoledSurface = Color(0xFF090D14)
val AmoledSurfaceHigh = Color(0xFF121926)
val AmoledOutline = Color(0xFF202A3C)

val LightBg = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFEBF0F7)
val LightOutline = Color(0xFFCFD8E6)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceMuted = Color(0xFF475569)

@Immutable
data class TorrentClawSemanticColors(
  val verified: Color,
  val warning: Color,
  val suspicious: Color,
  val hdrGold: Color,
  val dolbyViolet: Color,
  val debridCached: Color,
  val seederHigh: Color,
  val seederMid: Color,
  val seederLow: Color,
  val cardSubtleBorder: Color,
)

val DarkSemanticColors =
  TorrentClawSemanticColors(
    verified = Color(0xFF2EE59D),
    warning = Color(0xFFFFB74D),
    suspicious = Color(0xFFFF5252),
    hdrGold = Color(0xFFFFCA28),
    dolbyViolet = Color(0xFFCE93D8),
    debridCached = Color(0xFF00E5FF),
    seederHigh = Color(0xFF34D399),
    seederMid = Color(0xFFFBBF24),
    seederLow = Color(0xFFF87171),
    cardSubtleBorder = Color(0xFF24324A),
  )

val LightSemanticColors =
  TorrentClawSemanticColors(
    verified = Color(0xFF0D8A56),
    warning = Color(0xFFB45309),
    suspicious = Color(0xFFC62828),
    hdrGold = Color(0xFFA16207),
    dolbyViolet = Color(0xFF7B1FA2),
    debridCached = Color(0xFF0284C7),
    seederHigh = Color(0xFF059669),
    seederMid = Color(0xFFD97706),
    seederLow = Color(0xFFDC2626),
    cardSubtleBorder = Color(0xFFDDE5F0),
  )

val LocalTorrentClawColors = staticCompositionLocalOf { DarkSemanticColors }
