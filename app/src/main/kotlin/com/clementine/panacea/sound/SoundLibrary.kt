package com.clementine.panacea.sound

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.clementine.panacea.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies sound files the user picks into the app's own storage, after checking the phone can play them. */
class SoundLibrary(private val context: Context, private val settings: Settings) {
    private val dir get() = File(context.filesDir, "sounds").apply { mkdirs() }

    sealed interface Result {
        data class Added(val setting: SoundSetting) : Result
        data class Failed(val message: String) : Result
    }

    fun fileOf(sound: CustomSound) = File(dir, sound.file)

    suspend fun add(event: SoundEvent, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Sound"

        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val data = input.readNBytes(MAX_BYTES + 1)
                if (data.size > MAX_BYTES) return@withContext Result.Failed("That file is over 20 MB. A short clip works best.")
                data
            }
        } catch (e: Exception) {
            null
        } ?: return@withContext Result.Failed("That file couldn't be opened.")

        val (content, extension) = if (Aiff.isAiff(bytes)) {
            try {
                Aiff.toWav(bytes) to "wav"
            } catch (e: Aiff.Unsupported) {
                return@withContext Result.Failed("This AIFF file uses compression Android can't play. Save it as WAV or AIFF without compression.")
            }
        } else {
            bytes to extensionOf(name, resolver.getType(uri))
        }

        val file = File(dir, "${event.key}-${System.currentTimeMillis()}.$extension")
        file.writeBytes(content)
        if (!playable(file)) {
            file.delete()
            return@withContext Result.Failed("This phone can't play that file. Try MP3, AAC, FLAC, Ogg, Opus or WAV.")
        }

        // Keep only the newest file per sound.
        val old = settings.sound(event).value.custom
        val setting = SoundSetting(SoundMode.CUSTOM, CustomSound(file.name, name))
        settings.setSound(event, setting)
        old?.let { fileOf(it).delete() }
        Result.Added(setting)
    }

    private fun playable(file: File): Boolean = try {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(SoundPlayer.ATTRIBUTES)
            player.setDataSource(file.path)
            player.prepare()
            player.duration > 0
        } finally {
            player.release()
        }
    } catch (e: Exception) {
        false
    }

    private fun extensionOf(name: String, mime: String?): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "audio"

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024

        /** What the file picker offers; some formats aren't labelled audio by every app. */
        val PICKER_TYPES = arrayOf("audio/*", "application/ogg", "application/x-ogg", "video/ogg", "video/webm", "video/3gpp", "video/mp4")
    }
}
