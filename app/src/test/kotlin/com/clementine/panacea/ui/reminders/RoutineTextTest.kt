package com.clementine.panacea.ui.reminders

import com.clementine.panacea.reminder.Routine
import com.clementine.panacea.reminder.Slot
import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineTextTest {
    private val morning = Routine.TimesOfDay(listOf(Slot(9 * 60, 20)), doses = 14)

    @Test
    fun whatWasLearnedReadsPlainly() {
        assertEquals(
            "You usually take it around 9:00, give or take 20 minutes. If nothing is logged by 9:40, Panacea asks whether you took it.",
            RoutineText.summary(morning, on = true, nextAsk = at(10, 3, 9, 40), now, formats),
        )
        val twice = Routine.TimesOfDay(listOf(Slot(8 * 60 + 30, 10), Slot(20 * 60 + 30, 15)), doses = 20)
        assertEquals(
            "You usually take it around 8:30 and 20:30. If nothing is logged by 8:50 or 21:00, Panacea asks whether you took it.",
            RoutineText.summary(twice, on = true, nextAsk = null, now, formats),
        )
        val gap = Routine.SteadyGap(420, 10, ms(10, 2, 13, 0), ms(10, 2, 13, 0), doses = 25)
        assertEquals(
            "You usually take it about every 7 hours, give or take 10 minutes. If nothing is logged by 20:20, Panacea asks whether you took it.",
            RoutineText.summary(gap, on = true, nextAsk = at(10, 2, 20, 20), now, formats),
        )
        assertEquals(
            "You usually take it about every 7 hours, give or take 10 minutes. It asks again once the next dose is logged.",
            RoutineText.summary(gap, on = true, nextAsk = null, now, formats),
        )
        assertEquals("Times of day · learned from 14 doses", RoutineText.basis(morning))
    }

    @Test
    fun beforeAndWithoutAPattern() {
        assertEquals(
            "Still learning: 3 of 5 doses so far, on 2 days. It needs 5 doses over at least 3 days in the last 4 weeks.",
            RoutineText.summary(Routine.Learning(3, 2), on = true, nextAsk = null, now, formats),
        )
        assertEquals(
            "You usually take it around 9:00, give or take 20 minutes. Turn this on to be asked when a dose seems to be missing.",
            RoutineText.summary(morning, on = false, nextAsk = null, now, formats),
        )
        assertEquals("No steady pattern yet" to "Won't ask for now", RoutineText.card(Routine.Irregular(9), null, now, formats))
        assertEquals("Usually around 9:00" to "Asks at 9:40 tomorrow if nothing is logged", RoutineText.card(morning, at(10, 3, 9, 40), now, formats))
    }

    @Test
    fun gapsInWords() {
        assertEquals("about every hour", RoutineText.every(60))
        assertEquals("about every 4½ hours", RoutineText.every(270))
        assertEquals("about every 6 hours", RoutineText.every(365))
        assertEquals("about every 12 hours", RoutineText.every(725))
        assertEquals("about once a day", RoutineText.every(1440))
        assertEquals("about every 2 days", RoutineText.every(2880))
    }

    @Test
    fun theQuestionSaysWhenItWasLastLogged() {
        assertEquals("Did you take Sertraline?", RoutineText.question("Sertraline"))
        assertEquals(
            "You usually take it around 9:00. Last logged yesterday at 9:04.",
            RoutineText.notification(morning, ms(10, 1, 9, 4), now, formats),
        )
        assertEquals(
            "You usually take it around 9:00. Last logged Tuesday at 8:55.",
            RoutineText.notification(morning, ms(9, 29, 8, 55), now, formats),
        )
    }
}
