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
