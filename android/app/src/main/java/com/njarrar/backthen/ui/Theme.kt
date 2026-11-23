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

// Warm Archival (Light) & Warm Darkroom (Dark) tokens, 100% WCAG 2.1 AA+ verified.
val AccentFill = Color(0xFFE5533D)

val Accent: Color
    @Composable get() = LocalPalette.current.accent

/** Year chip & heart badge scrim on photos: #181615 @ 62% (>= 5.04:1 AA over pure snow). */
val Chip = Color(0x9E181615)
val ChipInk = Color.White
val PhotoBadgeScrim = Color(0x9E181615)

/** Year cards: a dark wash rising from the bottom of the photo, under white text. */
val CardScrim = Color(0xA6181615)

/** Calendar: a warm dark wash over a day's photo, under its white number (#181615 @ 60%, 4.71:1 AA over snow). */
val DayWash = Color(0x99181615)

/** Photo viewer: warm darkroom near-black, archival cream text, luminous coral heart. */
val ViewerBg = Color(0xFF0B0B0D)
val ViewerInk = Color(0xFFF5F2EC)
val ViewerHeart = Color(0xFFFF7A66)

data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val muted2: Color,
    val inkFuture: Color,
    val faint: Color,
    val borderControl: Color,
    val fill: Color,
    val fill2: Color,
    val line: Color,
    val accent: Color,
    val accTint: Color,
    val accInk: Color,
    val brandCobaltFill: Color,
    val brandCobaltFillInk: Color,
    val brandCobaltTint: Color,
    val brandCobaltInk: Color,
    val brandSunTint: Color,
    val brandSunInk: Color,
    val navBg: Color,
    /** Check mark on the toast. The toast is dark ink in light mode and light ink in dark mode. */
    val toastCheck: Color,
)

private val Light = Palette(
    bg = Color(0xFFFAF8F4),
    surface = Color.White,
    surface2 = Color(0xFFF3EFE8),
    ink = Color(0xFF181615),
    muted = Color(0xFF635D57),
    muted2 = Color(0xFF4D4843),
    inkFuture = Color(0xFF706A63),
    faint = Color(0xFF8A837A),
    borderControl = Color(0xFF8A837A),
    fill = Color(0xFFECE7DE),
    fill2 = Color(0xFFE4DED4),
    line = Color(0xFFDFD9CE),
    accent = Color(0xFFE5533D),
    accTint = Color(0xFFFCEAE6),
    accInk = Color(0xFF8E2314),
    brandCobaltFill = Color(0xFF1E5CE0),
    brandCobaltFillInk = Color.White,
    brandCobaltTint = Color(0xFFEBF2FF),
    brandCobaltInk = Color(0xFF1443A8),
    brandSunTint = Color(0xFFFDF3D7),
    brandSunInk = Color(0xFF754E00),
    navBg = Color(0xFFF5F2EC),
    toastCheck = Color(0xFFFF8B76),
)

private val Dark = Palette(
    bg = Color(0xFF111010),
    surface = Color(0xFF1B1A19),
    surface2 = Color(0xFF242220),
    ink = Color(0xFFF5F2EC),
    muted = Color(0xFF9E978F),
    muted2 = Color(0xFFC4BEB6),
    inkFuture = Color(0xFF8A837B),
    faint = Color(0xFF7A746C),
    borderControl = Color(0xFF7A746C),
    fill = Color(0xFF2C2927),
    fill2 = Color(0xFF242220),
    line = Color(0xFF302D2A),
    accent = Color(0xFFE5533D),
    accTint = Color(0xFF3B1E19),
    accInk = Color(0xFFFFA999),
    brandCobaltFill = Color(0xFF6BA1FF),
    brandCobaltFillInk = Color(0xFF0A1429),
    brandCobaltTint = Color(0xFF182845),
    brandCobaltInk = Color(0xFFA3C4FF),
    brandSunTint = Color(0xFF33280E),
    brandSunInk = Color(0xFFFFD466),
    navBg = Color(0xFF171615),
    toastCheck = Color(0xFF8E2314),
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
            primary = p.brandCobaltFill,
            onPrimary = p.brandCobaltFillInk,
            primaryContainer = p.brandCobaltTint,
            onPrimaryContainer = p.brandCobaltInk,
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
            outline = p.borderControl,
            outlineVariant = p.line,
            error = Color(0xFFFF7A66),
            onError = Color(0xFF181615),
            errorContainer = p.accTint,
            onErrorContainer = p.accInk,
        )
    } else {
        lightColorScheme(
            primary = p.brandCobaltFill,
            onPrimary = p.brandCobaltFillInk,
            primaryContainer = p.brandCobaltTint,
            onPrimaryContainer = p.brandCobaltInk,
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
            outline = p.borderControl,
            outlineVariant = p.line,
            error = Color(0xFFE5533D),
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
