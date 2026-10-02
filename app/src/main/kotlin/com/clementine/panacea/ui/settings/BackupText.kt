package com.clementine.panacea.ui.settings

import com.clementine.panacea.ui.counted
import com.clementine.panacea.data.backup.Written
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.reminders.ReminderText.natural
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

/** The words of the Backup card, kept apart from Compose so they can be tested. */
object BackupText {
    /** Days after which an old backup is called out. */
    const val DUE_DAYS = 30

    data class Question(val title: String, val text: String, val confirm: String, val destructive: Boolean)

    fun backupName(today: LocalDate) = "Panacea Backup $today.zip"

    fun csvName(today: LocalDate) = "Panacea Doses $today.csv"

    fun backedUp(w: Written): String {
        val parts = mutableListOf(count(w.medications, "medication"), count(w.doses, "dose"))
        if (w.photos > 0) parts += count(w.photos, "photo")
        if (w.sounds > 0) parts += count(w.sounds, "sound")
        return "Saved ${natural(parts)}."
    }

    /** "Last backed up today at 9:12", "… 28 Aug · 35 days ago"; [warn] once it's [DUE_DAYS] old or there's none. */
    data class Age(val text: String, val warn: Boolean)

    fun age(lastBackup: Long?, now: ZonedDateTime, f: TimeFormats): Age {
        lastBackup ?: return Age("Not backed up yet", warn = true)
        val at = Instant.ofEpochMilli(lastBackup).atZone(now.zone)
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate()).coerceAtLeast(0)
        val text = when (days) {
            0L -> "today at ${at.format(f.time)}"
            1L -> "yesterday at ${at.format(f.time)}"
            else -> "${at.format(f.shortDate)} · ${days} days ago"
        }
        return Age("Last backed up $text", warn = days >= DUE_DAYS)
    }

    /**
     * Whether the Settings tab should call out the backup: the last one is [DUE_DAYS] old, or there's
     * never been one and doses go back that far. A phone set up last week isn't nagged.
     */
    fun due(lastBackup: Long?, firstDose: Long?, now: ZonedDateTime): Boolean {
        val since = lastBackup ?: firstDose ?: return false
        return ChronoUnit.DAYS.between(Instant.ofEpochMilli(since).atZone(now.zone).toLocalDate(), now.toLocalDate()) >= DUE_DAYS
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

    private fun count(n: Int, noun: String) = counted(n, noun)
}
