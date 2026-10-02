package com.clementine.panacea.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.Settings
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.sound.SoundLibrary
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.sound.SoundSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: Settings, private val library: SoundLibrary) : ViewModel() {
    val theme: StateFlow<String> = settings.theme

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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PanaceaApp).container
                SettingsViewModel(container.settings, container.soundLibrary)
            }
        }
    }
}
