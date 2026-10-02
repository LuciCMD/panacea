package com.clementine.panacea.ui.reminders

import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.Prompt
import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDraftTest {
    @Test
    fun whatMustBeFilledIn() {
        assertEquals("Pick a medication.", ReminderDrafts.check(ReminderDraft()).medication)
        assertEquals("Pick at least one day.", ReminderDrafts.check(ReminderDraft(medicationId = 1, repeat = RepeatType.WEEKLY, daysMask = 0)).days)
        // Daily ignores the days it isn't showing.
        assertTrue(ReminderDrafts.check(ReminderDraft(medicationId = 1, daysMask = 0)).none)
        val backwards = ReminderDraft(medicationId = 1, repeat = RepeatType.HOURLY, windowStart = 20 * 60, windowEnd = 8 * 60)
        assertTrue(ReminderDrafts.check(backwards).window != null)
    }

    @Test
    fun savingKeepsCountsAndTidiesTimes() {
        val existing = ReminderEntity(
            id = 4, medicationId = 1, repeat = RepeatType.WEEKLY, enabled = false, daysMask = 0b0000010, times = listOf(540),
            intervalHours = 4, windowStart = 480, windowEnd = 1320, dayOfMonth = 1, note = "", timesCompleted = 7, timesMissed = 2,
            lastFiredAt = ms(9, 28, 9, 0),
        )
        val draft = ReminderDrafts.from(existing).copy(repeat = RepeatType.DAILY, times = listOf(1260, 540, 540), note = " With food ")
        val saved = ReminderDrafts.toEntity(draft, existing, now)
        assertEquals(listOf(540, 1260), saved.times)
        assertEquals(0x7F, saved.daysMask)
        assertEquals("With food", saved.note)
        assertEquals(7, saved.timesCompleted)
        assertFalse(saved.enabled)
        // Today's 9:00 has passed; it isn't caught up as missed.
        assertEquals(ms(10, 2, 9, 0), saved.lastFiredAt)
    }

    @Test
    fun earlyChoicesReadPlainly() {
        val d = ReminderDraft(medicationId = 1, times = listOf(9 * 60, 10 * 60))
        // Two times an hour apart leave 30 minutes each.
        assertEquals("Automatic (30 Min)", ReminderDrafts.earlyLabel(null, d))
        assertEquals("None", ReminderDrafts.earlyLabel(0, d))
        assertEquals("2 Hours", ReminderDrafts.earlyLabel(120, d))
        assertEquals("Every Hour", ReminderDrafts.interval(1))
    }

    @Test
    fun muteEndsReadNaturally() {
        assertEquals("Muted until 15:16", ReminderText.mutedUntil(ms(10, 2, 15, 16), now, formats))
        assertEquals("Muted until tomorrow", ReminderText.mutedUntil(ms(10, 3, 0, 0), now, formats))
        assertEquals("Muted until tomorrow at 1:16", ReminderText.mutedUntil(ms(10, 3, 1, 16), now, formats))
        assertEquals(ms(10, 3, 0, 0), MuteChoice.TOMORROW.until(now))
        assertEquals(at(10, 2, 15, 16).toInstant().toEpochMilli(), MuteChoice.TWO_HOURS.until(now))
    }

    @Test
    fun laterIsFromNowAndNotTodayEndsAtMidnight() {
        assertEquals(ms(10, 2, 13, 26), LaterChoice.TEN_MINUTES.until(now))
        assertEquals(ms(10, 2, 17, 16), LaterChoice.FOUR_HOURS.until(now))
        assertEquals(ms(10, 3, 0, 0), LaterChoice.NOT_TODAY.until(now))
    }

    @Test
    fun aNotificationsButtonIsKeptThroughRotation() {
        listOf(Prompt.Reminder(-1268404526), Prompt.Learned(3)).forEach { assertEquals(it, Prompt.fromKey(it.key)) }
        assertEquals(null, Prompt.fromKey("medication:3"))
    }
}
