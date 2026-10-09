package com.clementine.panacea.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Below this a load just appears; a loader that flashes for a moment reads as a glitch */
private const val SHOW_AFTER_MS = 300L

/**
 * What a screen shows while its data is on the way: nothing at first, then the alchemy circle once
 * the wait is long enough to notice. Screens use it as `val ui = state ?: return Loader()`
 */
@Composable
fun Loader(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MS)
        show = true
    }
    Box(modifier.fillMaxSize().background(Colors.Ground), contentAlignment = Alignment.Center) {
        AnimatedVisibility(show, enter = fadeIn(tween(250))) {
            AlchemyCircle(Modifier.semantics { contentDescription = "Loading" })
        }
    }
}

/**
 * A transmutation circle: the outer ring, the triangle and the inner circle draw themselves in one
 * after another, then the figure turns slowly inside a ring of marks turning the other way
 */
@Composable
fun AlchemyCircle(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drawn.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val turning = rememberInfiniteTransition(label = "alchemy")
    val turn by turning.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn")
    val glow by turning.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    val (accent, muted, line) = listOf(Colors.Accent, Colors.Muted, Colors.Line)

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        val t = drawn.value
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
        // Each part draws in over its own share of the 1.4 s
        fun part(from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

        // Ring of marks, turning against the figure
        rotate(-turn * 0.6f, c) {
            drawCircle(line, r, c, style = Stroke(1.dp.toPx()))
            val marks = part(0f, 0.5f)
            repeat(24) { i ->
                if (i < (marks * 24).toInt()) {
                    val a = i * 15.0 * PI / 180
                    val long = i % 6 == 0
                    val inner = r - (if (long) 7.dp else 4.dp).toPx()
                    drawLine(if (long) muted else line, c + polar(a, inner), c + polar(a, r), 1.dp.toPx(), StrokeCap.Round)
                }
            }
        }

        rotate(turn, c) {
            val outer = r * 0.78f
            trace(circle(c, outer), part(0.05f, 0.45f), accent, stroke)
            // Triangle pointing up: fire, and the vessel's three corners
            val tri = Path().apply {
                (0..3).forEach { i ->
                    val p = c + polar(-PI / 2 + i * 2 * PI / 3, outer)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            trace(tri, part(0.3f, 0.75f), accent, stroke)
            // The triangle's incircle is half its circumcircle
            trace(circle(c, outer / 2), part(0.6f, 1f), muted, stroke)
            if (t >= 1f) {
                (0..2).forEach { i -> drawCircle(accent, 2.5.dp.toPx(), c + polar(-PI / 2 + i * 2 * PI / 3, outer)) }
            }
        }
        drawCircle(accent.copy(alpha = glow * part(0.8f, 1f)), 3.dp.toPx(), c)
    }
}

private fun polar(angle: Double, radius: Float) = Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())

private fun circle(c: Offset, r: Float) = Path().apply {
    // Starts at the top, as a pen would
    arcTo(androidx.compose.ui.geometry.Rect(c, r), -90f, 359.99f, forceMoveTo = true)
}

/** Draws the first [share] of [path], as if a pen were still going */
private fun DrawScope.trace(path: Path, share: Float, color: Color, stroke: Stroke) {
    if (share <= 0f) return
    if (share >= 1f) return drawPath(path, color, style = stroke)
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * share, part, true)
    drawPath(part, color, style = stroke)
}
