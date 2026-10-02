package com.clementine.panacea.data

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.ui.edit.Drafts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseChangeTest {
    private val med = MedicationEntity(
        id = 1, name = "Sertraline", dose = 50.0, doseUnit = "mg", weight = 0.35, weightUnit = "g",
        category = "Prescribed", type = "ORAL_TABLET", sortOrder = 0,
    )

    private fun dose(multiplier: Double, amount: Double, unit: String = "mg", weight: Double? = 0.35 * multiplier, weightUnit: String? = "g") =
        DoseEntity(medicationId = 1, takenAt = 0, multiplier = multiplier, amount = amount, unit = unit, weight = weight, weightUnit = weightUnit)

    @Test
    fun aFixedDoseChangesOnlyDosesLoggedAtTheOldOne() {
        val change = DoseChange(med, med.copy(dose = 55.0))
        assertTrue(change.dose)
        assertFalse(change.weight)
        val half = dose(0.5, 25.0)
        assertTrue(change.affects(half))
        assertEquals(27.5, change.fixed(half).amount, 1e-9)
        // Logged under an earlier prescription: left as it was.
        val earlier = dose(1.0, 25.0)
        assertFalse(change.affects(earlier))
        assertEquals(earlier, change.fixed(earlier))
        // Another unit isn't the old dose either.
        assertFalse(change.affects(dose(1.0, 50.0, unit = "mL")))
    }

    @Test
    fun aFixedWeightKeepsTheDoseAndTakesTheNewUnit() {
        val change = DoseChange(med, med.copy(weight = 400.0, weightUnit = "mg"))
        assertFalse(change.dose)
        assertTrue(change.weight)
        val fixed = change.fixed(dose(2.0, 100.0))
        assertEquals(800.0, fixed.weight!!, 1e-9)
        assertEquals("mg", fixed.weightUnit)
        assertEquals(100.0, fixed.amount, 1e-9)
    }

    @Test
    fun aFirstWeightReachesDosesLoggedWithoutOne() {
        val before = med.copy(weight = null)
        val change = DoseChange(before, med)
        val old = dose(2.0, 100.0, weight = null, weightUnit = null)
        assertTrue(change.affects(old))
        assertEquals(0.7, change.fixed(old).weight!!, 1e-9)
        assertEquals("g", change.fixed(old).weightUnit)
        // A unit picked with no weight given changes nothing.
        assertFalse(DoseChange(before, before.copy(weightUnit = "mg")).weight)
    }

    @Test
    fun theQuestionSaysWhatTheDosesWereLoggedAt() {
        assertEquals(
            "1,200 doses were logged at 50 mg. Fix them too if it was entered wrong; keep them if the prescription changed.",
            Drafts.pastDosesQuestion(DoseChange(med, med.copy(dose = 55.0)), 1200, med),
        )
        assertEquals(
            "1 dose was logged at 0.35 g per tablet. Fix it too if it was entered wrong; keep it if the pill changed.",
            Drafts.pastDosesQuestion(DoseChange(med, med.copy(weight = 0.4)), 1, med),
        )
        val unweighed = med.copy(weight = null)
        assertEquals(
            "3 doses were logged before the pill had a weight. Fix them too to count their weight, or count weight only from now on.",
            Drafts.pastDosesQuestion(DoseChange(unweighed, med), 3, unweighed),
        )
    }
}
