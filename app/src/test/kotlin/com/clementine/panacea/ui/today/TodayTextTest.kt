package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTextTest {
    private val med = MedicationEntity(name = "A", dose = 50.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 0)

    private fun ingredient(name: String) = IngredientEntity(medicationId = 1, position = 0, name = name, amount = 1.0, unit = "mg")

    @Test
    fun lastTakenWordsDependOnTheDay() {
        assertEquals("Not taken yet", TodayText.lastTaken(null, "50 mg", now, formats))
        assertEquals("Took 50 mg at 9:04", TodayText.lastTaken(ms(10, 2, 9, 4), "50 mg", now, formats))
        assertEquals("Took 2 gummies yesterday at 22:10", TodayText.lastTaken(ms(10, 1, 22, 10), "2 gummies", now, formats))
        assertEquals("Took 50 mg on Monday at 8:00", TodayText.lastTaken(ms(9, 28, 8, 0), "50 mg", now, formats))
        assertEquals("Took 50 mg on 21 Sep at 8:00", TodayText.lastTaken(ms(9, 21, 8, 0), "50 mg", now, formats))
    }

    @Test
    fun nextWordsDependOnTheDay() {
        assertEquals("Next at 21:00", TodayText.next(at(10, 2, 21, 0), now, formats))
        assertEquals("Next at 9:00 tomorrow", TodayText.next(at(10, 3, 9, 0), now, formats))
        assertEquals("Next on Monday at 9:00", TodayText.next(at(10, 5, 9, 0), now, formats))
        assertEquals("Next on 14 Oct at 9:00", TodayText.next(at(10, 14, 9, 0), now, formats))
    }

    @Test
    fun overdueSaysWhenAndHowLongAgo() {
        assertEquals("Due at 12:30 · 46 min ago, not logged yet", TodayText.overdue(at(10, 2, 12, 30), now, formats))
        assertEquals(
            "Due yesterday at 22:00 · 3 h ago, not logged yet",
            TodayText.overdue(at(10, 1, 22, 0), at(10, 2, 1, 0), formats),
        )
    }

    @Test
    fun agoRoundsDownToTheMinute() {
        assertEquals("just now", TodayText.ago(ms(10, 2, 13, 16), now))
        assertEquals("12 min ago", TodayText.ago(ms(10, 2, 13, 4), now))
        assertEquals("4 h ago", TodayText.ago(ms(10, 2, 9, 16), now))
        assertEquals("4 h 12 min ago", TodayText.ago(ms(10, 2, 9, 4), now))
    }

    @Test
    fun doseLineLeavesOutWhatIsNotSet() {
        assertEquals("50 mg", TodayText.doseLine(med, emptyList()))
        assertEquals("50 mg · weighs 0.2 g", TodayText.doseLine(med.copy(weight = 0.20), emptyList()))
        assertEquals("50 mg · weighs 0.01 oz", TodayText.doseLine(med.copy(weight = 0.01, weightUnit = "oz"), emptyList()))
        assertEquals("50 mg · with Caffeine", TodayText.doseLine(med, listOf(ingredient("Caffeine"))))
        assertEquals(
            "Vitamin C and 2 more",
            TodayText.doseLine(med.copy(dose = 0.0), listOf(ingredient("Vitamin C"), ingredient("Zinc"), ingredient("Iron"))),
        )
        assertEquals("No dose set", TodayText.doseLine(med.copy(dose = 0.0), emptyList()))
    }

    @Test
    fun amountsScaleWithTheMultiplier() {
        assertEquals("100 mg", TodayText.amount(med, 2.0))
        assertEquals("2 tablets", TodayText.amount(med.copy(dose = 0.0), 2.0))
        assertEquals("0.62 g", TodayText.weightOf(med.copy(weight = 0.31), 2.0))
        assertEquals(null, TodayText.weightOf(med, 2.0))
        assertEquals("× 0.25", TodayText.multiplier(0.25))
    }

    @Test
    fun undoWordsNameTheDose() {
        assertEquals("A 50 mg logged at 13:16", TodayText.logged("A", "50 mg", now, now, formats))
        assertEquals("A 50 mg logged for 11:30", TodayText.logged("A", "50 mg", at(10, 2, 11, 30), now, formats))
        assertEquals("A 50 mg logged for yesterday at 23:00", TodayText.logged("A", "50 mg", at(10, 1, 23, 0), now, formats))
        assertEquals(
            "A 50 mg taken at 13:16 will be removed from your history.",
            TodayText.removeQuestion("A", "50 mg", now, now, formats),
        )
    }
}
