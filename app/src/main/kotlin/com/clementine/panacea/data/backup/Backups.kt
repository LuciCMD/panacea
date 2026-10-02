package com.clementine.panacea.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.clementine.panacea.data.PhotoStore
import com.clementine.panacea.data.Settings
import com.clementine.panacea.data.db.MetaEntity
import com.clementine.panacea.data.db.MetaKeys
import com.clementine.panacea.data.db.PanaceaDatabase
import com.clementine.panacea.model.Amounts
import com.clementine.panacea.reminder.Reminders
import com.clementine.panacea.sound.CustomSound
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.sound.SoundLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup or export wrote. */
data class Written(val medications: Int, val doses: Int, val photos: Int, val sounds: Int)

/** A backup read and checked, its files set aside, waiting for the user to say yes. */
class PendingRestore internal constructor(
    val data: BackupData,
    /** What's on the phone now, for the question. */
    val currentMedications: Int,
    val currentDoses: Int,
    /** Photos named in the backup but not in the file (a bare JSON backup has none). */
    val missingPhotos: Int,
    internal val staged: File,
)

/**
 * Backup, restore and CSV export. A backup is one zip: `backup.json` ([BackupFormat]) with the pill
 * photos and custom sounds beside it. Restore takes that zip, the bare JSON, or 3.x's
 * `panacea_backup.json`, and replaces everything on the phone in one transaction.
 */
