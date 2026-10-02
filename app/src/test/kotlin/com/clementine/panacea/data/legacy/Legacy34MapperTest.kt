package com.clementine.panacea.data.legacy

import com.clementine.panacea.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Legacy34MapperTest {

    private fun med(name: String, doses: List<LegacyDose> = emptyList(), ingredients: List<LegacyIngredient> = emptyList()) =
        LegacyMedication(name, 10.0, "mg", "OTC", "ORAL_TABLET", ingredients, doses)

    private fun reminder(name: String, id: Int?) = LegacyReminder(
        id, name, RepeatType.DAILY, true, 0x7F, listOf(540), 4, 480, 1320, 1, "", 0, 0, false, 0, 0,
    )

    private fun snapshot(
        meds: List<LegacyMedication>,
        order: List<String> = emptyList(),
        reminders: List<LegacyReminder> = emptyList(),
        lastAmounts: Map<String, Double> = emptyMap(),
    ) = LegacySnapshot(meds, order, reminders, null, lastAmounts, emptyList())

    @Test
    fun keepsTheSavedOrderThenAddsTheRestByName() {
        val rows = Legacy34Mapper.map(snapshot(listOf(med("Zinc"), med("B"), med("A"), med("C")), order = listOf("C", "A", "Gone")))
        assertEquals(listOf("C", "A", "B", "Zinc"), rows.medications.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), rows.medications.map { it.sortOrder })
        assertEquals(listOf(1L, 2L, 3L, 4L), rows.medications.map { it.id })
    }

    @Test
    fun dosesAndIngredientsPointAtTheirMedication() {
        val rows = Legacy34Mapper.map(
            snapshot(
                listOf(
                    med("A"),
                    med("B", doses = listOf(LegacyDose(100, 5.0, 0.5)), ingredients = listOf(LegacyIngredient("X", 2.0, "mcg"))),
                ),
                order = listOf("A", "B"),
            )
        )
        val dose = rows.doses.single()
        assertEquals(2L, dose.medicationId)
        assertEquals(100L, dose.takenAt)
        assertEquals(0.5, dose.multiplier, 0.0)
        assertEquals(5.0, dose.amount, 0.0)
        assertEquals("mg", dose.unit)
        assertEquals(null, dose.weight)
        val ingredient = rows.ingredients.single()
        assertEquals(2L, ingredient.medicationId)
        assertEquals("mcg", ingredient.unit)
    }

    @Test
    fun rememberedAmountBecomesTheLastMultiplier() {
        val rows = Legacy34Mapper.map(snapshot(listOf(med("A"), med("B")), lastAmounts = mapOf("A" to 0.25)))
        assertEquals(listOf(0.25, 1.0), rows.medications.map { it.lastMultiplier })
    }

    @Test
    fun remindersKeepTheirIdsAndFindTheirMedication() {
        val rows = Legacy34Mapper.map(
            snapshot(
                listOf(med("A"), med("B")),
                order = listOf("A", "B"),
                reminders = listOf(reminder("B", -1268404526), reminder("A", null), reminder("A", -1268404526)),
            )
        )
        assertEquals(listOf(-1268404526L, 0L, 0L), rows.reminders.map { it.id })
        assertEquals(listOf(2L, 1L, 1L), rows.reminders.map { it.medicationId })
        assertTrue(rows.problems.isEmpty())
    }

    @Test
    fun orphanRemindersAndRepeatedNamesAreReported() {
        val rows = Legacy34Mapper.map(snapshot(listOf(med("A"), med("A")), reminders = listOf(reminder("Gone", 7))))
        assertEquals(1, rows.medications.size)
        assertTrue(rows.reminders.isEmpty())
        assertEquals(2, rows.problems.size)
    }
}
