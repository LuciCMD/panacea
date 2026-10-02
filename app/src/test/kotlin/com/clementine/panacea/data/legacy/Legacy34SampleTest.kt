package com.clementine.panacea.data.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** A full set of 3.4 SharedPreferences files, read the way Android stores them, through parse and map. */
class Legacy34SampleTest {

    private fun pref(file: String, key: String): String? {
        val stream = javaClass.getResourceAsStream("/legacy34/$file.xml") ?: return null
        val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val strings = doc.getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == key }
            ?.textContent
    }

    @Test
    fun importsTheWholeSample() {
        val snapshot = Legacy34Parser.parsePrefs(
            medicationData = pref("MedTracker", "medication_data"),
            order = pref("MedTrackerOrder", "medication_order"),
            reminders = pref("ReminderPrefs", "medication_reminders"),
            amountPresets = pref("AmountPrefs", "presets"),
            lastAmounts = pref("AmountPrefs", "last_amounts"),
        )
        val rows = Legacy34Mapper.map(snapshot)

        assertTrue(rows.problems.toString(), rows.problems.isEmpty())
        assertEquals(
            listOf("Sertraline", "Multivitamin", "Ibuprofen", "Albuterol", "Melatonin", "Omeprazole"),
            rows.medications.map { it.name },
        )
        assertEquals(9 + 5 + 2 + 1 + 7 + 7, rows.doses.size)
        assertEquals(3, rows.ingredients.size)
        assertEquals(listOf(1001L, 1002L, 1003L), rows.reminders.map { it.id })
        assertEquals(listOf(0.25, 0.5, 1.0, 2.0), rows.amountPresets)

        val ibuprofen = rows.medications.single { it.name == "Ibuprofen" }
        assertEquals(2.0, ibuprofen.lastMultiplier, 0.0)
        assertEquals(listOf(400.0), rows.doses.filter { it.medicationId == ibuprofen.id }.map { it.amount }.distinct())
    }
}
