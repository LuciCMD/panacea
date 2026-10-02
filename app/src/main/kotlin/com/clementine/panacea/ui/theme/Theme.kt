package com.clementine.panacea.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One theme's colours, with the same roles as the Slate theme: surfaces from deepest to highest,
 * three text colours, one accent and three states. Nothing on screen uses a colour from outside it.
 */
@Immutable
data class Palette(
    val abyss: Color,
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val field: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val onAccent: Color,
    val accent: Color,
    val accentDim: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val isLight: Boolean = false,
) {
    val accentWash: Color get() = accent.copy(alpha = 0.16f)
}

private val LocalPalette = staticCompositionLocalOf { Themes.SLATE.palette }

/** The current theme's colours, for use inside composables. */
object Colors {
    val Abyss: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.abyss
    val Ground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ground
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
    val Field: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.field
    val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
    val LineSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.lineSoft
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val AccentDim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentDim
    val AccentWash: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentWash
    val Good: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.good
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warn
    val Bad: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bad
    val IsLight: Boolean @Composable @ReadOnlyComposable get() = LocalPalette.current.isLight
}

private fun Palette.scheme() = if (isLight) {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentDim, onPrimaryContainer = ink,
        secondary = accentDim, onSecondary = ink,
        background = ground, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = field, onSurfaceVariant = muted,
        surfaceContainerLowest = abyss, surfaceContainerLow = ground,
        surfaceContainer = surface, surfaceContainerHigh = raised,
        surfaceContainerHighest = field,
        outline = line, outlineVariant = lineSoft,
        error = bad, onError = onAccent,
    )
} else {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentDim, onPrimaryContainer = ink,
        secondary = accentDim, onSecondary = ink,
        background = ground, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = field, onSurfaceVariant = muted,
        surfaceContainerLowest = abyss, surfaceContainerLow = ground,
        surfaceContainer = surface, surfaceContainerHigh = raised,
        surfaceContainerHighest = field,
        outline = line, outlineVariant = lineSoft,
        error = bad, onError = onAccent,
    )
}

// Headings are not bold: hierarchy comes from size and colour.
private val PanaceaType = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val PanaceaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun PanaceaTheme(palette: Palette = Themes.SLATE.palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.scheme(), typography = PanaceaType, shapes = PanaceaShapes, content = content)
    }
}
