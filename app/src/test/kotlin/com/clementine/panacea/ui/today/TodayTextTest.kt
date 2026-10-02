package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayTextTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val now = ZonedDateTime.of(2026, 10, 2, 13, 16, 0, 0, zone)
    private val time = DateTimeFormatter.ofPattern("H:mm")
    private val date = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun lastTakenWordsDependOnTheDay() {
        assertEquals("Not taken yet", TodayText.lastTaken(null, now, time, date))
        assertEquals("Taken at 9:04", TodayText.lastTaken(at(2, 9, 4), now, time, date))
        assertEquals("Last taken yesterday at 22:10", TodayText.lastTaken(at(1, 22, 10), now, time, date))
        assertEquals("Last taken 28 Sep at 8:00", TodayText.lastTaken(at(1, 8, 0) - 3 * 86_400_000L, now, time, date))
    }

    @Test
    fun doseLineShowsWeightOnlyWhenKnown() {
        val med = MedicationEntity(name = "A", dose = 50.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 0)
        assertEquals("50 mg", TodayText.doseLine(med))
        assertEquals("50 mg · weighs 0.2 g", TodayText.doseLine(med.copy(weight = 0.20)))
        assertEquals("50 mg · weighs 0.01 oz", TodayText.doseLine(med.copy(weight = 0.01, weightUnit = "oz")))
    }
}
