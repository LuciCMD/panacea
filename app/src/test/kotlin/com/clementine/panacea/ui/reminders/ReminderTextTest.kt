package com.clementine.panacea.ui.reminders

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.today.Fixtures.formats
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTextTest {
    private fun reminder(
        repeat: RepeatType = RepeatType.DAILY,
        times: List<Int> = listOf(9 * 60),
        daysMask: Int = 0x7F,
        intervalHours: Int = 4,
        dayOfMonth: Int = 1,
    ) = ReminderEntity(
        medicationId = 1, repeat = repeat, enabled = true, daysMask = daysMask, times = times,
        intervalHours = intervalHours, windowStart = 8 * 60, windowEnd = 20 * 60, dayOfMonth = dayOfMonth, note = "",
    )

    private fun say(r: ReminderEntity) = ReminderText.schedule(r, formats)

    @Test
    fun dailyListsItsTimesInOrder() {
        assertEquals("Every day at 9:00", say(reminder()))
        assertEquals("Every day at 9:00 and 21:00", say(reminder(times = listOf(21 * 60, 9 * 60))))
        assertEquals("Every day at 8:00, 13:30 and 21:00", say(reminder(times = listOf(480, 810, 1260))))
    }

    @Test
    fun weeklyNamesItsDaysFromTheFirstDayOfTheWeek() {
        // Sunday is bit 0.
        val monWedSun = (1 shl 1) or (1 shl 3) or (1 shl 0)
        assertEquals("Mon, Wed and Sun at 9:00", say(reminder(repeat = RepeatType.WEEKLY, daysMask = monWedSun)))
        assertEquals("Weekdays at 9:00", say(reminder(repeat = RepeatType.WEEKLY, daysMask = 0b0111110)))
        assertEquals("Weekends at 9:00", say(reminder(repeat = RepeatType.WEEKLY, daysMask = 0b1000001)))
        assertEquals("Every day at 9:00", say(reminder(repeat = RepeatType.WEEKLY)))
    }

    @Test
    fun hourlyGivesItsSpan() {
        assertEquals("Every 4 h from 8:00 to 20:00", say(reminder(repeat = RepeatType.HOURLY)))
        assertEquals("Every hour from 8:00 to 20:00", say(reminder(repeat = RepeatType.HOURLY, intervalHours = 1)))
        assertEquals("Weekdays, every 4 h from 8:00 to 20:00", say(reminder(repeat = RepeatType.HOURLY, daysMask = 0b0111110)))
    }

    @Test
    fun monthlyUsesOrdinals() {
        assertEquals("Monthly on the 1st at 9:00", say(reminder(repeat = RepeatType.MONTHLY)))
        assertEquals("Monthly on the 31st (or the month's last day) at 9:00", say(reminder(repeat = RepeatType.MONTHLY, dayOfMonth = 31)))
        assertEquals(listOf("2nd", "3rd", "11th", "12th", "13th", "22nd", "23rd"), listOf(2, 3, 11, 12, 13, 22, 23).map(ReminderText::ordinal))
    }
}
