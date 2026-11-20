package com.njarrar.backthen.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.njarrar.backthen.R
import com.njarrar.backthen.core.L

// Tokens from the design. Adaptive coral accent meets WCAG AA contrast in both light and dark modes.
val AccentFill = Color(0xFFC93B25)

val Accent: Color
    @Composable get() = LocalPalette.current.accent

/** Year chip on browse grid tiles: white text on a dark capsule, the same in both themes (56% black). */
val Chip = Color(0x8F000000)
val ChipInk = Color.White

/** Year cards: a dark wash rising from the bottom of the photo, under white text. */
val CardScrim = Color(0x99000000)

/** Calendar: a dark wash over a day's photo, under its white number (54% black). */
val DayWash = Color(0x8A000000)

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
    val accent: Color,
    val accTint: Color,
    val accInk: Color,
    val navBg: Color,
    /** Check mark on the toast. The toast is dark ink in light mode and light ink in dark mode. */
    val toastCheck: Color,
)

private val Light = Palette(
    bg = Color(0xFFF6F6F3), surface = Color.White, surface2 = Color(0xFFF3F2EE), ink = Color(0xFF141416),
    muted = Color(0xFF5E5E63), muted2 = Color(0xFF45454A), faint = Color(0xFFA3A3A8), fill = Color(0xFFECECE8),
    fill2 = Color(0xFFE6E6E2), line = Color(0x1A141416), accent = Color(0xFFC93B25), accTint = Color(0xFFFBE4DF),
    accInk = Color(0xFF9A2E1E), navBg = Color(0xFFEDEDE9), toastCheck = Color(0xFFFF8B76),
)

private val Dark = Palette(
    bg = Color(0xFF121213), surface = Color(0xFF1C1C1E), surface2 = Color(0xFF242426), ink = Color(0xFFF2F2EF),
    muted = Color(0xFF9A9A9F), muted2 = Color(0xFFB4B4B8), faint = Color(0xFF5E5E63), fill = Color(0xFF2A2A2C),
    fill2 = Color(0xFF232325), line = Color(0x1FFFFFFF), accent = Color(0xFFFF6B53), accTint = Color(0xFF3A211C),
    accInk = Color(0xFFFF9C8A), navBg = Color(0xFF1A1A1C), toastCheck = Color(0xFF9A2E1E),
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

/** True when Android's "Remove animations" accessibility setting is enabled (ANIMATOR_DURATION_SCALE == 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Forces light status and navigation bar icons over full-screen dark overlays (photo viewer, reel editor),
 * restoring both to match the system theme when the overlay closes.
 */
@Composable
fun DarkSystemBars() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(view, dark) {
        val bars = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }
}

private fun appTypography(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

@Composable
fun BackThenTheme(content: @Composable () -> Unit) {
    val langVersion by L.version.collectAsState()
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val context = LocalContext.current
    val view = LocalView.current

    var reduce by remember(context) { mutableStateOf(reduceMotion(context)) }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = reduceMotion(context)
            }
        }
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }
        reduce = reduceMotion(context)
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    DisposableEffect(view, dark) {
        val bars = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = !dark
        bars?.isAppearanceLightNavigationBars = !dark
        onDispose {}
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            primaryContainer = p.accTint,
            onPrimaryContainer = p.accInk,
            secondary = p.muted2,
            onSecondary = p.bg,
            secondaryContainer = p.fill,
            onSecondaryContainer = p.ink,
            tertiary = p.accent,
            onTertiary = Color.White,
            tertiaryContainer = p.accTint,
            onTertiaryContainer = p.accInk,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.muted,
            surfaceContainerLowest = p.bg,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface2,
            surfaceContainerHighest = p.fill,
            outline = p.line,
            outlineVariant = p.line,
            error = Color(0xFFFF6B53),
            onError = Color(0xFF141416),
            errorContainer = p.accTint,
            onErrorContainer = p.accInk,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            primaryContainer = p.accTint,
            onPrimaryContainer = p.accInk,
            secondary = p.muted2,
            onSecondary = p.bg,
            secondaryContainer = p.fill,
            onSecondaryContainer = p.ink,
            tertiary = p.accent,
            onTertiary = Color.White,
            tertiaryContainer = p.accTint,
            onTertiaryContainer = p.accInk,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.muted,
            surfaceContainerLowest = p.bg,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface2,
            surfaceContainerHighest = p.fill,
            outline = p.line,
            outlineVariant = p.line,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = p.accTint,
            onErrorContainer = p.accInk,
        )
    }
    val typography = remember(langVersion) { appTypography(Nunito) }
    val layoutDirection = remember(langVersion) { if (L.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr }
    // Right to left for Arabic, whatever the rest of the phone is set to.
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalReduceMotion provides reduce,
        LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
