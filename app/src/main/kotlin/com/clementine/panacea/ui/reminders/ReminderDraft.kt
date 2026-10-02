package com.clementine.panacea.ui.reminders

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.reminder.ReminderEngine
import com.clementine.panacea.reminder.Schedule
import java.time.ZonedDateTime

/** The reminder editor's form. Times are minutes after midnight. */
data class ReminderDraft(
    val id: Long = 0,
    val medicationId: Long = 0,
    val repeat: RepeatType = RepeatType.DAILY,
    /** Bit 0 = Sunday … bit 6 = Saturday. */
    val daysMask: Int = ReminderDrafts.ALL_DAYS,
    val times: List<Int> = listOf(9 * 60),
    val intervalHours: Int = 4,
    val windowStart: Int = 8 * 60,
    val windowEnd: Int = 22 * 60,
    val dayOfMonth: Int = 1,
    val note: String = "",
    /** Null is the automatic window. */
    val early: Int? = null,
) {
    val isNew get() = id == 0L
}

data class ReminderProblems(val medication: String? = null, val days: String? = null, val window: String? = null, val note: String? = null) {
    val none get() = medication == null && days == null && window == null && note == null
}

object ReminderDrafts {
    const val ALL_DAYS = 0x7F
    const val MAX_TIMES = 12
    const val MAX_NOTE = 120
    val EARLY_CHOICES: List<Int?> = listOf(null, 0, 15, 30, 60, 120, 180)
    val INTERVALS = (1..12).toList()

    fun from(r: ReminderEntity) = ReminderDraft(
        id = r.id,
        medicationId = r.medicationId,
        repeat = r.repeat,
        daysMask = r.daysMask and ALL_DAYS,
        times = r.times.ifEmpty { listOf(9 * 60) },
        intervalHours = r.intervalHours.coerceIn(1, 12),
        windowStart = r.windowStart,
        windowEnd = r.windowEnd,
        dayOfMonth = r.dayOfMonth.coerceIn(1, 31),
        note = r.note,
        early = r.earlyWindowMinutes,
    )

    fun check(d: ReminderDraft): ReminderProblems = ReminderProblems(
        medication = if (d.medicationId == 0L) "Pick a medication." else null,
        days = if (d.repeat in setOf(RepeatType.WEEKLY, RepeatType.HOURLY) && d.daysMask and ALL_DAYS == 0) "Pick at least one day." else null,
        window = if (d.repeat == RepeatType.HOURLY && d.windowEnd < d.windowStart) "End after it starts; the hours can't run past midnight." else null,
        note = if (d.note.trim().length > MAX_NOTE) "Keep the note under $MAX_NOTE characters." else null,
    )

    /**
     * The row to save. Counts and the pending state carry over from [existing]; a time already past
     * isn't treated as missed.
     */
    fun toEntity(d: ReminderDraft, existing: ReminderEntity?, now: ZonedDateTime): ReminderEntity {
        val base = existing ?: ReminderEntity(
            medicationId = d.medicationId, repeat = d.repeat, enabled = true, daysMask = ALL_DAYS, times = emptyList(),
            intervalHours = 4, windowStart = 8 * 60, windowEnd = 22 * 60, dayOfMonth = 1, note = "",
        )
        val shape = base.copy(
            medicationId = d.medicationId,
            repeat = d.repeat,
            // Daily and monthly reminders come every day the rule allows.
            daysMask = if (d.repeat in setOf(RepeatType.DAILY, RepeatType.MONTHLY)) ALL_DAYS else d.daysMask and ALL_DAYS,
            times = d.times.distinct().sorted().take(MAX_TIMES),
            intervalHours = d.intervalHours.coerceIn(1, 12),
            windowStart = d.windowStart,
            windowEnd = d.windowEnd,
            dayOfMonth = d.dayOfMonth.coerceIn(1, 31),
            note = d.note.trim(),
            earlyWindowMinutes = d.early,
        )
        // A reminder moved to another medication starts afresh.
        val fresh = if (existing != null && existing.medicationId != d.medicationId) shape.copy(pending = false) else shape
        return ReminderEngine.settled(fresh, now)
    }

    /** "Automatic (1 Hour)", "None", "15 Min", "2 Hours". */
    fun earlyLabel(minutes: Int?, d: ReminderDraft): String = when (minutes) {
        null -> "Automatic (${duration(Schedule.earlyWindowMinutes(toEntity(d.copy(early = null), null, ZonedDateTime.now())))})"
        0 -> "None"
        else -> duration(minutes)
    }

    fun duration(minutes: Int): String = when {
        minutes % 60 == 0 -> if (minutes == 60) "1 Hour" else "${minutes / 60} Hours"
        minutes > 60 -> "${minutes / 60} h ${minutes % 60} Min"
        else -> "$minutes Min"
    }

    fun interval(hours: Int) = if (hours == 1) "Every Hour" else "Every $hours Hours"
}
