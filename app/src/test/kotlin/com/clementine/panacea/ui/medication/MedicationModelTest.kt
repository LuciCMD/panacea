package com.clementine.panacea.ui.medication

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationModelTest {
    private val med = MedicationEntity(
        id = 1, name = "Ibuprofen", dose = 200.0, doseUnit = "mg", weight = 350.0, weightUnit = "mg",
        category = "OTC", type = "ORAL_TABLET", sortOrder = 0,
    )

    private fun dose(at: Long, multiplier: Double = 1.0, amount: Double = 200.0 * multiplier, unit: String = "mg", weight: Double? = null, weightUnit: String? = null) =
        DoseEntity(medicationId = 1, takenAt = at, multiplier = multiplier, amount = amount, unit = unit, weight = weight, weightUnit = weightUnit)

    @Test
    fun factsSayWhatOnePillHolds() {
        val ui = MedicationModel.build(
            med, listOf(IngredientEntity(medicationId = 1, position = 0, name = "Caffeine", amount = 0.0, unit = "mg")),
            emptyList(), emptyList(), now, formats,
        )
        assertEquals("OTC · Tablet", ui.subtitle)
        assertEquals(listOf(Fact("Dose", "200 mg per tablet"), Fact("Pill Weight", "350 mg per tablet")), ui.facts)
        assertEquals(listOf(Fact("Caffeine", "")), ui.ingredients)
        assertNull(ui.totals)

        val bare = MedicationModel.build(med.copy(dose = 0.0, weight = null, category = "Uncategorized"), emptyList(), emptyList(), emptyList(), now, formats)
        assertEquals("Tablet", bare.subtitle)
        assertEquals(listOf("Not set", "Not set"), bare.facts.map { it.value })
    }

    @Test
    fun totalsCountEachPeriod() {
        val doses = listOf(
            dose(ms(10, 2, 9, 0)),
            dose(ms(10, 1, 22, 0), multiplier = 2.0),
            dose(ms(9, 28, 8, 0)),
            dose(ms(8, 1, 8, 0)),
        )
        val totals = MedicationModel.totals(med, doses, now)!!
        assertEquals(listOf("2", "3", "3", "4"), totals.rows.map { it.doses })
        assertEquals(listOf("600 mg", "800 mg", "800 mg", "1000 mg"), totals.rows.map { it.amount })
        assertEquals("Last 24 Hours: 2 doses, 600 mg, 1050 mg", totals.rows[0].spoken(totals.showAmount, totals.showWeight))
    }

    @Test
    fun weightIsAsLoggedInThePillsUnitOrElseFromThePillNow() {
        // One logged in grams before the unit changed, one from before any weight was set.
        val doses = listOf(dose(ms(10, 2, 9, 0), weight = 0.4, weightUnit = "g"), dose(ms(10, 2, 8, 0), multiplier = 0.5))
        assertEquals("575 mg", MedicationModel.totals(med, doses, now)!!.rows[0].weight)
        // With no weight anywhere, the column goes.
        assertFalse(MedicationModel.totals(med.copy(weight = null), listOf(dose(ms(10, 2, 9, 0))), now)!!.showWeight)
    }

    @Test
    fun aChangedUnitIsAddedNotMixed() {
        val doses = listOf(dose(ms(10, 2, 9, 0)), dose(ms(10, 2, 8, 0), amount = 5.0, unit = "mL"), dose(ms(10, 2, 7, 0), amount = 0.0))
        val row = MedicationModel.totals(med, doses, now)!!.rows[0]
        assertEquals("200 mg + 5 mL", row.amount)
        assertEquals("3", row.doses)
    }

    @Test
    fun emptyPeriodsShowADash() {
        val totals = MedicationModel.totals(med.copy(weight = null), listOf(dose(ms(8, 1, 8, 0))), now)!!
        assertEquals(TotalRow("Last 24 Hours", "0", TotalRow.NONE, TotalRow.NONE), totals.rows[0])
        assertEquals("Last 24 Hours: 0 doses", totals.rows[0].spoken(totals.showAmount, totals.showWeight))
    }
}
