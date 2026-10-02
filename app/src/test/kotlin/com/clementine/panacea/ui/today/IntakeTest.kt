package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.TakenIngredient
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test

class IntakeTest {
    private val percocet = MedicationEntity(id = 1, name = "Percocet", dose = 5.0, doseUnit = "mg", category = "Prescribed", type = "ORAL_TABLET", sortOrder = 0)
    private val tylenol = MedicationEntity(id = 2, name = "Acetaminophen", dose = 500.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 1)
    private val drops = MedicationEntity(id = 3, name = "Drops", dose = 0.0, doseUnit = "mg", category = "OTC", type = "DROPS", sortOrder = 2)
    private val meds = listOf(percocet, tylenol, drops)
    private val apap = IngredientEntity(id = 1, medicationId = 1, position = 0, name = "acetaminophen ", amount = 325.0, unit = "mg")
    private val nowMs = now.toInstant().toEpochMilli()

    private fun dose(id: Long, med: MedicationEntity, at: Long, m: Double, with: List<IngredientEntity> = emptyList()) =
        DoseEntity(
            id = id, medicationId = med.id, takenAt = at, multiplier = m, amount = med.dose * m, unit = med.doseUnit,
            ingredients = TakenIngredient.of(with, m),
        )

    @Test
    fun anIngredientCountsAcrossEveryMedicationWithIt() {
        val doses = listOf(
            dose(1, percocet, ms(10, 2, 8, 0), 1.5, listOf(apap)),
            dose(2, tylenol, ms(10, 2, 10, 0), 2.0),
            // A day and more ago: not counted.
            dose(3, percocet, ms(10, 1, 13, 0), 1.0, listOf(apap)),
        )
        val (own, apapHad) = Intake.last24h(percocet, meds, listOf(apap), doses, nowMs)
        assertEquals(Had(null, 7.5, "mg", 5.0, listOf("Percocet")), own)
        // 325 × 1.5 from Percocet, and Tylenol's own 1,000 mg, matched by name whatever the case.
        assertEquals(1487.5, apapHad.total, 0.0)
        assertEquals(listOf("Percocet", "Acetaminophen"), apapHad.from)
        assertEquals("1,487.5 mg acetaminophen in the last 24 h, from Percocet and Acetaminophen", TodayText.had(apapHad))
        assertEquals("1,812.5 mg with this one", TodayText.withThisOne(apapHad, 1.0))
        // Tylenol's own line counts the acetaminophen in Percocet too.
        val (tylenolOwn) = Intake.last24h(tylenol, meds, listOf(apap), doses, nowMs)
        assertEquals(1487.5, tylenolOwn.total, 0.0)
        assertEquals("1,487.5 mg in the last 24 h, from Percocet and Acetaminophen", TodayText.had(tylenolOwn))
    }

    @Test
    fun aDoseCountsTheIngredientsItWasTakenWith() {
        // Logged at 325 mg; the tablet says 500 mg now. The past dose still counts 325.
        val doses = listOf(dose(1, percocet, ms(10, 2, 8, 0), 2.0, listOf(apap)))
        val (_, apapHad) = Intake.last24h(percocet, meds, listOf(apap.copy(amount = 500.0)), doses, nowMs)
        assertEquals(650.0, apapHad.total, 0.0)
        assertEquals(500.0, apapHad.perOne, 0.0)
    }

    @Test
    fun withoutADoseItCountsDoses() {
        val doses = listOf(dose(1, drops, ms(10, 2, 9, 0), 2.0), dose(2, drops, ms(10, 2, 12, 0), 1.0))
        val (own) = Intake.last24h(drops, meds, emptyList(), doses, nowMs)
        assertEquals("2 doses in the last 24 h", TodayText.had(own))
        assertEquals("3 with this one", TodayText.withThisOne(own, 4.0))
    }

    @Test
    fun nothingYetSaysSo() {
        val (own, apapHad) = Intake.last24h(percocet, meds, listOf(apap.copy(name = "Acetaminophen")), emptyList(), nowMs)
        assertEquals("Nothing in the last 24 h", TodayText.had(own))
        assertEquals("No Acetaminophen in the last 24 h", TodayText.had(apapHad))
        assertEquals("7.5 mg with this one", TodayText.withThisOne(own, 1.5))
    }
}
