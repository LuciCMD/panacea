package com.clementine.panacea.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.Settings
import com.clementine.panacea.data.backup.BackupException
import com.clementine.panacea.data.backup.Backups
import com.clementine.panacea.data.backup.PendingRestore
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.minuteTicks
import com.clementine.panacea.ui.timeFormats
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.sound.SoundLibrary
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.sound.SoundSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The line under the Backup card's buttons. */
data class BackupStatus(val text: String, val failed: Boolean = false)

class SettingsViewModel(
    private val medications: MedicationRepository,
    private val settings: Settings,
    private val library: SoundLibrary,
    private val backups: Backups,
    private val formats: TimeFormats,
) : ViewModel() {
    val theme: StateFlow<String> = settings.theme

    /** Medications in Recently Removed, newest first. */
    val removed: StateFlow<List<RemovedItem>> = medications.observeRemoved()
        .map { list ->
            val now = ZonedDateTime.now()
            list.map { RemovedItem(it.id, it.name, RemovedText.status(it.removedAt ?: 0, now)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) {
        viewModelScope.launch { medications.restore(id) }
    }

    fun sound(event: SoundEvent): StateFlow<SoundSetting> = settings.sound(event)

    private val _problems = MutableStateFlow<Map<SoundEvent, String>>(emptyMap())
    /** Why the last file picked for a sound wasn't used. */
    val problems: StateFlow<Map<SoundEvent, String>> = _problems.asStateFlow()

    private val _adding = MutableStateFlow<SoundEvent?>(null)
    val adding: StateFlow<SoundEvent?> = _adding.asStateFlow()

    fun setTheme(key: String) = settings.setTheme(key)

    fun setMode(event: SoundEvent, mode: SoundMode) {
        val current = settings.sound(event).value
        settings.setSound(event, current.copy(mode = mode))
        _problems.update { it - event }
    }

    fun addSound(event: SoundEvent, uri: Uri, onAdded: (SoundSetting) -> Unit) {
        viewModelScope.launch {
            _adding.value = event
            when (val result = library.add(event, uri)) {
                is SoundLibrary.Result.Added -> {
                    _problems.update { it - event }
                    onAdded(result.setting)
                }
                is SoundLibrary.Result.Failed -> _problems.update { it + (event to result.message) }
            }
            _adding.value = null
        }
    }

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    /** "Saving…", "Reading…" while a backup task runs; the buttons wait meanwhile. */
    private val _working = MutableStateFlow<String?>(null)
    val working: StateFlow<String?> = _working.asStateFlow()

    private val _pending = MutableStateFlow<PendingRestore?>(null)
    /** A backup read and waiting for the user to confirm the restore. */
    val pending: StateFlow<PendingRestore?> = _pending.asStateFlow()

    /** When the last backup was saved, or null while there's nothing worth backing up. */
    val backupAge: StateFlow<BackupText.Age?> = combine(settings.lastBackup, medications.observeSummaries(), minuteTicks()) { last, meds, now ->
        if (last == null && meds.isEmpty()) null else BackupText.age(last, now, formats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun backUp(uri: Uri) = run("Saving…", "The backup couldn't be saved there.") {
        val written = backups.backUp(uri)
        settings.setLastBackup(System.currentTimeMillis())
        BackupText.backedUp(written)
    }

    fun exportCsv(uri: Uri) = run("Exporting…", "The CSV couldn't be saved there.") { BackupText.exported(backups.exportCsv(uri)) }

    fun open(uri: Uri) = run("Reading…", "That file couldn't be read as a Panacea backup.") {
        _pending.value = backups.open(uri)
        null
    }

    fun question(p: PendingRestore): BackupText.Question = BackupText.question(
        medications = p.data.medications.size,
        doses = p.data.doses.size,
        reminders = p.data.reminders.size,
        legacy = p.data.legacy,
        exportedAt = p.data.exportedAt,
        currentMedications = p.currentMedications,
        currentDoses = p.currentDoses,
        missingPhotos = p.missingPhotos,
        problems = p.data.problems.size,
        settings = p.data.settings != null,
        zone = ZoneId.systemDefault(),
        date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(formats.locale),
    )

    fun restore() {
        val p = _pending.value ?: return
        _pending.value = null
        run("Restoring…", "The restore didn't finish, so nothing was changed.") {
            backups.restore(p)
            BackupText.restored(p.data.medications.size, p.data.doses.size, p.data.problems.size)
        }
    }

    fun cancelRestore() {
        _pending.value?.let(backups::discard)
        _pending.value = null
    }

    /** Runs one backup task at a time; [block] returns what to say when it's done, or null for nothing. */
    private fun run(label: String, failure: String, block: suspend () -> String?) {
        if (_working.value != null) return
        _working.value = label
        _backupStatus.value = null
        viewModelScope.launch {
            _backupStatus.value = try {
                block()?.let { BackupStatus(it) }
            } catch (e: BackupException) {
                BackupStatus(e.message ?: failure, failed = true)
            } catch (e: Exception) {
                BackupStatus(failure, failed = true)
            }
            _working.value = null
        }
    }

    override fun onCleared() {
        _pending.value?.let(backups::discard)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PanaceaApp).container
                SettingsViewModel(container.medications, container.settings, container.soundLibrary, container.backups, timeFormats(this[APPLICATION_KEY] as PanaceaApp))
            }
        }
    }
}
