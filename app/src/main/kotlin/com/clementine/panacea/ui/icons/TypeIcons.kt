package com.clementine.panacea.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.clementine.panacea.model.MedicationType

/** Stroke icons on a 24-unit grid, one per dose form, drawn in whatever colour the caller tints. */
object TypeIcons {
    private val paths = mapOf(
        MedicationType.ORAL_TABLET to "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0M12 4v16",
        MedicationType.ORAL_CAPSULE to "M5.6 13.5l7.9-7.9a4 4 0 0 1 5.7 5.7l-7.9 7.9a4 4 0 0 1-5.7-5.7zM9.5 9.6l5.7 5.7",
        MedicationType.LIQUID_SYRUP to "M9 3h6M10 3v4l-3 4v8a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-8l-3-4V3M7 14h10",
        MedicationType.IV_INJECTION to "M18 3l3 3M19.5 4.5L10 14M13 5l6 6M7 15l-4 4M8.5 10.5l5 5L9 20H4v-5z",
        MedicationType.SUBLINGUAL to "M5 11h14v4a7 7 0 0 1-14 0zM12 11v8M9.5 6.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        MedicationType.TOPICAL to "M8 3h8l-1 3H9zM9 6h6v12l-3 3-3-3z",
        MedicationType.INHALER to "M9 3h5v5H9zM7 8h9v10a3 3 0 0 1-3 3h-3a3 3 0 0 1-3-3z",
        MedicationType.DROPS to "M12 3c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11z",
        MedicationType.PATCH to "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM9 9h.01M15 9h.01M9 15h.01M15 15h.01M12 12h.01",
        MedicationType.SUPPOSITORY to "M12 3c3 2 4 5 4 9v8H8v-8c0-4 1-7 4-9z",
        MedicationType.POWDER to "M4 19h16M6 19c0-4 3-7 6-7s6 3 6 7M9 8h.01M12 6h.01M15 8h.01",
        MedicationType.EDIBLE to "M8 4h8a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM9.5 14.5c1.4 1.2 3.6 1.2 5 0M9.5 9.5h.01M14.5 9.5h.01",
        MedicationType.OTHER to "M5 12h.01M12 12h.01M19 12h.01",
        MedicationType.UNSPECIFIED to "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .8-1 1.5M12 16.5h.01",
    )

    private val icons = MedicationType.entries.associateWith { build(it.name, paths.getValue(it)) }

    fun of(type: MedicationType): ImageVector = icons.getValue(type)

    private fun build(name: String, pathData: String): ImageVector =
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
}
