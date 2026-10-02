package com.clementine.panacea

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clementine.panacea.data.Settings
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundPlayer
import com.clementine.panacea.ui.AppRequest
import com.clementine.panacea.ui.PanaceaShell
import com.clementine.panacea.ui.edit.LocalPhotoStore
import com.clementine.panacea.ui.theme.PanaceaTheme
import com.clementine.panacea.ui.theme.Themes

class MainActivity : ComponentActivity() {
    private lateinit var sounds: SoundPlayer
    private var request by mutableStateOf<AppRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as PanaceaApp).container
        sounds = SoundPlayer(this, container.settings, container.soundLibrary)
        // After a rotation the request was already handled.
        if (savedInstanceState == null) handle(intent)
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
                    PanaceaShell(request) { request = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A notification opens a medication ("panacea://medication/12"), asks to mute it, or to log it earlier. */
    private fun handle(intent: Intent) {
        val id = intent.data?.takeIf { it.scheme == "panacea" }?.lastPathSegment?.toLongOrNull() ?: return
        request = when (intent.action) {
            ACTION_OPEN_MEDICATION -> AppRequest.OpenMedication(id)
            ACTION_MUTE -> AppRequest.Mute(id)
            ACTION_TOOK_EARLIER -> AppRequest.TookEarlier(id)
            else -> return
        }
    }

    companion object {
        const val ACTION_OPEN_MEDICATION = "com.clementine.panacea.OPEN_MEDICATION"
        const val ACTION_MUTE = "com.clementine.panacea.MUTE"
        const val ACTION_TOOK_EARLIER = "com.clementine.panacea.TOOK_EARLIER"
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
