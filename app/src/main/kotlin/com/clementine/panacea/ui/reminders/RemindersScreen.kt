package com.clementine.panacea.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.reminder.Reminders
import com.clementine.panacea.reminder.Schedule
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.minuteTicks
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.timeFormats
import com.clementine.panacea.ui.today.TodayText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** "Next at 9:00 tomorrow", "Muted until 14:00" or "Off". */
    val next: String,
    val muted: Boolean = false,
)

fun reminderCard(r: ReminderEntity, medication: String, now: ZonedDateTime, f: TimeFormats, mutedUntil: Long = 0): ReminderCard {
    val muted = r.enabled && mutedUntil > now.toInstant().toEpochMilli()
    return ReminderCard(
        id = r.id,
        medication = medication,
        schedule = ReminderText.schedule(r, f),
        note = r.note.trim().ifEmpty { null },
        enabled = r.enabled,
        next = when {
            !r.enabled -> "Off"
            muted -> ReminderText.mutedUntil(mutedUntil, now, f)
            else -> Schedule.nextAfter(r, now)?.let { TodayText.next(it, now, f) } ?: "Never comes"
        },
        muted = muted,
    )
}

data class RemindersUi(val cards: List<ReminderCard>, val muteAllUntil: Long, val muteAllText: String?, val hasMedications: Boolean)

class RemindersViewModel(private val repository: MedicationRepository, private val reminders: Reminders, formats: TimeFormats) : ViewModel() {

    val ui: StateFlow<RemindersUi?> = combine(
        repository.observeReminderRows(),
        reminders.observeMuteAll(),
        repository.observeSummaries(),
        minuteTicks(),
    ) { rows, muteAll, meds, _ ->
        val now = ZonedDateTime.now()
        val nowMs = now.toInstant().toEpochMilli()
        RemindersUi(
            cards = rows.map { reminderCard(it.reminder, it.medicationName, now, formats, maxOf(it.medicationMutedUntil, muteAll)) },
            muteAllUntil = muteAll,
            muteAllText = if (muteAll > nowMs) ReminderText.mutedUntil(muteAll, now, formats) else null,
            hasMedications = meds.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val names: StateFlow<Map<Long, String>?> = repository.observeNames()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }

    /** [medicationId] null mutes or unmutes every reminder. */
    fun mute(medicationId: Long?, until: Long) {
        viewModelScope.launch { reminders.mute(medicationId, until) }
    }

    fun unmute(medicationId: Long?) {
        viewModelScope.launch { reminders.unmute(medicationId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                RemindersViewModel(app.container.medications, app.container.reminders, timeFormats(app))
            }
        }
    }
}

@Composable
fun RemindersScreen(
    onAdd: () -> Unit,
    onOpen: (reminderId: Long) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var muting by rememberSaveable { mutableStateOf(false) }
    val state = ui ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val on = state.cards.count { it.enabled }
        val subtitle = state.muteAllText?.let { "All " + it.replaceFirstChar(Char::lowercase) }
            ?: state.cards.takeIf { it.isNotEmpty() }?.let { "$on of ${it.size} on" }
        item(key = "header") {
            ScreenHeader("Reminders", subtitle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.cards.isNotEmpty()) {
                        val muted = state.muteAllText != null
                        SlateButton(onClick = { if (muted) viewModel.unmute(null) else muting = true }) {
                            Icon(if (muted) Glyphs.Bell else Glyphs.BellOff, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(if (muted) "Unmute" else "Mute All")
                        }
                    }
                    if (state.hasMedications) {
                        SlateButton(
                            onClick = onAdd,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Add Reminder" },
                        ) { Icon(Glyphs.Plus, contentDescription = null) }
                    }
                }
            }
        }
        if (state.cards.any { it.enabled }) {
            item(key = "notifications") { NotificationsOffCard() }
        }
        if (state.cards.isEmpty()) {
            item(key = "empty") {
                val text = if (state.hasMedications) "Tap + to set up a reminder." else "Add a medication first, then set up its reminders here."
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
            }
        }
        items(state.cards, key = { it.id }) { card ->
            ReminderRow(card, onToggle = { viewModel.setEnabled(card.id, it) }, onOpen = { onOpen(card.id) })
        }
    }

    if (muting) {
        MuteDialog(
            title = "Mute All Reminders",
            onMute = { until ->
                viewModel.mute(null, until)
                muting = false
            },
            onDismiss = { muting = false },
        )
    }
}
