package com.clementine.panacea.data.backup

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.data.db.TakenIngredient
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.sound.CustomSound
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.sound.SoundSetting
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val melatonin = MedicationEntity(
        id = 7, name = "Melatonin", dose = 3.0, doseUnit = "mg", weight = 0.25, weightUnit = "g", category = "OTC",
        type = "EDIBLE", sortOrder = 1, lastMultiplier = 1.5, learnRoutine = true, mutedUntil = 99, photoFront = "a.jpg",
        photoBack = null, routineAskedFor = 1234,
    )
    private val ibuprofen = MedicationEntity(id = 3, name = "Ibuprofen", dose = 200.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 0)

    private val data = BackupData(
        medications = listOf(melatonin, ibuprofen),
        ingredients = listOf(IngredientEntity(medicationId = 7, position = 0, name = "Vitamin B6", amount = 1.5, unit = "mg")),
        doses = listOf(
            // Logged when the tablet had 1 mg of B6; it has 1.5 mg now, and the dose keeps what it was.
            DoseEntity(
                medicationId = 7, takenAt = 1000, multiplier = 1.5, amount = 4.5, unit = "mg", weight = 0.375, weightUnit = "g",
                ingredients = listOf(TakenIngredient("Vitamin B6", 1.5, "mg")),
            ),
            DoseEntity(medicationId = 3, takenAt = 2000, multiplier = 2.0, amount = 400.0, unit = "mg"),
        ),
        reminders = listOf(
            ReminderEntity(
                id = -12, medicationId = 3, repeat = RepeatType.WEEKLY, enabled = false, daysMask = 0x22, times = listOf(540, 1260),
                intervalHours = 4, windowStart = 480, windowEnd = 1320, dayOfMonth = 15, note = "With food", earlyWindowMinutes = 30,
                timesCompleted = 5, timesMissed = 2, pending = true, lastFiredAt = 3000, lastCompletedAt = 2500,
            ),
        ),
        amountPresets = listOf(0.5, 1.0, 1.5),
        settings = BackupSettings("ocean", mapOf("take" to SoundSetting(SoundMode.CUSTOM, CustomSound("take-1.mp3", "Chime.mp3")))),
        exportedAt = null,
        legacy = false,
    )

    @Test
    fun everyFieldComesBack() {
        val back = BackupFormat.read(BackupFormat.write(data, "4.0", now = 5000))
        // In list order, with fresh ids; everything else as it was.
        assertEquals(listOf(ibuprofen.copy(id = 1, sortOrder = 0), melatonin.copy(id = 2, sortOrder = 1)), back.medications)
        assertEquals(listOf(data.ingredients[0].copy(medicationId = 2)), back.ingredients)
        assertEquals(setOf(data.doses[0].copy(medicationId = 2), data.doses[1].copy(medicationId = 1)), back.doses.toSet())
        assertEquals(listOf(data.reminders[0].copy(medicationId = 1)), back.reminders)
        assertEquals(data.amountPresets, back.amountPresets)
        assertEquals(data.settings, back.settings)
        assertEquals(5000L, back.exportedAt)
        assertFalse(back.legacy)
        assertTrue(back.problems.isEmpty())
        assertEquals(setOf("a.jpg"), back.photos)
        assertEquals(setOf("take-1.mp3"), back.sounds)
    }

    @Test
    fun aVersion3BackupsDosesTakeTheMedicationsIngredients() {
        val root = JSONObject(BackupFormat.write(data, "4.0", now = 5000)).put("backupVersion", 3)
        val meds = root.getJSONArray("medications")
        for (i in 0 until meds.length()) {
            val doses = meds.getJSONObject(i).getJSONArray("doses")
            for (j in 0 until doses.length()) doses.getJSONObject(j).remove("ingredients")
        }
        val back = BackupFormat.read(root.toString())
        assertEquals(listOf(TakenIngredient("Vitamin B6", 2.25, "mg")), back.doses.single { it.medicationId == 2L }.ingredients)
        assertEquals(emptyList<TakenIngredient>(), back.doses.single { it.medicationId == 1L }.ingredients)
    }

    @Test
    fun saysWhatItIs() {
        val root = JSONObject(BackupFormat.write(data, "4.0", now = 5000))
        assertEquals("Panacea", root.getString("app"))
        assertEquals(4, root.getInt("backupVersion"))
        assertEquals("4.0", root.getString("appVersion"))
    }

    @Test
    fun readsA34Backup() {
        // 3.4 wrote version 2; it must never be read as the new format.
        val back = BackupFormat.read(
            """{"app":"Panacea","backupVersion":2,"exportedAt":777,"medications":[{"name":"A","dosage":5,"unit":"mg"}],
            "order":["A"],"reminders":[{"medicationName":"A","times":[{"h":9,"m":0}]}]}"""
        )
        assertTrue(back.legacy)
        assertNull(back.settings)
        assertEquals(listOf("A"), back.medications.map { it.name })
        assertEquals(listOf(540), back.reminders.single().times)
        assertEquals(777L, back.exportedAt)
    }

    @Test
    fun refusesWhatIsNotABackup() {
        for (json in listOf("not json", """{"something":"else"}""", """{"backupVersion":3}""")) {
            try {
                BackupFormat.read(json)
                throw AssertionError("read $json")
            } catch (e: BackupException) {
                assertEquals("That file isn't a Panacea backup.", e.message)
            }
        }
        try {
            BackupFormat.read("""{"backupVersion":5,"medications":[]}""")
            throw AssertionError("read a newer backup")
        } catch (e: BackupException) {
            assertTrue(e.message!!.startsWith("This backup was made by a newer Panacea."))
        }
    }

    @Test
    fun badItemsAreSkippedAndNamed() {
        val back = BackupFormat.read(
            """{"backupVersion":3,"medications":[
              {"name":"A","dose":1,"photoFront":"../../databases/panacea.db",
               "doses":[{"takenAt":10,"multiplier":1},{"takenAt":"soon"}],"reminders":[{"repeat":"YEARLY"}]},
              {"name":"A"},
              {"dose":2}
            ]}"""
        )
        assertEquals(listOf("A"), back.medications.map { it.name })
        assertNull(back.medications[0].photoFront)
        assertEquals(1, back.doses.size)
        assertTrue(back.reminders.isEmpty())
        assertEquals(
            listOf(
                "1 dose of \"A\" could not be read.",
                "A reminder for \"A\" could not be read.",
                "A second medication named \"A\" was skipped.",
                "Medication 3 in the backup could not be read.",
            ),
            back.problems,
        )
    }

    @Test
    fun restoringShowsNothingAndAsksNothingPast() {
        val ready = BackupFormat.forRestore(data, now = 9000)
        assertFalse(ready.reminders.single().pending)
        assertEquals(9000L, ready.medications[0].routineAskedFor)
        assertEquals(0L, ready.medications[1].routineAskedFor)
    }

    @Test
    fun onlyPlainFileNamesAreSafe() {
        assertTrue(BackupFormat.isSafeName("3f2a-11.jpg"))
        assertTrue(BackupFormat.isSafeName("take-1727.mp3"))
        assertFalse(BackupFormat.isSafeName("../x.jpg"))
        assertFalse(BackupFormat.isSafeName("a/b.jpg"))
        assertFalse(BackupFormat.isSafeName(".hidden"))
        assertFalse(BackupFormat.isSafeName(""))
    }
}
