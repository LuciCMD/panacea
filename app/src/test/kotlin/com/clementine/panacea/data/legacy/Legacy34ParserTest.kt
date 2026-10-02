package com.clementine.panacea.data.legacy

import com.clementine.panacea.model.RepeatType
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Legacy34ParserTest {

    private fun meds(json: String) = Legacy34Parser.parsePrefs(json, null, null, null, null)
    private fun reminders(json: String) = Legacy34Parser.parsePrefs(null, null, json, null, null).reminders

    @Test
    fun readsA34Medication() {
        val snapshot = meds(
            """{"Oxycodone":{"name":"Oxycodone","dosage":5,"unit":"mg","category":"Prescribed","type":"ORAL_TABLET",
            "ingredients":[{"name":"Acetaminophen","dosage":325,"unit":"mg"}],
            "usageHistory":[{"timeTaken":2000,"dosageTaken":2.5,"fraction":0.5},{"timeTaken":1000,"dosageTaken":5,"fraction":1}]}}"""
        )
        val med = snapshot.medications.single()
        assertEquals("Oxycodone", med.name)
        assertEquals(5.0, med.dose, 0.0)
        assertEquals("Prescribed", med.category)
        assertEquals("ORAL_TABLET", med.type)
        assertEquals(listOf(LegacyIngredient("Acetaminophen", 325.0, "mg")), med.ingredients)
        // Sorted oldest first
        assertEquals(listOf(1000L, 2000L), med.doses.map { it.takenAt })
        assertEquals(0.5, med.doses[1].fraction, 0.0)
        assertTrue(snapshot.problems.isEmpty())
    }

    @Test
    fun missingFieldsTakeThe34Defaults() {
        val med = meds("""{"A":{"name":"A","category":"","type":""}}""").medications.single()
        assertEquals(0.0, med.dose, 0.0)
        assertEquals("mg", med.unit)
        assertEquals("Uncategorized", med.category)
        assertEquals("UNSPECIFIED", med.type)
        assertTrue(med.ingredients.isEmpty())
        assertTrue(med.doses.isEmpty())
    }

    @Test
    fun legacySecondaryDrugBecomesAnIngredient() {
        val med = meds("""{"A":{"name":"A","dosage":10,"secondaryDrugName":" Caffeine ","secondaryDrugDosage":65}}""")
            .medications.single()
        assertEquals(listOf(LegacyIngredient("Caffeine", 65.0, "mg")), med.ingredients)
    }

    @Test
    fun ingredientsArrayWinsOverTheLegacyFieldsAndIsCapped() {
        val many = (1..12).joinToString(",") { """{"name":"I$it","dosage":$it}""" }
        val med = meds("""{"A":{"name":"A","secondaryDrugName":"Old","ingredients":[$many,{"name":"  "}]}}""")
            .medications.single()
        assertEquals(Legacy34Parser.MAX_INGREDIENTS, med.ingredients.size)
        assertEquals("I1", med.ingredients.first().name)
        assertEquals("mg", med.ingredients.first().unit)
    }

    @Test
    fun missingFractionIsWorkedOutFromTheDose() {
        val med = meds(
            """{"A":{"name":"A","dosage":20,"usageHistory":[{"timeTaken":1,"dosageTaken":10},{"timeTaken":2,"dosageTaken":20}]}}"""
        ).medications.single()
        assertEquals(listOf(0.5, 1.0), med.doses.map { it.fraction })
    }

    @Test
    fun missingFractionWithNoDoseCountsAsAFullDose() {
        val med = meds("""{"A":{"name":"A","usageHistory":[{"timeTaken":1,"dosageTaken":3}]}}""").medications.single()
        assertEquals(1.0, med.doses.single().fraction, 0.0)
    }

    @Test
    fun oneBadDoseRecordDoesNotLoseTheRest() {
        val snapshot = meds(
            """{"A":{"name":"A","dosage":1,"usageHistory":[{"timeTaken":1,"dosageTaken":1},{"dosageTaken":1},{"timeTaken":3,"dosageTaken":1}]}}"""
        )
        assertEquals(2, snapshot.medications.single().doses.size)
        assertEquals(1, snapshot.problems.size)
    }

    @Test
    fun oneBadMedicationDoesNotLoseTheOthers() {
        val snapshot = meds("""{"Bad":"oops","NoName":{"dosage":1},"Good":{"name":"Good"}}""")
        assertEquals(listOf("Good"), snapshot.medications.map { it.name })
        assertEquals(2, snapshot.problems.size)
    }

    @Test
    fun unreadableMedicationListIsReportedNotThrown() {
        val snapshot = meds("{not json")
        assertTrue(snapshot.medications.isEmpty())
        assertEquals(1, snapshot.problems.size)
    }

    @Test
    fun readsA34Reminder() {
        val r = reminders(
            """[{"medicationName":"A","repeatType":"WEEKLY","enabled":false,"daysMask":62,"intervalHours":4,
            "startHour":8,"startMinute":0,"endHour":22,"endMinute":30,"dayOfMonth":1,"label":"With food","id":-1268404526,
            "timesCompleted":9,"timesMissed":1,"pending":true,"lastFiredAt":5,"lastCompletedAt":4,
            "times":[{"h":8,"m":30},{"h":21,"m":0}]}]"""
        ).single()
        assertEquals(-1268404526, r.id)
        assertEquals(RepeatType.WEEKLY, r.repeat)
        assertEquals(false, r.enabled)
        assertEquals(62, r.daysMask)
        assertEquals(listOf(510, 1260), r.times)
        assertEquals(8 * 60, r.windowStart)
        assertEquals(22 * 60 + 30, r.windowEnd)
        assertEquals("With food", r.note)
        assertEquals(9, r.timesCompleted)
        assertEquals(true, r.pending)
    }

    @Test
    fun pre32ReminderBecomesDailyWithOneTime() {
        val r = reminders("""[{"medicationName":"A","hour":21,"minute":15}]""").single()
        assertEquals(RepeatType.DAILY, r.repeat)
        assertEquals(listOf(21 * 60 + 15), r.times)
        assertEquals(0x7F, r.daysMask)
        assertNull(r.id)
    }

    @Test
    fun reminderEdgeValuesAreNormalised() {
        val many = (0 until 20).joinToString(",") { """{"h":$it,"m":0}""" }
        val r = reminders(
            """[{"medicationName":"A","repeatType":"YEARLY","daysMask":0,"intervalHours":0,"dayOfMonth":40,"times":[$many]}]"""
        ).single()
        assertEquals(RepeatType.DAILY, r.repeat)
        assertEquals(0x7F, r.daysMask)
        assertEquals(1, r.intervalHours)
        assertEquals(31, r.dayOfMonth)
        assertEquals(12, r.times.size)
    }

    @Test
    fun reminderWithNoTimesGets8am() {
        assertEquals(listOf(480), reminders("""[{"medicationName":"A","times":[]}]""").single().times)
    }

    @Test
    fun readsPresetsOrderAndLastAmounts() {
        val snapshot = Legacy34Parser.parsePrefs(
            null, """["B","A"]""", null, "[0.25,0.5,1,2]", """{"A":0.5,"B":"x","C":-1}""",
        )
        assertEquals(listOf("B", "A"), snapshot.order)
        assertEquals(listOf(0.25, 0.5, 1.0, 2.0), snapshot.amountPresets)
        assertEquals(mapOf("A" to 0.5), snapshot.lastAmounts)
    }

    @Test
    fun presetsNeverSavedStayNull() {
        assertNull(Legacy34Parser.parsePrefs(null, null, null, null, null).amountPresets)
    }

    @Test
    fun readsABackupFile() {
        val snapshot = Legacy34Parser.parseBackup(
            """{"app":"Panacea","backupVersion":2,"medications":[{"name":"A","dosage":1}],
            "order":["A"],"reminders":[{"medicationName":"A","times":[{"h":9,"m":0}]}]}"""
        )
        assertEquals(listOf("A"), snapshot.medications.map { it.name })
        assertEquals(listOf("A"), snapshot.order)
        assertEquals(listOf(540), snapshot.reminders.single().times)
    }

    @Test(expected = JSONException::class)
    fun aFileThatIsNotABackupIsRefused() {
        Legacy34Parser.parseBackup("""{"something":"else"}""")
    }
}
