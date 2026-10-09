package com.clementine.panacea.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.timeFormats
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: MedicationRepository, formats: TimeFormats) : ViewModel() {

    /** Null until the database has answered. */
    val days: StateFlow<List<HistoryDay>?> = repository.observeHistory()
        .map { HistoryModel.build(it, ZonedDateTime.now(), formats) }
        // Every dose ever logged; built on the main thread it froze the tab while opening
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(id: Long) {
        viewModelScope.launch { repository.removeDose(id) }
    }

    fun changeTime(id: Long, takenAt: Long) {
        viewModelScope.launch { repository.changeDoseTime(id, takenAt) }
    }

    fun changeAmount(id: Long, multiplier: Double) {
        viewModelScope.launch { repository.changeDoseAmount(id, multiplier) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                HistoryViewModel(app.container.medications, timeFormats(app))
            }
        }
    }
}
