package com.railfancopilot.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── RailFan dark color palette ────────────────────────────────────────────────

val RailBlue = Color(0xFF60A5FA)
val RailBlueDark = Color(0xFF1E3A5F)
val RailBlueMid = Color(0xFF3B82F6)
val RailGreen = Color(0xFF4ADE80)
val RailAmber = Color(0xFFFBBF24)
val RailRed = Color(0xFFF87171)
val RailPurple = Color(0xFF818CF8)

val BgPrimary = Color(0xFF0D1117)
val BgCard = Color(0xFF141B27)
val BgInput = Color(0xFF1A2235)
val Border = Color(0xFF1E2A3A)
val BorderLight = Color(0xFF2A3450)

val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF6B7280)

// ── Alert severity backgrounds & borders ──────────────────────────────────────
val AlertBgDanger = Color(0xFF1A0A0A)
val AlertBorderDanger = Color(0xFF7F1D1D)
val AlertBgWarning = Color(0xFF1A1400)
val AlertBorderWarning = Color(0xFF78580C)
val AlertBgInfo = Color(0xFF0A0F1A)
// AlertBorderInfo uses RailBlueDark (already defined above)

// ── Status / badge backgrounds ────────────────────────────────────────────────
val StatusBgGreen = Color(0xFF1A3A1A)    // ETA on-time, priority High
val StatusBgAmber = Color(0xFF3A1A0A)    // delayed train
val StatusBgNeutral = Color(0xFF2A2A0A)  // priority Medium
val StatusBgBlue = Color(0xFF1A1A2A)     // priority Low

// ── Miscellaneous UI backgrounds ──────────────────────────────────────────────
val LiveDotInactive = Color(0xFF374151)
val MicListeningBg = Color(0xFF4A1A1A)
val GpsWarnBg = Color(0xFF1A1000)
val AchievementBannerBg = Color(0xFF1A2A0A)
val GoldenHourBg = Color(0xFF2A1A0A)
val LocoPlaceholderBg = Color(0xFF1A2A1A)
val RrPlaceholderBg = Color(0xFF2A2A2A)
val SunRayAmber = Color(0xFFF59E0B)
val SunBelowHorizon = Color(0xFF6B5A30)

private val DarkColorScheme = darkColorScheme(
    primary = RailBlueMid,
    onPrimary = Color.White,
    primaryContainer = RailBlueDark,
    onPrimaryContainer = RailBlue,
    secondary = RailGreen,
    onSecondary = Color(0xFF0A1A0A),
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgInput,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = RailRed,
    onError = Color.White
)

@Composable
fun RailFanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
