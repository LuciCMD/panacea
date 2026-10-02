package com.clementine.panacea.ui.settings

import com.clementine.panacea.data.backup.Written
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import com.clementine.panacea.ui.today.Fixtures.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BackupTextTest {
    @Test
    fun theSettingsTabCallsOutAnOverdueBackupOnly() {
        assertFalse(BackupText.due(ms(9, 3, 9, 0), ms(1, 1, 9, 0), now))
        assertTrue(BackupText.due(ms(9, 2, 9, 0), ms(1, 1, 9, 0), now))
        assertFalse(BackupText.due(null, ms(9, 20, 9, 0), now))
        assertTrue(BackupText.due(null, ms(8, 1, 9, 0), now))
        assertFalse(BackupText.due(null, null, now))
    }

    @Test
    fun saysHowOldTheLastBackupIs() {
        assertEquals(BackupText.Age("Not backed up yet", warn = true), BackupText.age(null, now, formats))
        assertEquals(BackupText.Age("Last backed up today at 9:12", warn = false), BackupText.age(ms(10, 2, 9, 12), now, formats))
        assertEquals(BackupText.Age("Last backed up yesterday at 21:05", warn = false), BackupText.age(ms(10, 1, 21, 5), now, formats))
        assertEquals(BackupText.Age("Last backed up 3 Sep · 29 days ago", warn = false), BackupText.age(ms(9, 3, 9, 0), now, formats))
        assertEquals(BackupText.Age("Last backed up 2 Sep · 30 days ago", warn = true), BackupText.age(ms(9, 2, 9, 0), now, formats))
    }

    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    @Test
    fun replacingSaysWhatGoesAndWhatComes() {
        val q = BackupText.question(
            medications = 5, doses = 340, reminders = 3, legacy = false, exportedAt = ms(10, 2, 9, 0),
            currentMedications = 4, currentDoses = 12, missingPhotos = 0, problems = 0, settings = true, zone = zone, date = date,
        )
        assertEquals("Replace Everything?", q.title)
        assertEquals(
            "This backup from 2 October 2026 has 5 medications, 340 doses and 3 reminders. Restoring it replaces the " +
                "4 medications and 12 doses on this phone, with their reminders and photos. The theme and sounds come from the backup too.",
            q.text,
        )
        assertEquals("Replace", q.confirm)
        assertTrue(q.destructive)
    }

    @Test
    fun anEmptyPhoneIsJustRestored() {
        val q = BackupText.question(
            medications = 1, doses = 1, reminders = 0, legacy = true, exportedAt = null,
            currentMedications = 0, currentDoses = 0, missingPhotos = 2, problems = 1, settings = false, zone = zone, date = date,
        )
        assertEquals("Restore This Backup?", q.title)
        assertEquals(
            "This Panacea 3 backup has 1 medication and 1 dose. 2 photos aren't in this file, so those pills will show " +
                "without one. 1 item couldn't be read and will be left out.",
            q.text,
        )
        assertFalse(q.destructive)
    }

    @Test
    fun resultsInWords() {
        assertEquals("Saved 5 medications, 340 doses, 8 photos and 1 sound.", BackupText.backedUp(Written(5, 340, 8, 1)))
        assertEquals("Saved 1 medication and 0 doses.", BackupText.backedUp(Written(1, 0, 0, 0)))
        assertEquals("Exported 340 doses.", BackupText.exported(Written(5, 340, 0, 0)))
        assertEquals("Restored 5 medications and 340 doses. 2 items couldn't be read and were left out.", BackupText.restored(5, 340, 2))
        assertEquals("Panacea Backup 2026-10-02.zip", BackupText.backupName(LocalDate.of(2026, 10, 2)))
    }
}
