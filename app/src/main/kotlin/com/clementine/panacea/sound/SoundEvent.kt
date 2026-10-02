package com.clementine.panacea.sound

import androidx.annotation.RawRes
import com.clementine.panacea.R

/** The moments that make a sound. Keys are stored; never rename one. */
enum class SoundEvent(val key: String, val label: String, @param:RawRes val builtIn: Int) {
    TAKE("take", "Taking a Dose", R.raw.gobble),
    UNDO("undo", "Removing a Dose", R.raw.undo),
}

enum class SoundMode(val key: String) {
    BUILT_IN("builtin"), NONE("none"), CUSTOM("custom");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: BUILT_IN
    }
}

/** A sound file the user picked, copied into the app's own storage as [file]. */
data class CustomSound(val file: String, val name: String)

data class SoundSetting(val mode: SoundMode, val custom: CustomSound?) {
    /** What actually plays: a custom choice whose file is gone falls back to the built-in sound. */
    val effective: SoundMode get() = if (mode == SoundMode.CUSTOM && custom == null) SoundMode.BUILT_IN else mode
}
