package com.clementine.panacea.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Labels spread evenly along a line, first at the start and last at the end. When large text leaves
 * no room, every other one goes, then all but the ends, so they never run into each other.
 */
@Composable
fun AxisLabels(labels: List<String>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Layout(
        content = { labels.forEach { Text(it, style = style, color = color, maxLines = 1, softWrap = false) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val last = placeables.lastIndex
        fun x(i: Int): Int {
            val w = placeables[i].width
            if (last <= 0) return 0
            return (i * width / last - w * i / last).coerceIn(0, (width - w).coerceAtLeast(0))
        }
        fun fits(shown: List<Int>) = shown.zipWithNext().all { (a, b) -> x(a) + placeables[a].width + gap <= x(b) }
        val choices = listOf((0..last).toList(), (0..last step 2).toList().let { if (last % 2 == 0) it else it + last }, listOf(0, last))
        val shown = choices.firstOrNull(::fits) ?: listOf(0, last)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            shown.distinct().forEach { placeables[it].placeRelative(x(it), 0) }
        }
    }
}
