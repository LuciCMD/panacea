package com.clementine.panacea.reminder

import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTest {
    private val minute = 60_000L

    /** One dose a day at [hour]:[minute] plus the day's jitter, for each day of September from [from] to [to]. */
    private fun daily(from: Int, to: Int, hour: Int, minute: Int = 0, jitter: List<Int> = listOf(0)): List<Long> =
        (from..to).map { d -> ms(9, d, hour, minute) + jitter[d % jitter.size] * this.minute }

    @Test
    fun noGuessWithoutEnoughHistory() {
        val four = daily(25, 28, 9)
        assertEquals(Routine.Learning(4, 4), Routines.learn(four, now))
        // Five doses, but only two days.
        val twoDays = listOf(ms(10, 1, 8, 0), ms(10, 1, 14, 0), ms(10, 1, 20, 0), ms(10, 2, 8, 0), ms(10, 2, 12, 0))
        assertEquals(Routine.Learning(5, 2), Routines.learn(twoDays, now))
        // A second pill ten minutes later is the same sitting.
        val doubled = daily(27, 30, 9) + ms(9, 30, 9, 10)
        assertEquals(4, Routines.learn(doubled, now).doses)
    }

    @Test
    fun aMorningDoseIsLearnedAsATimeOfDay() {
        val doses = daily(18, 30, 9, 0, jitter = listOf(-15, 5, 20, -10, 0, 10, -5))
        val routine = Routines.learn(doses, now) as Routine.TimesOfDay
        assertEquals(1, routine.slots.size)
        val slot = routine.slots.single()
        assertTrue("centre ${slot.minute}", slot.minute in 9 * 60 - 5..9 * 60 + 5)
        assertTrue("spread ${slot.spread}", slot.spread in 10..15)

        // Today's 9:00 isn't logged; it asked after the margin.
        val ask = Routines.latestAsk(routine, now)!!
        assertEquals(ms(10, 2, 9, 0) + (slot.minute - 540) * minute, ask.expected)
        assertEquals(ask.expected + Routines.margin(slot.spread) * minute, ask.ask)
        // A dose up to three hours early answers it.
        assertTrue(ask.coveredBy(listOf(ms(10, 2, 6, 30)), now.toInstant().toEpochMilli()))
        assertFalse(ask.coveredBy(listOf(ms(10, 1, 21, 0)), now.toInstant().toEpochMilli()))
        // Next: tomorrow morning.
        assertEquals(ask.ask + 24 * 60 * minute, Routines.nextAsk(routine, now)!!.ask)
    }

    @Test
    fun twiceADayGivesTwoTimes() {
        val doses = daily(20, 30, 8, 30) + daily(20, 30, 20, 30, jitter = listOf(0, 15, -15))
        val routine = Routines.learn(doses, now) as Routine.TimesOfDay
        assertEquals(listOf(8 * 60 + 30, 20 * 60 + 30), routine.slots.map { it.minute })
        // The evening dose can't count for the morning: halfway back is 14:30, but three hours is the limit.
        val morning = Routines.latestAsk(routine, at(10, 2, 9, 0))!!
        assertEquals(ms(10, 2, 5, 30), morning.coverFrom)
    }

    @Test
    fun everySixHoursWithASleepGivesThreeTimesAcrossMidnight() {
        // 14:00, 20:00 and 02:00 from mid-September, today's 02:00 included; asleep from 2 till 14.
        val jitter = listOf(-20, 10, 0, 25, -10, 5, -5)
        val doses = (18..30).flatMap { d ->
            listOf(ms(9, d, 14, 0), ms(9, d, 20, 0), at(9, d, 2, 0).plusDays(1).toInstant().toEpochMilli())
                .mapIndexed { i, t -> t + jitter[(d + i) % jitter.size] * minute }
        } + listOf(ms(10, 1, 14, 0), ms(10, 1, 20, 0), ms(10, 2, 2, 0))
        val routine = Routines.learn(doses, now) as Routine.TimesOfDay
        assertEquals(listOf(2 * 60, 14 * 60, 20 * 60), routine.slots.map { it.minute })

        // Through the next day it asks after each of the three, the 2:00 one included.
        val asks = generateSequence(Routines.nextAsk(routine, now)) { Routines.nextAsk(routine, Instant.ofEpochMilli(it.ask).atZone(now.zone)) }
            .take(4).map { it.expected }.toList()
        assertEquals(listOf(ms(10, 2, 14, 0), ms(10, 2, 20, 0), ms(10, 3, 2, 0), ms(10, 3, 14, 0)), asks)

        // A 2:00 dose not logged is still asked about in the small hours.
        val night = Routines.latestAsk(routine, at(10, 3, 3, 0))!!
        assertEquals(ms(10, 3, 2, 0), night.expected)
        assertTrue(Routines.stillWorthAsking(night, at(10, 3, 3, 0)))
        // The 20:00 dose taken at 19:30 answers the 20:00 question, not the 14:00 one.
        val evening = Routines.latestAsk(routine, at(10, 2, 21, 0))!!
        assertTrue(evening.coveredBy(listOf(ms(10, 2, 19, 30)), ms(10, 2, 21, 0)))
        assertFalse(Routines.latestAsk(routine, at(10, 2, 15, 0))!!.coveredBy(listOf(ms(10, 2, 19, 30)), ms(10, 2, 15, 0)))
    }

    @Test
    fun everySevenHoursRoundTheClockIsASteadyGap() {
        // Seven hours apart, so the clock times walk all the way round the day.
        val start = ms(9, 25, 13, 0)
        val doses = (0 until 25).map { start + it * 7 * 60 * minute }.filter { it <= now.toInstant().toEpochMilli() }
        val routine = Routines.learn(doses, now) as Routine.SteadyGap
        assertEquals(420, routine.gapMinutes)
        assertEquals(10, routine.spread)
        val ask = Routines.nextAsk(routine, now)!!
        assertEquals(doses.last() + (420 + 20) * minute, ask.ask)
        assertEquals(doses.last() + 1, ask.coverFrom)
    }

    @Test
    fun noPatternMeansNoQuestions() {
        val doses = listOf(
            ms(9, 20, 8, 0), ms(9, 21, 15, 0), ms(9, 23, 2, 0), ms(9, 23, 19, 0),
            ms(9, 25, 11, 0), ms(9, 28, 22, 0), ms(9, 30, 5, 0),
        )
        val routine = Routines.learn(doses, now)
        assertEquals(Routine.Irregular(7), routine)
        assertNull(Routines.nextAsk(routine, now))
    }

    @Test
    fun aChangedRoutineIsPickedUp() {
        // Mornings until mid-September, evenings since.
        val doses = daily(5, 20, 9) + daily(21, 30, 21) + listOf(ms(10, 1, 21, 0))
        val routine = Routines.learn(doses, now) as Routine.TimesOfDay
        assertEquals(listOf(21 * 60), routine.slots.map { it.minute })
    }

    @Test
    fun oldQuestionsAreLeftAlone() {
        val doses = daily(18, 30, 9)
        val routine = Routines.learn(doses, now)
        val ask = Routines.latestAsk(routine, now)!!
        assertTrue(Routines.stillWorthAsking(ask, now))
        assertFalse(Routines.stillWorthAsking(ask, at(10, 2, 18, 0)))
        // Not yesterday's at midnight.
        assertFalse(Routines.stillWorthAsking(Routines.latestAsk(routine, at(10, 2, 23, 59))!!, at(10, 3, 0, 1)))
    }
}
