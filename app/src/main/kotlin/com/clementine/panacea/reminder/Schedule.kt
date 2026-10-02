package com.clementine.panacea.reminder

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When a reminder fires, and which doses count for it. The repeat rules are the ones 3.4 used. */
object Schedule {
    /** Long enough for a monthly reminder on the 31st to find its next month. */
    private const val SCAN_DAYS = 400
    private const val MAX_HOURLY_SLOTS = 48
    private const val MINUTES_PER_DAY = 24 * 60
    const val DEFAULT_EARLY_MINUTES = 60

    /** Minutes after midnight when [r] fires on a day it's active, ascending. */
    fun slotsOfDay(r: ReminderEntity): List<Int> {
        val slots = when (r.repeat) {
            RepeatType.HOURLY -> {
                val step = r.intervalHours.coerceAtLeast(1) * 60
                generateSequence(r.windowStart) { it + step }
                    .takeWhile { it <= r.windowEnd }
                    .take(MAX_HOURLY_SLOTS)
                    .toList()
            }
            else -> r.times.sorted().distinct()
        }
        return slots.filter { it in 0 until MINUTES_PER_DAY }
    }

    fun isActive(r: ReminderEntity, date: LocalDate): Boolean = when (r.repeat) {
        RepeatType.MONTHLY -> date.dayOfMonth == r.dayOfMonth.coerceAtMost(date.lengthOfMonth())
        // DayOfWeek runs Monday = 1 … Sunday = 7; the mask has Sunday in bit 0.
        else -> r.daysMask and (1 shl (date.dayOfWeek.value % 7)) != 0
    }

    fun occurrencesOn(r: ReminderEntity, date: LocalDate, zone: ZoneId): List<ZonedDateTime> {
        if (!isActive(r, date)) return emptyList()
        return slotsOfDay(r).map { date.atTime(LocalTime.of(it / 60, it % 60)).atZone(zone) }
    }

    /** The first time [r] fires strictly after [from], or null if it never does. */
    fun nextAfter(r: ReminderEntity, from: ZonedDateTime): ZonedDateTime? {
        if (slotsOfDay(r).isEmpty()) return null
        var date = from.toLocalDate()
        repeat(SCAN_DAYS) {
            occurrencesOn(r, date, from.zone).firstOrNull { it.isAfter(from) }?.let { return it }
            date = date.plusDays(1)
        }
        return null
    }

    /** The last time [r] fired at or before [at], or null if it never did. */
    fun latestAtOrBefore(r: ReminderEntity, at: ZonedDateTime): ZonedDateTime? {
        if (slotsOfDay(r).isEmpty()) return null
        var date = at.toLocalDate()
        repeat(SCAN_DAYS) {
            occurrencesOn(r, date, at.zone).lastOrNull { !it.isAfter(at) }?.let { return it }
            date = date.minusDays(1)
        }
        return null
    }

    /**
     * How long before a reminder a dose may be logged and still count for it. Never more than half
     * the gap between two of its times, so one dose can't count for two of them.
     */
    fun earlyWindowMinutes(r: ReminderEntity): Int {
        r.earlyWindowMinutes?.let { return it.coerceAtLeast(0) }
        if (r.repeat == RepeatType.HOURLY) return r.intervalHours.coerceAtLeast(1) * 60 / 2
        val slots = slotsOfDay(r)
        if (slots.size < 2) return DEFAULT_EARLY_MINUTES
        val gaps = slots.zipWithNext { a, b -> b - a } + (MINUTES_PER_DAY - slots.last() + slots.first())
        return minOf(DEFAULT_EARLY_MINUTES, gaps.min() / 2)
    }

    /** True if a dose in [doseTimes] (epoch ms) was logged from the early window before [at] up to [until]. */
    fun isCovered(at: ZonedDateTime, earlyMinutes: Int, doseTimes: List<Long>, until: Long): Boolean {
        val from = at.minusMinutes(earlyMinutes.toLong()).toInstant().toEpochMilli()
        return doseTimes.any { it in from..until }
    }
}
