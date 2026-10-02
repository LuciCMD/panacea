package com.clementine.panacea.ui.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.minuteTicks
import com.clementine.panacea.ui.timeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

sealed interface MedicationState {
    data object Loading : MedicationState

    /** Removed, or never there. */
    data object Gone : MedicationState

    data class Ready(val ui: MedicationUi) : MedicationState
}

class MedicationViewModel(private val repository: MedicationRepository, private val formats: TimeFormats) : ViewModel() {

    fun observe(id: Long): Flow<MedicationState> = combine(
        repository.observeMedication(id),
        repository.observeIngredientsOf(id),
        repository.observeDosesOf(id),
        repository.observeRemindersOf(id),
        minuteTicks(),
    ) { med, ingredients, doses, reminders, _ ->
        if (med == null) {
            MedicationState.Gone
        } else {
            MedicationState.Ready(MedicationModel.build(med, ingredients, doses, reminders, ZonedDateTime.now(), formats))
        }
    }.flowOn(Dispatchers.Default)

    fun removeDose(id: Long) {
        viewModelScope.launch { repository.removeDose(id) }
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                MedicationViewModel(app.container.medications, timeFormats(app))
            }
        }
    }
}
