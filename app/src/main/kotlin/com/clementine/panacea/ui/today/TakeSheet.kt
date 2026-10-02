package com.clementine.panacea.ui.today

import android.text.format.DateFormat
import android.view.ViewParent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.clementine.panacea.model.Amounts
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Slate
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Take a medication with a different amount or at an earlier time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeSheet(
    card: CardState,
    presets: List<Double>,
    formats: TimeFormats,
    onTake: (multiplier: Double, takenAt: Long?) -> Unit,
    onSavePreset: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val med = card.medication
    var multiplier by rememberSaveable(med.id) { mutableDoubleStateOf(med.lastMultiplier) }
    var takenAt by rememberSaveable(med.id) { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now = remember { ZonedDateTime.now() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = Slate.Surface,
        contentColor = Slate.Ink,
        scrimColor = Color(0x990A0A0E),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(Slate.Line, RoundedCornerShape(2.dp)))
        },
    ) {
        // The sheet has its own window, where Android would shade the navigation bar.
        val view = LocalView.current
        SideEffect {
            generateSequence(view as ViewParent?) { it.parent }
                .firstNotNullOfOrNull { (it as? DialogWindowProvider)?.window }
                ?.isNavigationBarContrastEnforced = false
        }
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Take ${med.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    modifier = Modifier.semantics { heading() },
                )
                perItem(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Slate.Muted) }
            }

            Row(
                Modifier.fillMaxWidth().background(Slate.Raised, MaterialTheme.shapes.medium).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Glyphs.Minus, "Less", enabled = multiplier > Amounts.MIN) { multiplier = Amounts.less(multiplier) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(TodayText.multiplier(multiplier), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, lineHeight = 46.sp).merge(Numbers))
                    Text(
                        listOfNotNull(
                            TodayText.amount(med, multiplier).takeIf { med.dose > 0 },
                            TodayText.weightOf(med, multiplier),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp).merge(Numbers),
                        color = Slate.Muted,
                    )
                }
                RoundButton(Glyphs.Plus, "More", enabled = multiplier < Amounts.MAX) { multiplier = Amounts.more(multiplier) }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    SlateChip(selected = p == multiplier, onClick = { multiplier = p }) {
                        Text(TodayText.multiplier(p), style = MaterialTheme.typography.bodyMedium.merge(Numbers))
                    }
                }
                if (multiplier !in presets && presets.size < Amounts.MAX_PRESETS) {
                    SlateChip(
                        selected = false,
                        onClick = { onSavePreset(multiplier) },
                        modifier = Modifier.semantics { contentDescription = "Save ${TodayText.multiplier(multiplier)} as a Preset" },
                    ) {
                        Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = Slate.LineSoft)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Taken At", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
                    Text(takenAtText(takenAt, now, formats), style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Slate.Muted)
                }
                SlateButton(onClick = { picking = true }) {
                    Icon(Glyphs.Clock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(if (takenAt == null) "I Took It Earlier" else "Change Time", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (multiplier != med.lastMultiplier) {
                Text("This amount is kept for next time.", style = MaterialTheme.typography.bodyMedium, color = Slate.Muted)
            }

            SlateButton(
                onClick = {
                    onTake(multiplier, takenAt)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                kind = ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("Take ${TodayText.amount(med, multiplier)}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp).merge(Numbers))
            }
        }
    }

    if (picking) {
        EarlierTimeDialog(
            initial = takenAt?.let { Instant.ofEpochMilli(it).atZone(now.zone).toLocalTime() } ?: LocalTime.now(),
            onPick = { time ->
                val current = ZonedDateTime.now()
                val today = current.with(time).withSecond(0).withNano(0)
                // A time still to come today can only mean yesterday.
                val picked = if (today.isAfter(current)) today.minusDays(1) else today
                takenAt = picked.toInstant().toEpochMilli()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** "50 mg per tablet · each weighs 0.31 g", or null when neither is known. */
private fun perItem(card: CardState): String? {
    val med = card.medication
    return listOfNotNull(
        "${formatAmount(med.dose)} ${med.doseUnit} per ${card.type.noun}".takeIf { med.dose > 0 },
        TodayText.weightOf(med, 1.0)?.let { "each weighs $it" },
    ).joinToString(" · ").ifEmpty { null }
}

private fun takenAtText(takenAt: Long?, now: ZonedDateTime, f: TimeFormats): String {
    if (takenAt == null) return "Now, ${ZonedDateTime.now().format(f.time)}"
    val at = Instant.ofEpochMilli(takenAt).atZone(now.zone)
    val day = if (at.toLocalDate() == ZonedDateTime.now().toLocalDate()) "Today" else "Yesterday"
    return "$day at ${at.format(f.time)}"
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Slate.Field,
        contentColor = if (enabled) Slate.Ink else Slate.Faint,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarlierTimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = "When Did You Take It?" }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text("When Did You Take It?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A time later than now counts as yesterday.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate.Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
                TimePicker(
                    state = state,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Slate.Field,
                        clockDialSelectedContentColor = Slate.OnAccent,
                        clockDialUnselectedContentColor = Slate.Ink,
                        selectorColor = Slate.Accent,
                        containerColor = Slate.Surface,
                        periodSelectorBorderColor = Slate.Line,
                        periodSelectorSelectedContainerColor = Slate.AccentWash,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = Slate.Accent,
                        periodSelectorUnselectedContentColor = Slate.Muted,
                        timeSelectorSelectedContainerColor = Slate.AccentWash,
                        timeSelectorUnselectedContainerColor = Slate.Field,
                        timeSelectorSelectedContentColor = Slate.Accent,
                        timeSelectorUnselectedContentColor = Slate.Ink,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SlateButton(onClick = onDismiss) { Text("Cancel") }
                    SlateButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }, kind = ButtonKind.Primary) { Text("Set Time") }
                }
            }
        }
    }
}
