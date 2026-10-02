package com.clementine.panacea.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's other stroke icons, drawn like [TypeIcons]. */
object Glyphs {
    val Plus = strokeIcon("Plus", "M12 5v14M5 12h14")
    val Minus = strokeIcon("Minus", "M5 12h14")
    val Check = strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Clock = strokeIcon("Clock", "M12 7v5l3 2M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    val Play = strokeIcon("Play", "M8 5.5v13l10.5-6.5z")
    val File = strokeIcon("File", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5")

    // Bottom bar
    val Today = strokeIcon("Today", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 3v4M16 3v4")
    val Bell = strokeIcon("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0")
    val History = strokeIcon("History", "M3 12a9 9 0 1 0 2.6-6.4M3 4v4h4M12 7v5l3 2")
    val Sliders = strokeIcon("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8M16 5v4M10 15v4")
}

/** A 24-unit stroke icon (1.75 wide, round ends) from SVG path data; tinted by whoever draws it. */
internal fun strokeIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
