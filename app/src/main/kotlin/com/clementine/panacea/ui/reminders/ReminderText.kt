package com.clementine.panacea.ui.reminders

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.reminder.Schedule
import com.clementine.panacea.ui.TimeFormats
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle

/** A reminder's schedule in words: "Every day at 9:00 and 21:00", "Weekdays at 8:30". */
object ReminderText {
    private const val ALL_DAYS = 0x7F
    private const val WEEKDAYS = 0b0111110
    private const val WEEKENDS = 0b1000001

    fun schedule(r: ReminderEntity, f: TimeFormats): String {
        val days = days(r.daysMask, f)
        return when (r.repeat) {
            RepeatType.HOURLY -> {
                val every = if (r.intervalHours <= 1) "Every hour" else "Every ${r.intervalHours} h"
                val span = "from ${time(r.windowStart, f)} to ${time(r.windowEnd, f)}"
                if (days == null) "$every $span" else "$days, ${every.lowercase()} $span"
            }
            RepeatType.MONTHLY -> {
                val lastDay = if (r.dayOfMonth > 28) " (or the month's last day)" else ""
                "Monthly on the ${ordinal(r.dayOfMonth)}$lastDay at ${times(r, f)}"
            }
            else -> "${days ?: "Every day"} at ${times(r, f)}"
        }
    }

    /** Null for every day; "Weekdays", "Weekends" or "Mon, Wed and Fri" otherwise. */
    private fun days(mask: Int, f: TimeFormats): String? = when (mask and ALL_DAYS) {
        ALL_DAYS, 0 -> null
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> natural(
            (0L until 7L).map { f.firstDayOfWeek.plus(it) }
                .filter { mask and (1 shl (it.value % 7)) != 0 }
                .map { it.getDisplayName(TextStyle.SHORT, f.locale) }
        )
    }

    private fun times(r: ReminderEntity, f: TimeFormats) = natural(Schedule.slotsOfDay(r).map { time(it, f) })

    private fun time(minutes: Int, f: TimeFormats) = LocalTime.of(minutes / 60 % 24, minutes % 60).format(f.time)

    /** "Muted until 14:00", "Muted until tomorrow", "Muted until Saturday at 9:00". */
    fun mutedUntil(until: Long, now: ZonedDateTime, f: TimeFormats): String {
        val at = Instant.ofEpochMilli(until).atZone(now.zone)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val midnight = at.toLocalTime() == LocalTime.MIDNIGHT
        return "Muted until " + when {
            days == 1L && midnight -> "tomorrow"
            days == 0L -> at.format(f.time)
            days == 1L -> "tomorrow at ${at.format(f.time)}"
            else -> "${at.format(f.weekday)} at ${at.format(f.time)}"
        }
    }

    /** "a", "a and b", "a, b and c". */
    fun natural(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
