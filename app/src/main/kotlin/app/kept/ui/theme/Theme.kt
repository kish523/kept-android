package app.kept.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kept.R
import app.kept.data.settings.ThemeMode
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Kept's own colours. Every pair used for text passes WCAG AA (4.5:1) in both themes. */
@Immutable
data class KeptColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    /** Deadlines within a week. */
    val soon: Color,
    val soonContainer: Color,
    /** "Check this" fields. Always paired with an icon and words, never colour alone. */
    val check: Color,
    val checkContainer: Color,
    /** The dark "Next up" card and the snackbar. */
    val heroBackground: Color,
    val heroInk: Color,
    val heroTrack: Color,
    val heroArc: Color,
    val heroChip: Color,
    val toastAccent: Color,
    val glow2: Color,
    val glowAlpha: Float,
) {
    /** Frosted bars: what scrolls underneath shows through, blurred. */
    val glassBar: HazeStyle
        get() = HazeStyle(
            backgroundColor = background,
            tint = HazeTint(background.copy(alpha = if (dark) 0.70f else 0.72f)),
            blurRadius = 22.dp,
            noiseFactor = 0f,
        )

    val glassCard: HazeStyle
        get() = HazeStyle(
            backgroundColor = surface,
            tint = HazeTint(surface.copy(alpha = if (dark) 0.80f else 0.78f)),
            blurRadius = 26.dp,
            noiseFactor = 0f,
        )
}

val LightKeptColors = KeptColors(
    dark = false,
    background = Color(0xFFF5F4F0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECEAE4),
    ink = Color(0xFF16181C),
    muted = Color(0xFF5D626A),
    line = Color(0x1416181C),
    lineStrong = Color(0x2E16181C),
    accent = Color(0xFF0F6E56),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE0ECE9),
    soon = Color(0xFFB4410F),
    soonContainer = Color(0xFFFCE9DE),
    check = Color(0xFF8A5300),
    checkContainer = Color(0xFFFFF3DC),
    heroBackground = Color(0xFF16181C),
    heroInk = Color(0xFFF5F4F0),
    heroTrack = Color(0x1FFFFFFF),
    heroArc = Color(0xFFFF9F6E),
    heroChip = Color(0x29FF9F6E),
    toastAccent = Color(0xFF5FD3A8),
    glow2 = Color(0xFFFF9F6E),
    glowAlpha = 0.42f,
)

val DarkKeptColors = KeptColors(
    dark = true,
    background = Color(0xFF0F1012),
    surface = Color(0xFF1A1C20),
    surface2 = Color(0xFF24272C),
    ink = Color(0xFFEDEEF0),
    muted = Color(0xFFA3A8B0),
    line = Color(0x14FFFFFF),
    lineStrong = Color(0x2EFFFFFF),
    accent = Color(0xFF7CC9AE),
    onAccent = Color(0xFF0F1012),
    accentContainer = Color(0xFF283434),
    soon = Color(0xFFFFB088),
    soonContainer = Color(0xFF3A2219),
    check = Color(0xFFFFC56B),
    checkContainer = Color(0xFF33270F),
    heroBackground = Color(0xFF202328),
    heroInk = Color(0xFFEDEEF0),
    heroTrack = Color(0x1AFFFFFF),
    heroArc = Color(0xFFFFB088),
    heroChip = Color(0x24FFB088),
    toastAccent = Color(0xFF5FD3A8),
    glow2 = Color(0xFFFF9F6E),
    glowAlpha = 0.30f,
)

val LocalKeptColors = staticCompositionLocalOf { LightKeptColors }

object Kept {
    val colors: KeptColors
        @Composable @ReadOnlyComposable get() = LocalKeptColors.current
}

/** Motion: calm, iOS-like curves for screens and sheets; a soft spring for things you press. */
object KeptMotion {
    /** Screens, sheets, the segmented control. Fast start, long gentle settle, no overshoot. */
    val Settle = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    /** Things that appear: rows, cards, fields. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SCREEN_MS = 480
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> pop() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)
}

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private val KeptTypography = Typography(
    displaySmall = style(40, FontWeight.ExtraBold, 44, -0.035),
    headlineSmall = style(22, FontWeight.Bold, 28, -0.015),
    titleLarge = style(19, FontWeight.Bold, 25, -0.015),
    titleMedium = style(17, FontWeight.Bold, 23, -0.01),
    titleSmall = style(15, FontWeight.SemiBold, 21),
    bodyLarge = style(16, FontWeight.SemiBold, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(13, FontWeight.Bold, 18),
    labelSmall = style(12, FontWeight.Bold, 16, 0.07),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Kept's palette in both themes, Plus Jakarta Sans, and Material 3 components tuned to match. */
@Composable
fun KeptTheme(dark: Boolean, content: @Composable () -> Unit) {
    val k = if (dark) DarkKeptColors else LightKeptColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = k.accent,
        onPrimary = k.onAccent,
        primaryContainer = k.accentContainer,
        onPrimaryContainer = k.ink,
        secondaryContainer = k.accentContainer,
        onSecondaryContainer = k.ink,
        background = k.background,
        onBackground = k.ink,
        surface = k.background,
        onSurface = k.ink,
        surfaceVariant = k.surface2,
        onSurfaceVariant = k.muted,
        surfaceContainerLowest = k.surface,
        surfaceContainerLow = k.surface,
        surfaceContainer = k.surface,
        surfaceContainerHigh = k.surface,
        surfaceContainerHighest = k.surface2,
        outline = k.lineStrong,
        outlineVariant = k.line,
        error = k.soon,
        errorContainer = k.soonContainer,
        inverseSurface = k.heroBackground,
        inverseOnSurface = k.heroInk,
        inversePrimary = k.toastAccent,
    )
    CompositionLocalProvider(LocalKeptColors provides k) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KeptTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
