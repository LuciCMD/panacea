package com.clementine.panacea.ui.settings

import com.clementine.panacea.data.backup.Written
import com.clementine.panacea.ui.reminders.ReminderText.natural
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The words of the Backup card, kept apart from Compose so they can be tested. */
object BackupText {
    data class Question(val title: String, val text: String, val confirm: String, val destructive: Boolean)

    fun backupName(today: LocalDate) = "Panacea Backup $today.zip"

    fun csvName(today: LocalDate) = "Panacea Doses $today.csv"

    fun backedUp(w: Written): String {
        val parts = mutableListOf(count(w.medications, "medication"), count(w.doses, "dose"))
        if (w.photos > 0) parts += count(w.photos, "photo")
        if (w.sounds > 0) parts += count(w.sounds, "sound")
        return "Saved ${natural(parts)}."
    }

    fun exported(w: Written) = "Exported ${count(w.doses, "dose")}."

    fun restored(medications: Int, doses: Int, problems: Int): String {
        val text = "Restored ${count(medications, "medication")} and ${count(doses, "dose")}."
        return if (problems == 0) text else "$text ${leftOut(problems, done = true)}"
    }

    fun question(
        medications: Int,
        doses: Int,
        reminders: Int,
        legacy: Boolean,
        exportedAt: Long?,
        currentMedications: Int,
        currentDoses: Int,
        missingPhotos: Int,
        problems: Int,
        /** The backup carries a theme and sounds. */
        settings: Boolean,
        zone: ZoneId,
        date: DateTimeFormatter,
    ): Question {
        val which = buildString {
            append(if (legacy) "This Panacea 3 backup" else "This backup")
            exportedAt?.let { append(" from ").append(Instant.ofEpochMilli(it).atZone(zone).format(date)) }
        }
        val holds = natural(listOfNotNull(count(medications, "medication"), count(doses, "dose"), reminders.takeIf { it > 0 }?.let { count(it, "reminder") }))
        val empty = currentMedications == 0 && currentDoses == 0
        val notes = buildList {
            if (settings && !empty) add("The theme and sounds come from the backup too.")
            if (missingPhotos > 0) add("${count(missingPhotos, "photo")} ${if (missingPhotos == 1) "isn't" else "aren't"} in this file, so those pills will show without one.")
            if (problems > 0) add(leftOut(problems, done = false))
        }
        val effect = if (empty) {
            "$which has $holds."
        } else {
            "$which has $holds. Restoring it replaces the ${count(currentMedications, "medication")} and ${count(currentDoses, "dose")} " +
                "on this phone, with their reminders and photos."
        }
        val text = (listOf(effect) + notes).joinToString(" ")
        return if (empty) Question("Restore This Backup?", text, "Restore", destructive = false)
        else Question("Replace Everything?", text, "Replace", destructive = true)
    }

    private fun leftOut(problems: Int, done: Boolean): String {
        val left = when {
            !done -> "will be left out"
            problems == 1 -> "was left out"
            else -> "were left out"
        }
        return if (problems == 1) "1 item couldn't be read and $left." else "$problems items couldn't be read and $left."
    }

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
