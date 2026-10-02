package com.clementine.panacea.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.reminder.Schedule
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateSwitch
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.timeFormats
import com.clementine.panacea.ui.today.TodayText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class ReminderCard(
    val id: Long,
    val medication: String,
    val schedule: String,
    val note: String?,
    val enabled: Boolean,
    /** "Next at 9:00 tomorrow", or "Off". */
    val next: String,
)

class RemindersViewModel(private val repository: MedicationRepository, formats: TimeFormats) : ViewModel() {

    val cards: StateFlow<List<ReminderCard>?> = repository.observeReminderRows()
        .map { rows ->
            val now = ZonedDateTime.now()
            rows.map { row ->
                val r = row.reminder
                ReminderCard(
                    id = r.id,
                    medication = row.medicationName,
                    schedule = ReminderText.schedule(r, formats),
                    note = r.note.trim().ifEmpty { null },
                    enabled = r.enabled,
                    next = if (!r.enabled) "Off" else Schedule.nextAfter(r, now)?.let { TodayText.next(it, now, formats) } ?: "Never fires",
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                RemindersViewModel(app.container.medications, timeFormats(app))
            }
        }
    }
}

@Composable
fun RemindersScreen(viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val list = cards
        val on = list?.count { it.enabled } ?: 0
        item(key = "header") {
            ScreenHeader("Reminders", list?.takeIf { it.isNotEmpty() }?.let { "$on of ${it.size} on" })
        }
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                Text("Reminders you set up show up here.", style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
            }
        }
        items(list.orEmpty(), key = { it.id }) { card ->
            ReminderRow(card) { viewModel.setEnabled(card.id, it) }
        }
    }
}

@Composable
private fun ReminderRow(card: ReminderCard, onToggle: (Boolean) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SlateCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .toggleable(value = card.enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(card.medication, style = MaterialTheme.typography.titleMedium, color = Colors.Ink)
                Text(card.schedule, style = body, color = if (card.enabled) Colors.Ink else Colors.Muted)
                card.note?.let { Text(it, style = body, color = Colors.Muted) }
                Text(card.next, style = body, color = Colors.Muted)
            }
            // The whole card toggles, so the switch itself stays out of the way of screen readers.
            SlateSwitch(checked = card.enabled, onCheckedChange = null)
        }
    }
}
