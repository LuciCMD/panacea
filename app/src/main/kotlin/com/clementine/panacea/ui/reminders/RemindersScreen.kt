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
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.reminder.Routines
import com.clementine.panacea.reminder.Reminders
import com.clementine.panacea.reminder.Schedule
import com.clementine.panacea.ui.Prompt
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
import java.time.Instant
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
        next = listOfNotNull(
            when {
                !r.enabled -> "Off"
                muted -> ReminderText.mutedUntil(mutedUntil, now, f)
                else -> Schedule.nextAfter(r, now)?.let { TodayText.next(it, now, f) } ?: "Never comes"
            },
            ReminderText.taken(r.timesCompleted, r.timesMissed),
        ).joinToString(" · "),
        muted = muted,
    )
}

/** A medication learning its routine, as the Reminders tab shows it. */
data class LearnedCard(
    val medicationId: Long,
    val medication: String,
    /** "Usually around 23:30" */
    val usually: String,
    /** "Asks at 0:05 if nothing is logged", or "Muted until 14:00". */
    val next: String,
    val muted: Boolean,
)

fun learnedCard(med: MedicationEntity, doseTimes: List<Long>, now: ZonedDateTime, f: TimeFormats, mutedUntil: Long): LearnedCard {
    val routine = Routines.learn(doseTimes, now)
    val nextAsk = Routines.nextAsk(routine, now)?.let { Instant.ofEpochMilli(it.ask).atZone(now.zone) }
    val (usually, next) = RoutineText.card(routine, nextAsk, now, f)
    val muted = mutedUntil > now.toInstant().toEpochMilli()
    return LearnedCard(med.id, med.name, usually, if (muted) ReminderText.mutedUntil(mutedUntil, now, f) else next, muted)
}

data class RemindersUi(
    val cards: List<ReminderCard>,
    val learned: List<LearnedCard>,
    val muteAllUntil: Long,
    val muteAllText: String?,
    val hasMedications: Boolean,
) {
    val isEmpty get() = cards.isEmpty() && learned.isEmpty()
}

class RemindersViewModel(private val repository: MedicationRepository, private val reminders: Reminders, formats: TimeFormats) : ViewModel() {

    private val learning = combine(
        repository.observeLearning(),
        repository.observeLearningTimes(System.currentTimeMillis() - Routines.WINDOW.toMillis()),
    ) { meds, times -> meds to times.groupBy({ it.medicationId }, { it.takenAt }) }

    val ui: StateFlow<RemindersUi?> = combine(
        repository.observeReminderRows(),
        reminders.observeMuteAll(),
        repository.observeSummaries(),
        learning,
        minuteTicks(),
    ) { rows, muteAll, meds, (learningMeds, times), _ ->
        val now = ZonedDateTime.now()
        val nowMs = now.toInstant().toEpochMilli()
        val order = meds.map { it.medication.id }
        RemindersUi(
            cards = rows.map { reminderCard(it.reminder, it.medicationName, now, formats, maxOf(it.medicationMutedUntil, muteAll)) },
            learned = learningMeds.sortedBy { order.indexOf(it.id) }.map {
                learnedCard(it, times[it.id].orEmpty(), now, formats, maxOf(it.mutedUntil, muteAll))
            },
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

    fun stopLearning(medicationId: Long) {
        viewModelScope.launch { repository.setLearnRoutine(medicationId, false) }
    }

    /** [medicationId] null mutes or unmutes every reminder. */
    fun mute(medicationId: Long?, until: Long) {
        viewModelScope.launch { reminders.mute(medicationId, until) }
    }

    fun unmute(medicationId: Long?) {
        viewModelScope.launch { reminders.unmute(medicationId) }
    }

    /** The medication a notification is about; null if it's gone. */
    suspend fun medicationOf(prompt: Prompt): Long? = when (prompt) {
        is Prompt.Reminder -> repository.reminder(prompt.id)?.medicationId
        is Prompt.Learned -> prompt.id
    }

    /** Already Logged, or a dose logged with Took It Earlier: puts the notification away as taken. */
    fun dealtWith(prompt: Prompt) {
        viewModelScope.launch {
            when (prompt) {
                is Prompt.Reminder -> reminders.dealtWith(prompt.id)
                is Prompt.Learned -> reminders.askDealtWith(prompt.id)
            }
        }
    }

    fun later(prompt: Prompt, medicationId: Long, choice: LaterChoice) {
        viewModelScope.launch {
            val until = choice.until(ZonedDateTime.now())
            when {
                choice == LaterChoice.NOT_TODAY -> reminders.mute(medicationId, until)
                prompt is Prompt.Reminder -> reminders.snooze(prompt.id, until)
                prompt is Prompt.Learned -> reminders.askLater(prompt.id, until)
            }
        }
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
    onOpenMedication: (medicationId: Long) -> Unit,
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
        // Learned reminders are on for as long as they're listed.
        val on = state.cards.count { it.enabled } + state.learned.size
        val total = state.cards.size + state.learned.size
        val subtitle = state.muteAllText?.let { "All " + it.replaceFirstChar(Char::lowercase) }
            ?: if (total > 0) "$on of $total on" else null
        item(key = "header") {
            ScreenHeader("Reminders", subtitle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.isEmpty) {
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
        if (state.cards.any { it.enabled } || state.learned.isNotEmpty()) {
            item(key = "notifications") { NotificationsOffCard() }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                val text = if (state.hasMedications) {
                    "Tap + to set up a reminder, or turn on Learn My Routine on a medication's page."
                } else {
                    "Add a medication first, then set up its reminders here."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
            }
        }
        items(state.cards, key = { it.id }) { card ->
            ReminderRow(card, onToggle = { viewModel.setEnabled(card.id, it) }, onOpen = { onOpen(card.id) })
        }
        items(state.learned, key = { "learned-${it.medicationId}" }) { card ->
            LearnedRow(card, onStop = { viewModel.stopLearning(card.medicationId) }, onOpen = { onOpenMedication(card.medicationId) })
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
