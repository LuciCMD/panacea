package com.clementine.panacea.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.clementine.panacea.ui.components.AxisLabels
import com.clementine.panacea.ui.components.InfoTip
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.theme.Colors

/** Doses within this of a group's first share one mark, with their count in it. */
private val Merge = 16.dp

/** How far either side of a tap reaches: half a 48dp finger target. */
private val Reach = 24.dp

/**
 * Today's doses on a line from midnight to midnight. A tap on a dot says what it was; doses too close
 * to tell apart share one numbered dot, and a tap lists every dose near the finger.
 */
@Composable
fun DayStripCard(strip: DayStrip) {
    val small = MaterialTheme.typography.bodyMedium.merge(Numbers)
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    val groups = remember(strip.dots, width) {
        with(density) { StripLayout.group(strip.dots.map { it.fraction }, width.toFloat(), Merge.toPx()) }
    }
    // The groups a tap picked; cleared when the doses or the width change underneath them.
    var picked by remember(groups) { mutableStateOf<List<DotGroup>>(emptyList()) }
    SlateCard(Modifier.fillMaxWidth()) {
        Column(
            // The label row is the ⓘ's 48dp tall, which brings its own space above and below.
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Doses Today", style = small, color = Colors.Muted)
                InfoTip(about = "Doses Today", text = TodayText.STRIP)
                Spacer(Modifier.weight(1f))
                Text(TodayText.dosesToday(strip.count), style = small, color = Colors.Ink)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { width = it.width }
                    // The line and its labels together make a target near 48dp tall.
                    .pointerInput(groups) {
                        detectTapGestures { tap -> picked = StripLayout.hit(groups, tap.x, Reach.toPx()) }
                    },
            ) {
                Column {
                    StripLine(strip, groups, picked)
                    AxisLabels(strip.axis, MaterialTheme.typography.bodySmall.merge(Numbers), Colors.Muted, Modifier.padding(top = 8.dp).clearAndSetSemantics { })
                }
                if (picked.isNotEmpty()) {
                    val x = picked.map { it.x }.average().toInt()
                    val gap = with(density) { 6.dp.roundToPx() }
                    val margin = with(density) { 16.dp.roundToPx() }
                    Popup(
                        popupPositionProvider = remember(x, gap, margin) { UnderDots(x, gap, margin) },
                        onDismissRequest = { picked = emptyList() },
                        properties = PopupProperties(focusable = true),
                    ) {
                        DosesAt(picked.flatMap { it.dots }.sorted().map { strip.dots[it] })
                    }
                }
            }
        }
    }
}

@Composable
private fun StripLine(strip: DayStrip, groups: List<DotGroup>, picked: List<DotGroup>) {
    val description = if (strip.dots.isEmpty()) "No doses logged today" else "Logged today: " + strip.dots.joinToString { it.label }
    val (field, lineColor, surface, accent, ink) = listOf(Colors.Field, Colors.Line, Colors.Surface, Colors.Accent, Colors.Ink)
    val onAccent = Colors.OnAccent
    val measurer = rememberTextMeasurer()
    val countStyle = MaterialTheme.typography.labelSmall.merge(Numbers).copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = onAccent)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            // TalkBack hears every dose from this at once, so the taps are for sight only.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val line = 2.dp.toPx()
        val y = size.height / 2
        val nowX = size.width * strip.nowFraction
        val corner = CornerRadius(line / 2)
        drawRoundRect(field, Offset(0f, y - line / 2), Size(size.width, line), corner)
        drawRoundRect(lineColor, Offset(0f, y - line / 2), Size(nowX, line), corner)
        groups.forEach { g ->
            val center = Offset(g.x, y)
            val many = g.dots.size > 1
            // A shared dot is a little larger, to hold its count.
            val dot = (if (many) 9.dp else 7.dp).toPx()
            drawCircle(surface, radius = dot + 3.dp.toPx(), center = center)
            drawCircle(accent, radius = dot, center = center)
            if (g in picked) drawCircle(ink, radius = dot + 3.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
            if (many) {
                val text = measurer.measure(if (g.dots.size > 9) "9+" else g.dots.size.toString(), countStyle)
                drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
            }
        }
        drawRoundRect(ink, Offset(nowX - line / 2, 0f), Size(line, size.height), corner)
    }
}

/** Each dose under the finger: its time, what it was and how much, in time order. */
@Composable
private fun DosesAt(dots: List<StripDot>) {
    val body = MaterialTheme.typography.bodyMedium
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Colors.Surface,
        contentColor = Colors.Ink,
        border = BorderStroke(1.dp, Colors.Line),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
    ) {
        Column(
            Modifier.width(IntrinsicSize.Max).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dots.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.time, style = body.merge(Numbers), color = Colors.Muted)
                    Text(it.name, style = body, color = Colors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Text(it.amount, style = body.merge(Numbers), color = Colors.Muted)
                }
            }
        }
    }
}

/** Below the line, centred on the picked dots and kept on screen; above it when there's no room below. */
private class UnderDots(private val x: Int, private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val left = (anchorBounds.left + x - w / 2).coerceIn(margin, maxOf(margin, windowSize.width - w - margin))
        val below = anchorBounds.bottom + gap
        return IntOffset(left, if (below + h <= windowSize.height - margin) below else anchorBounds.top - gap - h)
    }
}
