package com.clementine.panacea.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import com.clementine.panacea.data.Settings

/**
 * Plays the take and removal sounds. One prepared [MediaPlayer] per sound, so any format the phone
 * can decode works at any length; it's rebuilt only when the choice changes. Owned by the activity.
 */
class SoundPlayer(private val context: Context, private val settings: Settings, private val library: SoundLibrary) {
    private val players = mutableMapOf<SoundEvent, Pair<SoundSetting, MediaPlayer>>()

    fun play(event: SoundEvent) = play(event, settings.sound(event).value)

    /** Plays [setting] for [event]; also used to try a choice before keeping it. */
    fun play(event: SoundEvent, setting: SoundSetting) {
        if (setting.effective == SoundMode.NONE) return
        val player = player(event, setting) ?: player(event, SoundSetting(SoundMode.BUILT_IN, null)) ?: return
        player.setVolume(setting.gain, setting.gain)
        if (player.isPlaying) player.seekTo(0) else player.start()
    }

    private fun player(event: SoundEvent, setting: SoundSetting): MediaPlayer? {
        // Volume is set on each play, so changing it doesn't rebuild the player
        val source = setting.copy(volume = 1f)
        players[event]?.let { (cached, player) -> if (cached == source) return player }
        release(event)
        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(ATTRIBUTES)
                val custom = setting.custom
                if (setting.effective == SoundMode.CUSTOM && custom != null) {
                    setDataSource(library.fileOf(custom).path)
                } else {
                    context.resources.openRawResourceFd(event.builtIn).use { setDataSource(it) }
                }
                setOnCompletionListener { it.seekTo(0) }
                prepare()
            }
            players[event] = source to player
            player
        } catch (e: Exception) {
            Log.w("Panacea", "Can't play the ${event.key} sound", e)
            null
        }
    }

    private fun release(event: SoundEvent) {
        players.remove(event)?.second?.release()
    }

    fun release() = SoundEvent.entries.forEach(::release)

    companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer> { error("No SoundPlayer provided") }
