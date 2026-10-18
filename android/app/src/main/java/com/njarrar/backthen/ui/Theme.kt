package com.njarrar.backthen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.njarrar.backthen.R
import com.njarrar.backthen.core.L

// Tokens from the design. One coral accent, kept for hearts, selection and the current day.
val Accent = Color(0xFFE5533D)

/** Year chip on browse grid tiles: white text on a dark capsule, the same in both themes. */
val Chip = Color(0x73000000)
val ChipInk = Color.White

/** Year cards: a dark wash rising from the bottom of the photo, under white text. */
val CardScrim = Color(0x8C000000)

/** Photo viewer: photos on near-black, light text and controls, a brighter heart. */
val ViewerBg = Color(0xFF0D0D0E)
val ViewerInk = Color(0xFFF4F4F2)
val ViewerHeart = Color(0xFFFF6A52)

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
    /** Check mark on the toast. The toast is dark ink in light mode and light ink in dark mode. */
    val toastCheck: Color,
)

private val Light = Palette(
    bg = Color(0xFFF6F6F3), surface = Color.White, surface2 = Color(0xFFF3F2EE), ink = Color(0xFF141416),
    muted = Color(0xFF6E6E73), muted2 = Color(0xFF45454A), faint = Color(0xFFA3A3A8), fill = Color(0xFFECECE8),
    fill2 = Color(0xFFE6E6E2), line = Color(0x1A141416), accTint = Color(0xFFFBE4DF), accInk = Color(0xFF9A2E1E),
    navBg = Color(0xFFEDEDE9), toastCheck = Color(0xFFFF8B76),
)

private val Dark = Palette(
    bg = Color(0xFF121213), surface = Color(0xFF1C1C1E), surface2 = Color(0xFF242426), ink = Color(0xFFF2F2EF),
    muted = Color(0xFF9A9A9F), muted2 = Color(0xFFB4B4B8), faint = Color(0xFF5E5E63), fill = Color(0xFF2A2A2C),
    fill2 = Color(0xFF232325), line = Color(0x1FFFFFFF), accTint = Color(0xFF3A211C), accInk = Color(0xFFFF9C8A),
    navBg = Color(0xFF1A1A1C), toastCheck = Color(0xFF9A2E1E),
)

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/** Tajawal has no SemiBold, so SemiBold uses Bold: Arabic needs the extra weight to read at small sizes. */
private val TajawalFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
    Font(R.font.tajawal_black, FontWeight.Black),
)

/** The app font: Nunito, or Tajawal for languages in Arabic script, since Nunito has no Arabic letters. */
val Nunito: FontFamily get() = if (L.lang.arabicScript) TajawalFamily else NunitoFamily

/** Tight tracking for big titles. Arabic letters join, so they keep normal spacing. */
fun tightSpacing(size: Number): Double = if (L.lang.arabicScript) 0.0 else size.toDouble() * -0.03

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
    // Right to left for Arabic, whatever the rest of the phone is set to.
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalLayoutDirection provides if (L.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
