package com.clementine.panacea.ui.today

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
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.minuteTicks
import com.clementine.panacea.ui.timeFormats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/** A dose just logged, shown in the undo bar until it times out or is dealt with. */
data class LoggedDose(val taken: TakenDose, val message: String, val removeQuestion: String)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: MedicationRepository,
    val formats: TimeFormats,
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

    private val data = combine(
        repository.observeSummaries(),
        repository.observeIngredients(),
        repository.observeEnabledReminders(),
        recentDoses,
        repository.observePresets(),
    ) { summaries, ingredients, reminders, doses, presets -> Data(summaries, ingredients, reminders, doses, presets) }

    /** Null until the database has answered, so the screen can tell "loading" from "empty". */
    val ui: StateFlow<TodayUi?> = combine(data, clock) { d, _ ->
        // The clock only says when to redraw; a dose logged mid-minute must not land in the future.
        TodayModel.build(d.summaries, d.ingredients, d.reminders, d.doses, d.presets, ZonedDateTime.now(), formats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _logged = MutableStateFlow<LoggedDose?>(null)
    val logged: StateFlow<LoggedDose?> = _logged.asStateFlow()

    /** Logs a dose; [takenAt] null means now. */
    fun take(medicationId: Long, multiplier: Double, takenAt: Long? = null) {
        viewModelScope.launch {
            val now = ZonedDateTime.now()
            val taken = repository.takeDose(medicationId, multiplier, takenAt ?: now.toInstant().toEpochMilli()) ?: return@launch
            val at = Instant.ofEpochMilli(taken.dose.takenAt).atZone(now.zone)
            val amount = TodayText.amount(taken.medication, taken.dose.multiplier)
            _logged.value = LoggedDose(
                taken,
                TodayText.logged(taken.medication.name, amount, at, now, formats),
                TodayText.removeQuestion(taken.medication.name, amount, at, now, formats),
            )
        }
    }

    fun undo(logged: LoggedDose) {
        _logged.compareAndSet(logged, null)
        viewModelScope.launch { repository.undoDose(logged.taken) }
    }

    fun dismiss(logged: LoggedDose) {
        _logged.compareAndSet(logged, null)
    }

    fun addPreset(value: Double) {
        viewModelScope.launch { repository.addPreset(value) }
    }

    private data class Data(
        val summaries: List<MedicationSummary>,
        val ingredients: List<IngredientEntity>,
        val reminders: List<ReminderEntity>,
        val doses: List<DoseEntity>,
        val presets: List<Double>,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                TodayViewModel(app.container.medications, timeFormats(app))
            }
        }
    }
}