class Backups(
    private val context: Context,
    private val db: PanaceaDatabase,
    private val photos: PhotoStore,
    private val settings: Settings,
    private val sounds: SoundLibrary,
    private val reminders: Reminders,
) {
    private val resolver get() = context.contentResolver

    suspend fun backUp(uri: Uri): Written = withContext(Dispatchers.IO) {
        val data = snapshot()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val json = BackupFormat.write(data, version, System.currentTimeMillis())
        var photoCount = 0
        var soundCount = 0
        val out = resolver.openOutputStream(uri, "wt") ?: throw BackupException("That place can't be written to.")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.JSON_NAME))
            zip.write(json.toByteArray())
            zip.closeEntry()
            data.photos.map(photos::fileOf).filter { it.isFile }.forEach {
                zip.add(BackupFormat.PHOTOS + it.name, it)
                photoCount++
            }
            data.sounds.map { sounds.fileOf(CustomSound(it, it)) }.filter { it.isFile }.forEach {
                zip.add(BackupFormat.SOUNDS + it.name, it)
                soundCount++
            }
        }
        Written(data.medications.size, data.doses.size, photoCount, soundCount)
    }

    suspend fun exportCsv(uri: Uri): Written = withContext(Dispatchers.IO) {
        val (meds, doses) = db.withTransaction { db.medicationDao().all() to db.doseDao().all() }
        val csv = CsvExport.write(meds, doses, ZoneId.systemDefault())
        val out = resolver.openOutputStream(uri, "wt") ?: throw BackupException("That place can't be written to.")
        out.use { it.write(csv.toByteArray()) }
        Written(meds.size, doses.size, 0, 0)
    }

    /** Reads the file at [uri] and sets its photos and sounds aside. Throws [BackupException] with what's wrong. */
    suspend fun open(uri: Uri): PendingRestore = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val input = resolver.openInputStream(uri) ?: throw BackupException("That file couldn't be opened.")
            val json = BufferedInputStream(input).use { stream ->
                stream.mark(4)
                val zipped = stream.read() == 'P'.code && stream.read() == 'K'.code
                stream.reset()
                if (zipped) unzip(stream, staged) else readCapped(stream, MAX_JSON_BYTES)
            }
            val data = BackupFormat.read(json)
            val missing = data.photos.count { !File(staged, BackupFormat.PHOTOS + it).isFile }
            PendingRestore(data, db.medicationDao().count(), db.doseDao().count(), missing, staged)
        } catch (e: BackupException) {
            staged.deleteRecursively()
            throw e
        } catch (e: Exception) {
            staged.deleteRecursively()
            throw BackupException("That file couldn't be read as a Panacea backup.")
        }
    }

    fun discard(pending: PendingRestore) {
        pending.staged.deleteRecursively()
    }

    /** Replaces everything on the phone with [pending]'s backup. Once started it finishes, even if the screen goes. */
    suspend fun restore(pending: PendingRestore) = withContext(Dispatchers.IO + NonCancellable) {
        try {
            val now = System.currentTimeMillis()
            val data = BackupFormat.forRestore(pending.data, now).let { d ->
                // A photo that didn't come with the backup would only show as missing.
                d.copy(medications = d.medications.map { m ->
                    m.copy(
                        photoFront = m.photoFront?.takeIf { File(pending.staged, BackupFormat.PHOTOS + it).isFile },
                        photoBack = m.photoBack?.takeIf { File(pending.staged, BackupFormat.PHOTOS + it).isFile },
                    )
                })
            }
            val oldPhotos = db.medicationDao().photoFiles().toSet()

            // Files first: if the database step fails, the new files are only unused, and swept later.
            data.photos.forEach { File(pending.staged, BackupFormat.PHOTOS + it).copyTo(photos.fileOf(it), overwrite = true) }
            data.sounds.forEach { name ->
                val file = File(pending.staged, BackupFormat.SOUNDS + name)
                if (file.isFile) file.copyTo(sounds.fileOf(CustomSound(name, name)), overwrite = true)
            }

            reminders.replaceData {
                db.withTransaction {
                    db.reminderDao().deleteAll()
                    db.doseDao().deleteAll()
                    db.medicationDao().deleteAllIngredients()
                    db.medicationDao().deleteAll()
                    db.medicationDao().insertAll(data.medications)
                    db.medicationDao().insertIngredients(data.ingredients)
                    db.doseDao().insertAll(data.doses)
                    db.reminderDao().insertAll(data.reminders)
                    data.amountPresets?.let { db.metaDao().put(MetaEntity(MetaKeys.AMOUNT_PRESETS, it.joinToString(","))) }
                }
            }

            (oldPhotos - data.photos).forEach(photos::delete)
            data.settings?.let { applySettings(it) }
        } finally {
            pending.staged.deleteRecursively()
        }
    }

    private fun applySettings(s: BackupSettings) {
        s.theme?.let(settings::setTheme)
        SoundEvent.entries.forEach { event ->
            val setting = s.sounds[event.key] ?: return@forEach
            val old = settings.sound(event).value.custom
            // A custom sound whose file didn't come along falls back to the built-in one.
            val custom = setting.custom?.takeIf { sounds.fileOf(it).isFile }
            settings.setSound(event, setting.copy(custom = custom))
            if (old != null && old.file != custom?.file) sounds.fileOf(old).delete()
        }
    }

    private suspend fun snapshot(): BackupData = db.withTransaction {
        val meds = db.medicationDao().all()
        BackupData(
            medications = meds,
            ingredients = db.medicationDao().allIngredients(),
            doses = db.doseDao().all(),
            reminders = db.reminderDao().all(),
            amountPresets = db.metaDao().get(MetaKeys.AMOUNT_PRESETS)?.let(Amounts::parsePresets),
            settings = BackupSettings(settings.theme.value, SoundEvent.entries.associate { it.key to settings.sound(it).value }),
            exportedAt = null,
            legacy = false,
        )
    }

    /** Unpacks the zip's known entries into [dir]; returns the backup's JSON. Ignores anything else in it. */
    private fun unzip(input: InputStream, dir: File): String {
        var json: String? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == BackupFormat.JSON_NAME -> json = readCapped(zip, MAX_JSON_BYTES)
                    name.startsWith(BackupFormat.PHOTOS) || name.startsWith(BackupFormat.SOUNDS) -> {
                        val folder = name.substringBefore('/')
                        val file = name.substringAfter('/')
                        // Only plain names, so nothing can land outside the folder.
                        if (BackupFormat.isSafeName(file)) {
                            val target = File(dir, folder).apply { mkdirs() }.resolve(file)
                            total += copyCapped(zip, target, MAX_FILE_BYTES)
                            if (total > MAX_TOTAL_BYTES) throw BackupException("That backup is too large to restore.")
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return json ?: throw BackupException("That zip file isn't a Panacea backup.")
    }

    private fun readCapped(input: InputStream, max: Int): String {
        val bytes = input.readNBytes(max + 1)
        if (bytes.size > max) throw BackupException("That file is too large to be a Panacea backup.")
        return String(bytes)
    }

    private fun copyCapped(input: InputStream, target: File, max: Long): Long {
        var written = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > max) throw BackupException("That backup holds a file too large to restore.")
                out.write(buffer, 0, n)
            }
        }
        return written
    }

    private fun ZipOutputStream.add(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private companion object {
        const val MAX_JSON_BYTES = 64 * 1024 * 1024
        const val MAX_FILE_BYTES = SoundLibrary.MAX_BYTES + 1024 * 1024L
        const val MAX_TOTAL_BYTES = 1024 * 1024 * 1024L
    }
}
