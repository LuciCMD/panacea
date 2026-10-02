package com.clementine.panacea.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.clementine.panacea.R

/** The short sounds for taking and removing a dose, loaded once into a [SoundPool]. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val taken = pool.load(context, R.raw.gobble, 1)
    private val removed = pool.load(context, R.raw.undo, 1)

    fun taken() = play(taken)
    fun removed() = play(removed)

    private fun play(id: Int) {
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

@Composable
fun rememberSounds(): Sounds {
    val context = LocalContext.current
    val sounds = remember { Sounds(context.applicationContext) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}
