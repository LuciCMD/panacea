package com.clementine.panacea.reminder

import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {
    /** Daily at 9:00 and 21:00, so the automatic early window is 60 minutes. */
    private val daily = ReminderEntity(
        id = 1, medicationId = 1, repeat = RepeatType.DAILY, enabled = true, daysMask = 0x7F, times = listOf(9 * 60, 21 * 60),
        intervalHours = 4, windowStart = 0, windowEnd = 0, dayOfMonth = 1, note = "",
    )
    private val nine = ms(10, 2, 9, 0)
    private val minute = 60_000L

    @Test
    fun aDoseInTheEarlyWindowKeepsItQuiet() {
        // The 3.4 complaint: taken at 8:50, the 9:00 reminder still went off.
        val fired = ReminderEngine.fire(daily, nine, listOf(nine - 10 * minute), mutedUntil = 0, now = nine, snoozed = false)
        assertEquals(FireAction.QUIET, fired.action)
        assertFalse(fired.reminder.pending)
        assertEquals(1, fired.reminder.timesCompleted)
        // A dose from before the window doesn't count.
        val early = ReminderEngine.fire(daily, nine, listOf(nine - 61 * minute), 0, nine, false)
        assertEquals(FireAction.NOTIFY, early.action)
        assertTrue(early.reminder.pending)
        assertEquals(nine, early.reminder.lastFiredAt)
    }

    @Test
    fun stillPendingWhenItFiresAgainIsAMiss() {
        val waiting = daily.copy(pending = true, lastFiredAt = ms(10, 1, 21, 0))
        val fired = ReminderEngine.fire(waiting, nine, emptyList(), 0, nine, false)
        assertEquals(1, fired.reminder.timesMissed)
        assertEquals(FireAction.NOTIFY, fired.action)
    }

    @Test
    fun mutedRemindersAreHeldNotLost() {
        val fired = ReminderEngine.fire(daily, nine, emptyList(), mutedUntil = nine + 60 * minute, now = nine, snoozed = false)
        assertEquals(FireAction.HOLD, fired.action)
        assertTrue(fired.reminder.pending)
        // Shown when the mute ends later that day…
        assertTrue(ReminderEngine.showOnRelease(fired.reminder, at(10, 2, 10, 0)))
        // …but Until Tomorrow means not today: nothing from yesterday at midnight.
        assertFalse(ReminderEngine.showOnRelease(fired.reminder, at(10, 3, 0, 0)))
    }

    @Test
    fun aSnoozeEndingAfterTheDoseSaysNothing() {
        val waiting = daily.copy(pending = true, lastFiredAt = nine)
        assertEquals(FireAction.NOTIFY, ReminderEngine.fire(waiting, nine, emptyList(), 0, nine + 10 * minute, snoozed = true).action)
        val covered = ReminderEngine.fire(waiting, nine, listOf(nine + 5 * minute), 0, nine + 10 * minute, snoozed = true)
        assertEquals(FireAction.QUIET, covered.action)
        assertEquals(1, covered.reminder.timesCompleted)
        // Settled meanwhile (Taken, or a dose in the app): nothing to show, nothing counted.
        assertEquals(Fired(daily, FireAction.QUIET), ReminderEngine.fire(daily, nine, emptyList(), 0, nine + 10 * minute, snoozed = true))
    }

    @Test
    fun aDoseCountsForAShowingReminderFromItsEarlyWindowOn() {
        val waiting = daily.copy(pending = true, lastFiredAt = nine)
        assertTrue(ReminderEngine.countsFor(waiting, nine + 30 * minute))
        // Logged as taken earlier, still inside the window.
        assertTrue(ReminderEngine.countsFor(waiting, nine - 30 * minute))
        assertFalse(ReminderEngine.countsFor(waiting, nine - 2 * 60 * minute))
        assertFalse(ReminderEngine.countsFor(daily, nine))
    }

    @Test
    fun aTimeMissedWhileThePhoneWasOffIsCaughtUpIfRecent() {
        val firedLastNight = daily.copy(lastFiredAt = ms(10, 1, 21, 0))
        // now is 13:16: the 9:00 never fired.
        assertEquals(at(10, 2, 9, 0), ReminderEngine.missedWhileAway(firedLastNight, now))
        // Too long ago to be worth a notification now.
        assertNull(ReminderEngine.missedWhileAway(firedLastNight, at(10, 2, 15, 30)))
        assertNull(ReminderEngine.missedWhileAway(daily.copy(lastFiredAt = nine), now))
    }

    @Test
    fun aNewReminderDoesntCountTodaysPastTimeAsMissed() {
        val added = ReminderEngine.settled(daily, now)
        assertEquals(nine, added.lastFiredAt)
        assertNull(ReminderEngine.missedWhileAway(added, now))
        // A pending one keeps the time it's waiting on.
        val waiting = daily.copy(pending = true, lastFiredAt = ms(10, 1, 21, 0))
        assertEquals(waiting, ReminderEngine.settled(waiting, now))
    }

    @Test
    fun theNotificationSaysHowMuch() {
        val med = MedicationEntity(name = "Omeprazole", dose = 20.0, doseUnit = "mg", category = "", type = "ORAL_CAPSULE", sortOrder = 0)
        assertEquals("Time for Omeprazole", ReminderEngine.title(med))
        assertEquals("20 mg, 1 capsule. With lunch.", ReminderEngine.text(med, " With lunch. "))
        assertEquals("40 mg, 2 capsules.", ReminderEngine.text(med.copy(lastMultiplier = 2.0), ""))
        assertEquals("1.5 gummies.", ReminderEngine.text(med.copy(dose = 0.0, type = "EDIBLE", lastMultiplier = 1.5), ""))
    }
}
