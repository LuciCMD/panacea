package com.clementine.panacea.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clementine.panacea.ui.theme.Colors
import java.time.LocalTime
import java.time.ZonedDateTime

/** When a dose picked as [time] on [yesterday] or today was taken; a time still to come today can only be yesterday. */
fun takenAt(time: LocalTime, yesterday: Boolean, now: ZonedDateTime): ZonedDateTime {
    val today = now.with(time).withSecond(0).withNano(0)
    return if (yesterday || today.isAfter(now)) today.minusDays(1) else today
}

/** Whether [time] today is still to come. */
private fun later(time: LocalTime, now: ZonedDateTime) = now.with(time).withSecond(0).withNano(0).isAfter(now)

/** When a dose was taken: a time, today or yesterday. */
@Composable
fun DoseTimeDialog(initial: ZonedDateTime, onPick: (ZonedDateTime) -> Unit, onDismiss: () -> Unit, confirmLabel: String = "Set Time") {
    val now = remember { ZonedDateTime.now(initial.zone) }
    var yesterday by rememberSaveable { mutableStateOf(initial.toLocalDate() < now.toLocalDate()) }
    TimeDialog(
        title = "When Did You Take It?",
        initial = initial.toLocalTime(),
        confirmLabel = confirmLabel,
        onPick = { onPick(takenAt(it, yesterday, now)) },
        onDismiss = onDismiss,
        day = { time ->
            val later = later(time, now)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                SlateChip(selected = !yesterday && !later, onClick = { yesterday = false }) { Text("Today") }
                SlateChip(selected = yesterday || later, onClick = { yesterday = true }) { Text("Yesterday") }
            }
            if (later && !yesterday) {
                Text("That time hasn't come yet today.", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
            }
        },
    )
}

/** A clock to pick a time on, in the phone's 12- or 24-hour style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    title: String,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    confirmLabel: String = "Set Time",
    /** Shown above the clock, with the time as it's being picked. */
    day: (@Composable (LocalTime) -> Unit)? = null,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = title }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Colors.Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (day != null) {
                    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        day(LocalTime.of(state.hour, state.minute))
                    }
                }
                TimePicker(
                    state = state,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Colors.Field,
                        clockDialSelectedContentColor = Colors.OnAccent,
                        clockDialUnselectedContentColor = Colors.Ink,
                        selectorColor = Colors.Accent,
                        containerColor = Colors.Surface,
                        periodSelectorBorderColor = Colors.Line,
                        periodSelectorSelectedContainerColor = Colors.AccentWash,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = Colors.Accent,
                        periodSelectorUnselectedContentColor = Colors.Muted,
                        timeSelectorSelectedContainerColor = Colors.AccentWash,
                        timeSelectorUnselectedContainerColor = Colors.Field,
                        timeSelectorSelectedContentColor = Colors.Accent,
                        timeSelectorUnselectedContentColor = Colors.Ink,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SlateButton(onClick = onDismiss) { Text("Cancel") }
                    SlateButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }, kind = ButtonKind.Primary) { Text(confirmLabel) }
                }
            }
        }
    }
}
