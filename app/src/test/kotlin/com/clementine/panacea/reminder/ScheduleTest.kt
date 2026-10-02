package com.clementine.panacea.reminder

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Helsinki")

    // 2 October 2026 is a Friday.
    private fun at(month: Int, day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    private fun reminder(
        repeat: RepeatType = RepeatType.DAILY,
        times: List<Int> = listOf(9 * 60),
        daysMask: Int = 0x7F,
        intervalHours: Int = 4,
        windowStart: Int = 8 * 60,
        windowEnd: Int = 20 * 60,
        dayOfMonth: Int = 1,
        early: Int? = null,
    ) = ReminderEntity(
        medicationId = 1, repeat = repeat, enabled = true, daysMask = daysMask, times = times,
        intervalHours = intervalHours, windowStart = windowStart, windowEnd = windowEnd,
        dayOfMonth = dayOfMonth, note = "", earlyWindowMinutes = early,
    )

    @Test
    fun dailyFiresStrictlyAfterTheGivenTime() {
        val r = reminder()
        assertEquals(at(10, 2, 9, 0), Schedule.nextAfter(r, at(10, 2, 8, 0)))
        assertEquals(at(10, 3, 9, 0), Schedule.nextAfter(r, at(10, 2, 9, 0)))
    }

    @Test
    fun severalTimesADayComeInOrder() {
        val r = reminder(times = listOf(21 * 60, 9 * 60))
        assertEquals(at(10, 2, 21, 0), Schedule.nextAfter(r, at(10, 2, 13, 16)))
        assertEquals(at(10, 2, 9, 0), Schedule.latestAtOrBefore(r, at(10, 2, 13, 16)))
        assertEquals(at(10, 1, 21, 0), Schedule.latestAtOrBefore(r, at(10, 2, 8, 0)))
    }

    @Test
    fun weeklyKeepsToItsDays() {
        // Monday and Wednesday; Sunday is bit 0.
        val r = reminder(repeat = RepeatType.WEEKLY, daysMask = (1 shl 1) or (1 shl 3))
        assertEquals(at(10, 5, 9, 0), Schedule.nextAfter(r, at(10, 2, 13, 0)))
        assertEquals(at(9, 30, 9, 0), Schedule.latestAtOrBefore(r, at(10, 2, 13, 0)))
    }

    @Test
    fun monthlyOnThe31stFallsOnShorterMonthsLastDay() {
        val r = reminder(repeat = RepeatType.MONTHLY, dayOfMonth = 31)
        assertEquals(at(10, 31, 9, 0), Schedule.nextAfter(r, at(10, 2, 13, 0)))
        assertEquals(at(11, 30, 9, 0), Schedule.nextAfter(r, at(11, 1, 0, 0)))
    }

    @Test
    fun hourlyStepsThroughItsWindow() {
        val r = reminder(repeat = RepeatType.HOURLY)
        assertEquals(listOf(480, 720, 960, 1200), Schedule.slotsOfDay(r))
        assertEquals(at(10, 2, 16, 0), Schedule.nextAfter(r, at(10, 2, 13, 16)))
        assertEquals(at(10, 3, 8, 0), Schedule.nextAfter(r, at(10, 2, 20, 0)))
    }

    @Test
    fun aTimeInTheSpringGapMovesForward() {
        // Clocks in Helsinki jump from 3:00 to 4:00 on 29 March 2026.
        val r = reminder(times = listOf(3 * 60 + 30))
        assertEquals(at(3, 29, 4, 30), Schedule.nextAfter(r, at(3, 29, 0, 0)))
    }

    @Test
    fun aReminderWithNoTimesNeverFires() {
        val r = reminder(repeat = RepeatType.HOURLY, windowStart = 20 * 60, windowEnd = 8 * 60)
        assertNull(Schedule.nextAfter(r, at(10, 2, 0, 0)))
        assertNull(Schedule.latestAtOrBefore(r, at(10, 2, 0, 0)))
    }

    @Test
    fun earlyWindowNeverReachesTheNextTime() {
        assertEquals(60, Schedule.earlyWindowMinutes(reminder()))
        assertEquals(20, Schedule.earlyWindowMinutes(reminder(times = listOf(8 * 60, 8 * 60 + 40))))
        assertEquals(120, Schedule.earlyWindowMinutes(reminder(repeat = RepeatType.HOURLY)))
        assertEquals(30, Schedule.earlyWindowMinutes(reminder(repeat = RepeatType.HOURLY, intervalHours = 1)))
        assertEquals(15, Schedule.earlyWindowMinutes(reminder(early = 15)))
    }

    @Test
    fun aDoseCountsFromTheEarlyWindowOn() {
        val nine = at(10, 2, 9, 0)
        val now = at(10, 2, 9, 30).toInstant().toEpochMilli()
        fun ms(h: Int, m: Int) = at(10, 2, h, m).toInstant().toEpochMilli()
        assertTrue(Schedule.isCovered(nine, 60, listOf(ms(8, 0)), now))
        assertFalse(Schedule.isCovered(nine, 60, listOf(ms(7, 59)), now))
        assertTrue(Schedule.isCovered(nine, 60, listOf(ms(9, 20)), now))
        assertFalse(Schedule.isCovered(nine, 60, listOf(ms(9, 40)), now))
    }
}
