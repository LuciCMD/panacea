package com.clementine.panacea.ui.reminders

import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.db.MedicationName
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.timeFormats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

class EditReminderViewModel(private val repository: MedicationRepository, val formats: TimeFormats) : ViewModel() {

    val medications: StateFlow<List<MedicationName>?> = repository.observeNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun load(id: Long): ReminderDraft? = repository.reminder(id)?.let(ReminderDrafts::from)

    fun save(draft: ReminderDraft, onSaved: () -> Unit) {
        viewModelScope.launch {
            val existing = if (draft.isNew) null else repository.reminder(draft.id)
            repository.saveReminder(ReminderDrafts.toEntity(draft, existing, ZonedDateTime.now()))
            onSaved()
        }
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteReminder(id)
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PanaceaApp
                EditReminderViewModel(app.container.medications, timeFormats(app))
            }
        }

        /** Keeps a half-made reminder through rotation and the app being put away. */
        val DraftSaver: Saver<ReminderDraft?, String> = Saver(
            save = { d ->
                d?.let {
                    JSONObject()
                        .put("id", it.id).put("medicationId", it.medicationId).put("repeat", it.repeat.name)
                        .put("days", it.daysMask).put("times", JSONArray(it.times))
                        .put("interval", it.intervalHours).put("start", it.windowStart).put("end", it.windowEnd)
                        .put("dayOfMonth", it.dayOfMonth).put("note", it.note).put("early", it.early ?: -1)
                        .toString()
                } ?: ""
            },
            restore = { text ->
                if (text.isEmpty()) {
                    null
                } else {
                    val o = JSONObject(text)
                    val times = o.getJSONArray("times")
                    ReminderDraft(
                        id = o.getLong("id"),
                        medicationId = o.getLong("medicationId"),
                        repeat = RepeatType.valueOf(o.getString("repeat")),
                        daysMask = o.getInt("days"),
                        times = (0 until times.length()).map(times::getInt),
                        intervalHours = o.getInt("interval"),
                        windowStart = o.getInt("start"),
                        windowEnd = o.getInt("end"),
                        dayOfMonth = o.getInt("dayOfMonth"),
                        note = o.getString("note"),
                        early = o.getInt("early").takeIf { it >= 0 },
                    )
                }
            },
        )
    }
}
