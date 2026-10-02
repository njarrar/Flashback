package com.njarrar.backthen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.njarrar.backthen.R

// Tokens from the design. One coral accent, kept for hearts, selection and the current day.
val Accent = Color(0xFFE5533D)

data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val muted2: Color,
    val faint: Color,
    val fill: Color,
    val fill2: Color,
    val line: Color,
    val accTint: Color,
    val accInk: Color,
    val navBg: Color,
)

private val Light = Palette(
    bg = Color(0xFFF6F6F3), surface = Color.White, surface2 = Color(0xFFF3F2EE), ink = Color(0xFF141416),
    muted = Color(0xFF6E6E73), muted2 = Color(0xFF45454A), faint = Color(0xFFA3A3A8), fill = Color(0xFFECECE8),
    fill2 = Color(0xFFE6E6E2), line = Color(0x1A141416), accTint = Color(0xFFFBE4DF), accInk = Color(0xFF9A2E1E),
    navBg = Color(0xFFEDEDE9),
)

private val Dark = Palette(
    bg = Color(0xFF121213), surface = Color(0xFF1C1C1E), surface2 = Color(0xFF242426), ink = Color(0xFFF2F2EF),
    muted = Color(0xFF9A9A9F), muted2 = Color(0xFFB4B4B8), faint = Color(0xFF5E5E63), fill = Color(0xFF2A2A2C),
    fill2 = Color(0xFF232325), line = Color(0x1FFFFFFF), accTint = Color(0xFF3A211C), accInk = Color(0xFFFF9C8A),
    navBg = Color(0xFF1A1A1C),
)

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun BackThenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Accent,
        background = p.bg,
        surface = p.surface,
        surfaceContainerHigh = p.surface2,
        surfaceContainerLow = p.surface,
        onSurface = p.ink,
        onBackground = p.ink,
        onSurfaceVariant = p.muted,
        outline = p.line,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
