package com.clementine.panacea.ui.medication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clementine.panacea.ui.components.AxisLabels
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateSwitch
import com.clementine.panacea.ui.theme.Colors

/** Learn My Routine: what Panacea has learned about when this medication is taken, and the switch for asking. */
@Composable
fun RoutineCard(routine: RoutineUi, onToggle: (Boolean) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SlateCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = routine.on, role = Role.Switch, onValueChange = onToggle)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Learn My Routine", style = MaterialTheme.typography.titleMedium, color = Colors.Ink, modifier = Modifier.weight(1f))
                SlateSwitch(checked = routine.on, onCheckedChange = null)
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(routine.summary, style = body, color = Colors.Muted)
                if (routine.bands.isNotEmpty()) DayLine(routine)
                routine.basis?.let { Text(it, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Colors.Faint) }
            }
        }
    }
}

/** The day from midnight to midnight: the usual times as soft bands, each dose learned from as a tick. */
@Composable
private fun DayLine(routine: RoutineUi) {
    val line = Colors.Line
    val band = Colors.AccentWash
    val tick = Colors.Accent.copy(alpha = 0.6f)
    // The summary above says it in words.
    Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val mid = size.height / 2
            drawRect(line, Offset(0f, mid - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
            val bandHeight = 18.dp.toPx()
            routine.bands.forEach { b ->
                drawRoundRect(
                    band,
                    Offset(b.from * size.width, mid - bandHeight / 2),
                    Size(((b.to - b.from) * size.width).coerceAtLeast(4.dp.toPx()), bandHeight),
                    CornerRadius(bandHeight / 2),
                )
            }
            val w = 2.dp.toPx()
            val h = 12.dp.toPx()
            routine.ticks.forEach { t ->
                drawRect(tick, Offset((t * size.width - w / 2).coerceIn(0f, size.width - w), mid - h / 2), Size(w, h))
            }
        }
        AxisLabels(routine.axis, MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).merge(Numbers), Colors.Faint)
    }
}
