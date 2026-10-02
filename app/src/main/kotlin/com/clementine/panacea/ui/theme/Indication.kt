package com.clementine.panacea.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Slate's interaction states, for everything tappable: a white wash over the fill on hover (6%) and
 * while pressed (12%), dark on a light theme, and a 2 dp accent ring in [shape] when focused from a
 * keyboard. The app sets it as the default indication, so a plain `clickable` gets it too.
 */
data class SlateIndication(val shape: Shape = RoundedCornerShape(8.dp)) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, shape)

    private class Node(private val source: InteractionSource, private val shape: Shape) :
        Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
        private var presses = 0
        private var hovers = 0
        private var focuses = 0

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    when (i) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                        is HoverInteraction.Enter -> hovers++
                        is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                        is FocusInteraction.Focus -> focuses++
                        is FocusInteraction.Unfocus -> focuses = (focuses - 1).coerceAtLeast(0)
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val palette = currentValueOf(LocalPalette)
            val wash = when {
                presses > 0 -> 0.12f
                hovers > 0 -> 0.06f
                else -> 0f
            }
            if (wash > 0f) {
                val base = if (palette.isLight) Color.Black else Color.White
                drawOutline(shape.createOutline(size, layoutDirection, this), base.copy(alpha = wash))
            }
            if (focuses > 0) {
                val stroke = 2.dp.toPx()
                drawOutline(shape.createOutline(size, layoutDirection, this), palette.accent, style = Stroke(stroke))
            }
        }
    }
}
