package com.clementine.panacea.ui.today

import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS
import android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.TakenDose
import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.reminder.Reminders
import com.clementine.panacea.reminder.Routines
import com.clementine.panacea.reminder.Settled
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.minuteTicks
import com.clementine.panacea.ui.timeFormats
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the bar stays, before Android's "Time to take action" setting lengthens it. */
const val DOSE_BAR_MS = 15_000L

/** A dose just logged or just removed, shown in a bar until it times out or is acted on. */
sealed interface DoseNotice {
    val taken: TakenDose
    val message: String
}

/** Offers Undo, which removes it in one tap. */
data class LoggedDose(override val taken: TakenDose, val settled: Settled, override val message: String) : DoseNotice

/** Offers Put Back, in case Undo was the slip. */
data class RemovedDose(override val taken: TakenDose, override val message: String) : DoseNotice

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: MedicationRepository,
    private val reminders: Reminders,
    val formats: TimeFormats,
    /** How long the bar stays, given [DOSE_BAR_MS]. */
    private val barShownMs: (Long) -> Long = { it },
) : ViewModel() {

    /** Ticks at each new minute, only while the screen is watched. */
    private val clock = minuteTicks().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Two days back covers the last 24 hours and any reminder still counted as due.
    private val recentDoses = clock
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            repository.observeDosesSince(day.minusDays(2).atStartOfDay(ZonedDateTime.now().zone).toInstant().toEpochMilli())
        }

    // Routine learns from the last four weeks itself, so a start fixed when the screen opens is enough.
    private val learningTimes = repository.observeLearningTimes(System.currentTimeMillis() - Routines.WINDOW.toMillis())
        .map { times -> times.groupBy({ it.medicationId }, { it.takenAt }) }

    private val data = combine(
        repository.observeSummaries(),
        repository.observeIngredients(),
        repository.observeEnabledReminders(),
        combine(recentDoses, learningTimes, repository.observeAllMedications(), ::Triple),
        repository.observePresets(),
    ) { summaries, ingredients, reminders, (doses, learning, all), presets -> Data(summaries, ingredients, reminders, doses, learning, presets, all) }

    /** Null until the database has answered, so the screen can tell "loading" from "empty". */
    val ui: StateFlow<TodayUi?> = combine(data, clock) { d, _ ->
        // The clock only says when to redraw; a dose logged mid-minute must not land in the future.
        TodayModel.build(d.summaries, d.ingredients, d.reminders, d.doses, d.presets, ZonedDateTime.now(), formats, d.learning, d.all)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the 3.4 import couldn't bring over; empty when nothing, or once put away. */
    val importProblems: StateFlow<List<String>> =
        repository.observeImportProblems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismissImportProblems() {
        viewModelScope.launch { repository.dismissImportProblems() }
    }

    private val _notice = MutableStateFlow<DoseNotice?>(null)
    val notice: StateFlow<DoseNotice?> = _notice.asStateFlow()

    /** Logs a dose; [takenAt] null means now. */
    fun take(medicationId: Long, multiplier: Double, takenAt: Long? = null) {
        viewModelScope.launch {
            val now = ZonedDateTime.now()
            val taken = repository.takeDose(medicationId, multiplier, takenAt ?: now.toInstant().toEpochMilli()) ?: return@launch
            // A dose settles a reminder that's showing, and keeps one coming soon quiet.
            val settled = reminders.doseLogged(medicationId, taken.dose.takenAt)
            val at = Instant.ofEpochMilli(taken.dose.takenAt).atZone(now.zone)
            show(LoggedDose(taken, settled, TodayText.logged(taken.medication.name, amountOf(taken), at, now, formats)))
        }
    }

    /** Removes the dose just logged, and brings back any reminder it put away. */
    fun undo(logged: LoggedDose) {
        if (!_notice.compareAndSet(logged, null)) return
        viewModelScope.launch {
            repository.undoDose(logged.taken)
            reminders.doseUndone(logged.taken.medication.id, logged.settled)
            show(RemovedDose(logged.taken, TodayText.removed(logged.taken.medication.name, amountOf(logged.taken))))
        }
    }

    /** Logs the dose just removed again, as it was. */
    fun putBack(removed: RemovedDose) {
        if (!_notice.compareAndSet(removed, null)) return
        take(removed.taken.medication.id, removed.taken.dose.multiplier, removed.taken.dose.takenAt)
    }

    private suspend fun show(notice: DoseNotice) {
        _notice.value = notice
        // Counted here rather than on screen, so leaving the screen doesn't stop the clock.
        delay(barShownMs(DOSE_BAR_MS))
        _notice.compareAndSet(notice, null)
    }

    private fun amountOf(taken: TakenDose) = TodayText.amount(taken.medication, taken.dose.multiplier)

    fun addPreset(value: Double) {
        viewModelScope.launch { repository.addPreset(value) }
    }

    private data class Data(
        val summaries: List<MedicationSummary>,
        val ingredients: List<IngredientEntity>,
        val reminders: List<ReminderEntity>,
        val doses: List<DoseEntity>,
        val learning: Map<Long, List<Long>>,
        val presets: List<Double>,
        val all: List<MedicationEntity>,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                val a11y = app.getSystemService(AccessibilityManager::class.java)
                TodayViewModel(app.container.medications, app.container.reminders, timeFormats(app)) { ms ->
                    a11y.getRecommendedTimeoutMillis(ms.toInt(), FLAG_CONTENT_TEXT or FLAG_CONTENT_CONTROLS).toLong()
                }
            }
        }
    }
}
