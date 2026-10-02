package com.clementine.panacea.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Slate tokens. No colour outside these, and one accent. */
object Slate {
    val Abyss = Color(0xFF1A1B21)
    val Ground = Color(0xFF1F2027)
    val Surface = Color(0xFF2A2B33)
    val Raised = Color(0xFF31323B)
    val Field = Color(0xFF3A3B46)
    val Line = Color(0xFF474855)
    val LineSoft = Color(0xFF34353F)

    val Ink = Color(0xFFE4E4E8)
    val Muted = Color(0xFFA4A5AF)
    val Faint = Color(0xFF7E7F8A)
    val OnAccent = Color(0xFF1F1B2B)

    val Accent = Color(0xFFB39DF0)
    val AccentDim = Color(0xFF7D6AB5)
    val AccentWash = Color(0x29B39DF0)

    val Good = Color(0xFF6CC77A)
    val Warn = Color(0xFFF5A742)
    val Bad = Color(0xFFEF6B67)
}

private val SlateScheme = darkColorScheme(
    primary = Slate.Accent, onPrimary = Slate.OnAccent,
    primaryContainer = Slate.AccentDim, onPrimaryContainer = Slate.Ink,
    secondary = Slate.AccentDim, onSecondary = Slate.Ink,
    background = Slate.Ground, onBackground = Slate.Ink,
    surface = Slate.Surface, onSurface = Slate.Ink,
    surfaceVariant = Slate.Field, onSurfaceVariant = Slate.Muted,
    surfaceContainerLowest = Slate.Abyss, surfaceContainerLow = Slate.Ground,
    surfaceContainer = Slate.Surface, surfaceContainerHigh = Slate.Raised,
    surfaceContainerHighest = Slate.Field,
    outline = Slate.Line, outlineVariant = Slate.LineSoft,
    error = Slate.Bad, onError = Slate.OnAccent,
)

// Headings are not bold: hierarchy comes from size and colour.
private val SlateType = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val SlateShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun PanaceaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SlateScheme, typography = SlateType, shapes = SlateShapes, content = content)
}
