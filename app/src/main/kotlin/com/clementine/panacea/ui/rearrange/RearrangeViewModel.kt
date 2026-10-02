package com.clementine.panacea.ui.rearrange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RearrangeItem(val id: Long, val name: String, val type: MedicationType, val category: Category, val photo: String?)

class RearrangeViewModel(private val repository: MedicationRepository) : ViewModel() {
    /** In Today's order; null until the database answers. */
    val items: StateFlow<List<RearrangeItem>?> = repository.observeSummaries()
        .map { list ->
            list.map {
                val m = it.medication
                RearrangeItem(m.id, m.name, MedicationType.fromKey(m.type), Category.fromKey(m.category), m.photoFront)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(order: List<Long>) {
        viewModelScope.launch { repository.reorder(order) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                RearrangeViewModel((this[APPLICATION_KEY] as PanaceaApp).container.medications)
            }
        }
    }
}

/** [list] with the item at [from] moved to [to], the others closing up around it. */
fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices || from == to) return list
    val out = list.toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
