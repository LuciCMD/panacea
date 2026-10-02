package com.clementine.panacea.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clementine.panacea.ui.theme.Colors
import java.time.LocalTime

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
