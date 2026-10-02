package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TodayModelTest {

    private fun daily(vararg minutes: Int, medicationId: Long = 1) = ReminderEntity(
        medicationId = medicationId, repeat = RepeatType.DAILY, enabled = true, daysMask = 0x7F, times = minutes.toList(),
        intervalHours = 1, windowStart = 0, windowEnd = 0, dayOfMonth = 1, note = "",
    )

    @Test
    fun aDoseJustBeforeTheReminderCountsForIt() {
        // The 3.4 bug: taken at 8:30 for a 9:00 reminder, the reminder still came.
        val r = listOf(daily(9 * 60))
        val doses = listOf(ms(10, 2, 8, 30))
        val before = TodayModel.due(r, doses, doses.last(), at(10, 2, 8, 45))
        assertNull(before.overdueAt)
        assertEquals(at(10, 3, 9, 0), before.next)
        val after = TodayModel.due(r, doses, doses.last(), at(10, 2, 9, 10))
        assertNull(after.overdueAt)
        assertEquals(at(10, 3, 9, 0), after.next)
    }

    @Test
    fun aMissedReminderIsOverdueUntilLogged() {
        val r = listOf(daily(12 * 60 + 30))
        assertEquals(at(10, 2, 12, 30), TodayModel.due(r, emptyList(), null, now).overdueAt)
        val late = listOf(ms(10, 2, 12, 50))
        assertNull(TodayModel.due(r, late, late.last(), now).overdueAt)
        // A dose from before the early window belongs to the day before.
        val tooEarly = listOf(ms(10, 2, 11, 0))
        assertEquals(at(10, 2, 12, 30), TodayModel.due(r, tooEarly, tooEarly.last(), now).overdueAt)
    }

    @Test
    fun aReminderMissedLongAgoIsNoLongerDue() {
        assertNull(TodayModel.due(listOf(daily(30)), emptyList(), null, now).overdueAt)
    }

    @Test
    fun progressRunsFromTheLastDoseToTheNextReminder() {
        val doses = listOf(ms(10, 2, 9, 4))
        val due = TodayModel.due(listOf(daily(9 * 60)), doses, doses.last(), now)
        assertEquals(at(10, 3, 9, 0), due.next)
        // 4 h 12 min of 23 h 56 min
        assertEquals(252f / 1436f, due.progress, 0.001f)
    }

    @Test
    fun theSoonestOfSeveralRemindersIsNext() {
        val doses = listOf(ms(10, 2, 9, 0))
        val due = TodayModel.due(listOf(daily(9 * 60), daily(21 * 60), daily(18 * 60)), doses, doses.last(), now)
        assertEquals(at(10, 2, 18, 0), due.next)
    }

    @Test
    fun noRemindersMeansNothingDue() {
        val due = TodayModel.due(emptyList(), emptyList(), null, now)
        assertNull(due.next)
        assertNull(due.overdueAt)
        assertEquals(0f, due.progress)
    }

    @Test
    fun buildsCardsStripAndFilters() {
        val a = MedicationEntity(id = 1, name = "Ibuprofen", dose = 200.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 0)
        val b = MedicationEntity(id = 2, name = "Sertraline", dose = 50.0, doseUnit = "mg", category = "Prescribed", type = "ORAL_TABLET", sortOrder = 1)
        val doses = listOf(
            DoseEntity(id = 1, medicationId = 1, takenAt = ms(10, 1, 22, 10), multiplier = 2.0, amount = 400.0, unit = "mg"),
            DoseEntity(id = 2, medicationId = 1, takenAt = ms(10, 2, 10, 0), multiplier = 1.0, amount = 200.0, unit = "mg"),
            DoseEntity(id = 3, medicationId = 2, takenAt = ms(10, 2, 9, 4), multiplier = 1.0, amount = 50.0, unit = "mg"),
        )
        val ui = TodayModel.build(
            summaries = listOf(MedicationSummary(a, ms(10, 2, 10, 0)), MedicationSummary(b, ms(10, 2, 9, 4))),
            ingredients = emptyList(),
            reminders = listOf(daily(9 * 60, medicationId = 2)),
            doses = doses,
            presets = listOf(1.0),
            now = now,
            f = formats,
        )
        assertEquals("Friday, 2 October · 13:16", ui.header)
        assertEquals(listOf(Category.PRESCRIBED, Category.OTC), ui.categories)

        val ibuprofen = ui.cards[0]
        assertEquals("Taken at 10:00 · 3 h 16 min ago · 600 mg in the last 24 h", ibuprofen.lastTakenLine)
        assertNull(ibuprofen.dueLine)
        val sertraline = ui.cards[1]
        assertEquals("Next at 9:00 tomorrow", sertraline.dueLine)
        assertFalse(sertraline.overdue)

        assertEquals(2, ui.strip.count)
        assertEquals(listOf("Sertraline at 9:04", "Ibuprofen at 10:00"), ui.strip.dots.map { it.label })
        assertEquals((13 * 60 + 16) / 1440f, ui.strip.nowFraction, 0.0001f)
        assertEquals(listOf("0:00", "6:00", "12:00", "18:00", "24:00"), ui.strip.axis)
    }
}
