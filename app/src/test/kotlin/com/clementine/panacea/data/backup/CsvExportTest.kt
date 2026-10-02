package com.clementine.panacea.data.backup

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.TakenIngredient
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.zone
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {
    @Test
    fun oneRowPerDoseOldestFirstWithARunningDayTotal() {
        val meds = listOf(
            MedicationEntity(id = 1, name = "Excedrin, Extra", dose = 250.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 0),
            MedicationEntity(id = 2, name = "Melatonin", dose = 3.0, doseUnit = "mg", category = "OTC", type = "EDIBLE", sortOrder = 1),
        )
        // Each dose's own ingredients, as logged: the medication's may have changed since.
        val caffeine = listOf(IngredientEntity(medicationId = 1, position = 0, name = "Caffeine", amount = 65.0, unit = "mg"))
        val doses = listOf(
            DoseEntity(medicationId = 1, takenAt = ms(10, 2, 14, 0), multiplier = 1.0, amount = 250.0, unit = "mg", ingredients = TakenIngredient.of(caffeine, 1.0)),
            DoseEntity(medicationId = 2, takenAt = ms(10, 1, 23, 30), multiplier = 1.0, amount = 3.0, unit = "mg", weight = 0.25, weightUnit = "g"),
            DoseEntity(medicationId = 1, takenAt = ms(10, 2, 9, 5), multiplier = 2.0, amount = 500.0, unit = "mg", ingredients = TakenIngredient.of(caffeine, 2.0)),
        )
        val lines = CsvExport.write(meds, doses, zone).split("\r\n")
        assertEquals("﻿Date,Time,Medication,Amount,Unit,Multiplier,Weight,Weight Unit,Ingredients,Day Total", lines[0])
        assertEquals("2026-10-01,23:30,Melatonin,3,mg,1,0.25,g,,3 mg", lines[1])
        assertEquals("2026-10-02,09:05,\"Excedrin, Extra\",500,mg,2,,,Caffeine 130 mg,500 mg", lines[2])
        assertEquals("2026-10-02,14:00,\"Excedrin, Extra\",250,mg,1,,,Caffeine 65 mg,750 mg", lines[3])
        assertEquals("", lines[4])
    }

    @Test
    fun cellsAreQuotedAndFormulasDefused() {
        assertEquals("plain", CsvExport.cell("plain"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", CsvExport.cell("=HYPERLINK(1)"))
        assertEquals("-2", CsvExport.cell("-2"))
    }
}
