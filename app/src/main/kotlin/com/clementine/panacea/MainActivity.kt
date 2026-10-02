package com.clementine.panacea

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clementine.panacea.data.Settings
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundPlayer
import com.clementine.panacea.ui.PanaceaShell
import com.clementine.panacea.ui.edit.LocalPhotoStore
import com.clementine.panacea.ui.theme.PanaceaTheme
import com.clementine.panacea.ui.theme.Themes

class MainActivity : ComponentActivity() {
    private lateinit var sounds: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as PanaceaApp).container
        sounds = SoundPlayer(this, container.settings, container.soundLibrary)
        setContent {
            val key by container.settings.theme.collectAsStateWithLifecycle()
            val theme = resolveTheme(key, isSystemInDarkTheme())
            val light = theme.palette.isLight
            LaunchedEffect(light) {
                // Dark icons on a light theme, light icons on a dark one.
                val bars = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            PanaceaTheme(theme.palette) {
                CompositionLocalProvider(LocalSoundPlayer provides sounds, LocalPhotoStore provides container.photos) {
                    PanaceaShell()
                }
            }
        }
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }
}

/** Match System follows the phone: Slate when it's dark, Daylight when it's light. */
fun resolveTheme(key: String, systemDark: Boolean): Themes = when (key) {
    Settings.THEME_SYSTEM -> if (systemDark) Themes.SLATE else Themes.DAYLIGHT
    else -> Themes.fromKey(key) ?: Themes.DEFAULT
}
