package com.clementine.panacea.data

import android.content.Context
import androidx.core.content.edit
import com.clementine.panacea.sound.CustomSound
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.sound.SoundSetting
import com.clementine.panacea.ui.theme.Themes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small app-wide choices, kept in SharedPreferences so the theme is known before the first frame.
 * Keys are stored; never rename one.
 */
/** Ways to lay out Today's medications; keys are stored */
enum class ListLayout(val key: String, val label: String) {
    CARDS("cards", "Cards"),
    GROUPED("grouped", "Grouped"),
    COMPACT("compact", "Compact"),
    WHEN_DUE("due", "When Due");

    companion object {
        // Grouped keeps everything a card shows, without a card for each medication
        val DEFAULT = GROUPED
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null) ?: firstTheme(context))
    /** A [Themes] key, or [THEME_SYSTEM]. */
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val sounds = SoundEvent.entries.associateWith { MutableStateFlow(readSound(it)) }

    private val _lastBackup = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 })
    /** When a backup was last saved from this phone; null if never. */
    val lastBackup: StateFlow<Long?> = _lastBackup.asStateFlow()

    fun setLastBackup(at: Long) {
        prefs.edit { putLong(KEY_LAST_BACKUP, at) }
        _lastBackup.value = at
    }

    private val _listLayout = MutableStateFlow(ListLayout.fromKey(prefs.getString(KEY_LIST_LAYOUT, null)))
    /** How Today lays out the medications */
    val listLayout: StateFlow<ListLayout> = _listLayout.asStateFlow()

    fun setListLayout(layout: ListLayout) {
        prefs.edit { putString(KEY_LIST_LAYOUT, layout.key) }
        _listLayout.value = layout
    }

    fun setTheme(key: String) {
        prefs.edit { putString(KEY_THEME, key) }
        _theme.value = key
    }

    fun sound(event: SoundEvent): StateFlow<SoundSetting> = sounds.getValue(event).asStateFlow()

    fun setSound(event: SoundEvent, setting: SoundSetting) {
        prefs.edit {
            putString("sound.${event.key}", setting.mode.key)
            putString("sound.${event.key}.file", setting.custom?.file)
            putString("sound.${event.key}.name", setting.custom?.name)
            putFloat("sound.${event.key}.volume", setting.volume)
        }
        sounds.getValue(event).value = setting
    }

    private fun readSound(event: SoundEvent): SoundSetting {
        val file = prefs.getString("sound.${event.key}.file", null)
        val name = prefs.getString("sound.${event.key}.name", null)
        val custom = if (file != null) CustomSound(file, name ?: file) else null
        return SoundSetting(SoundMode.fromKey(prefs.getString("sound.${event.key}", null)), custom, prefs.getFloat("sound.${event.key}.volume", 1f))
    }

    /** On first launch, carry over the theme picked in 3.4, if any. */
    private fun firstTheme(context: Context): String {
        val old = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val key = if (old.contains("selected_theme")) Themes.from34(old.getInt("selected_theme", 0)).key else Themes.DEFAULT.key
        prefs.edit { putString(KEY_THEME, key) }
        return key
    }

    companion object {
        private const val FILE = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_BACKUP = "backup.last"
        private const val KEY_LIST_LAYOUT = "today.layout"
        const val THEME_SYSTEM = "system"
    }
}
