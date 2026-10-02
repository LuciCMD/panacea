package com.clementine.panacea.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.db.MedicationSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TodayViewModel(repository: MedicationRepository) : ViewModel() {

    /** Null until the database has answered, so the screen can tell "loading" from "empty". */
    val medications: StateFlow<List<MedicationSummary>?> = repository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TodayViewModel((this[APPLICATION_KEY] as PanaceaApp).container.medications)
            }
        }
    }
}
