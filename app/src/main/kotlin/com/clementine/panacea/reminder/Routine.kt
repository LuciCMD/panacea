package com.clementine.panacea.reminder

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A usual time of day, in minutes after midnight, and how far doses usually stray from it. */
data class Slot(val minute: Int, val spread: Int)

/** What a medication's dose history says about when it's taken. */
sealed interface Routine {
    /** Doses learned from: those within [Routines.WINDOW], with doses minutes apart counted once. */
    val doses: Int

    /** Not enough history yet. */
    data class Learning(override val doses: Int, val days: Int) : Routine

    /** Enough history, but no pattern steady enough to go by. */
    data class Irregular(override val doses: Int) : Routine

    /** Taken at the same times each day, such as around 9:00 and 21:00. */
    data class TimesOfDay(val slots: List<Slot>, override val doses: Int) : Routine

    /** Taken a steady time apart, such as about every 6 hours, whatever the clock says. */
    data class SteadyGap(
        val gapMinutes: Int,
        val spread: Int,
        /** Start of the last dose, which the gap counts from. */
        val lastStart: Long,
        /** The very last dose logged; any later one is new. */
        val lastLogged: Long,
        override val doses: Int,
    ) : Routine
}

/**
 * A question the routine raises: the dose expected at [expected] isn't logged by [ask]. A dose
 * logged from [coverFrom] on answers it.
 */
data class Ask(val expected: Long, val ask: Long, val coverFrom: Long) {
    fun coveredBy(doseTimes: List<Long>, now: Long) = doseTimes.any { it in coverFrom..now }
}

/**
 * Learns when a medication is taken from its doses. Two models are tried and the steadier one wins:
 * times of day (doses clustered on a 24-hour clock) and a steady gap (the typical time between doses).
 * Recent doses weigh more, so a changed routine is picked up within a week or two.
 */
object Routines {
    const val MIN_DOSES = 5
    const val MIN_DAYS = 3
    val WINDOW: Duration = Duration.ofDays(28)

    /** A question older than this, or from an earlier day, is no longer worth asking. */
    val ASK_WITHIN: Duration = Duration.ofHours(6)

    /** A dose a week old counts half as much as one today. */
    private const val HALF_LIFE_DAYS = 7.0

    /** Doses this close together are one sitting (a second pill, or a slip logged twice). */
    private const val SAME_SITTING_MIN = 30

    /** Doses closer than this on the clock belong to the same usual time. */
    private const val JOIN_MIN = 90
    private const val MAX_SPREAD_MIN = 90

    /** A usual time must be kept on at least this share of days, recent days weighing more. */
    private const val MIN_KEPT = 0.5

    /** One stray dose shouldn't outweigh the rest, so no error counts as more than this. */
    private const val ERROR_CAP_MIN = 240

    /** Fit is judged on doses from this many days back (or the last few, if fewer). */
    private const val FIT_DAYS = 7L

    /** Above this average error neither model is trusted. */
    private const val MAX_ERROR_MIN = 60

    /** Times of day reads more naturally, so it wins unless the gap fits clearly better. */
    private const val PREFER_TIMES_MIN = 5

    private const val MIN_SPREAD_MIN = 10
    private const val MIN_MARGIN_MIN = 20
    private const val MAX_MARGIN_MIN = 120
    private const val MAX_COVER_MIN = 180
    private const val MIN_GAP_MIN = 60
    private const val MAX_GAP_MIN = 3 * 24 * 60
    private const val DAY_MIN = 24 * 60
    private const val MINUTE_MS = 60_000L

    private class Sitting(val start: Long, val minute: Int, val date: LocalDate, val weight: Double)

    fun learn(doseTimes: List<Long>, now: ZonedDateTime): Routine {
        val nowMs = now.toInstant().toEpochMilli()
        val from = nowMs - WINDOW.toMillis()
        val times = doseTimes.filter { it in from..nowMs }.sorted()
        val sittings = sittings(times, now)
        val days = sittings.map { it.date }.distinct().size
        if (sittings.size < MIN_DOSES || days < MIN_DAYS) return Routine.Learning(sittings.size, days)

        val fitFrom = minOf(
            now.toLocalDate().minusDays(FIT_DAYS).atStartOfDay(now.zone).toInstant().toEpochMilli(),
            sittings[sittings.size - MIN_DOSES].start,
        )
        val clock = timesOfDay(sittings, fitFrom, now)
        val gap = steadyGap(sittings, fitFrom, times.last())
        val best = when {
            clock == null -> gap?.first
            gap == null -> clock.first
            clock.second <= gap.second + PREFER_TIMES_MIN -> clock.first
            else -> gap.first
        }
        val error = listOfNotNull(clock, gap).firstOrNull { it.first == best }?.second ?: Double.MAX_VALUE
        return if (best != null && error <= MAX_ERROR_MIN) best else Routine.Irregular(sittings.size)
    }

    private fun sittings(times: List<Long>, now: ZonedDateTime): List<Sitting> {
        val out = ArrayList<Sitting>()
        var start = Long.MIN_VALUE
        for (t in times) {
            if (start != Long.MIN_VALUE && t - start <= SAME_SITTING_MIN * MINUTE_MS) continue
            start = t
            val at = Instant.ofEpochMilli(t).atZone(now.zone)
            out += Sitting(t, at.hour * 60 + at.minute, at.toLocalDate(), weight(at.toLocalDate(), now.toLocalDate()))
        }
        return out
    }

    private fun weight(date: LocalDate, today: LocalDate) =
        0.5.pow(ChronoUnit.DAYS.between(date, today).coerceAtLeast(0) / HALF_LIFE_DAYS)

    /** The model and its weighted average error in minutes, or null if no usual time stands out. */
    private fun timesOfDay(sittings: List<Sitting>, fitFrom: Long, now: ZonedDateTime): Pair<Routine.TimesOfDay, Double>? {
        val sorted = sittings.sortedBy { it.minute }
        // Cut the clock at its widest empty stretch, then wherever doses are far apart.
        val gaps = sorted.indices.map { i ->
            if (i == sorted.lastIndex) sorted[0].minute + DAY_MIN - sorted[i].minute else sorted[i + 1].minute - sorted[i].minute
        }
        val widest = gaps.indices.maxBy { gaps[it] }
        if (gaps[widest] <= JOIN_MIN) return null
        val clusters = ArrayList<MutableList<Sitting>>()
        for (k in 1..sorted.size) {
            val i = (widest + k) % sorted.size
            if (clusters.isEmpty() || gaps[(i - 1 + sorted.size) % sorted.size] > JOIN_MIN) clusters += mutableListOf<Sitting>()
            clusters.last() += sorted[i]
        }

        // Days from the first dose to the last, weighted like the doses.
        val first = sittings.minOf { it.date }
        val last = sittings.maxOf { it.date }
        val today = now.toLocalDate()
        val span = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val spanWeight = span.sumOf { weight(it, today) }

        val slots = clusters.mapNotNull { c ->
            val total = c.sumOf { it.weight }
            val angle = atan2(c.sumOf { it.weight * sin(rad(it.minute)) }, c.sumOf { it.weight * cos(rad(it.minute)) })
            val center = ((angle / (2 * Math.PI) * DAY_MIN).roundToInt() + DAY_MIN) % DAY_MIN
            val spread = sqrt(c.sumOf { it.weight * clockDistance(it.minute, center).toDouble().pow(2) } / total)
            val kept = c.map { it.date }.distinct().sumOf { weight(it, today) } / spanWeight
            if (kept >= MIN_KEPT && spread <= MAX_SPREAD_MIN && c.map { it.date }.distinct().size >= 2) {
                Slot(round5(center) % DAY_MIN, round5(spread.roundToInt()).coerceAtLeast(MIN_SPREAD_MIN))
            } else {
                null
            }
        }.sortedBy { it.minute }
        if (slots.isEmpty()) return null

        val recent = sittings.filter { it.start >= fitFrom }
        val error = recent.sumOf { s ->
            s.weight * slots.minOf { clockDistance(s.minute, it.minute) }.coerceAtMost(ERROR_CAP_MIN)
        } / recent.sumOf { it.weight }
        return Routine.TimesOfDay(slots, sittings.size) to error
    }

    /** The model and its weighted average error in minutes, or null if the gap isn't plausible. */
    private fun steadyGap(sittings: List<Sitting>, fitFrom: Long, lastLogged: Long): Pair<Routine.SteadyGap, Double>? {
        val gaps = sittings.zipWithNext().filter { (_, b) -> b.start >= fitFrom }
            .map { (a, b) -> ((b.start - a.start) / MINUTE_MS).toInt() to b.weight }
        if (gaps.size < MIN_DOSES - 1) return null
        val gap = weightedMedian(gaps)
        if (gap < MIN_GAP_MIN || gap > MAX_GAP_MIN) return null
        val deviations = gaps.map { (g, w) -> abs(g - gap) to w }
        val error = deviations.sumOf { (d, w) -> w * d.coerceAtMost(ERROR_CAP_MIN) } / gaps.sumOf { it.second }
        val spread = round5(weightedMedian(deviations)).coerceAtLeast(MIN_SPREAD_MIN)
        return Routine.SteadyGap(round5(gap), spread, sittings.last().start, lastLogged, sittings.size) to error
    }

    /** How long after the usual time it asks: twice the usual spread, within limits. */
    fun margin(spread: Int) = round5(spread * 2).coerceIn(MIN_MARGIN_MIN, MAX_MARGIN_MIN)

    /** The first question [routine] raises strictly after [after]. */
    fun nextAsk(routine: Routine, after: ZonedDateTime): Ask? {
        val afterMs = after.toInstant().toEpochMilli()
        return asksAround(routine, after).filter { it.ask > afterMs }.minByOrNull { it.ask }
    }

    /** The last question [routine] raised at or before [at]. */
    fun latestAsk(routine: Routine, at: ZonedDateTime): Ask? {
        val atMs = at.toInstant().toEpochMilli()
        return asksAround(routine, at).filter { it.ask <= atMs }.maxByOrNull { it.ask }
    }

    /** Whether a question raised at [ask] may still be asked at [now]: same day, and recent. */
    fun stillWorthAsking(ask: Ask, now: ZonedDateTime): Boolean {
        val at = Instant.ofEpochMilli(ask.ask).atZone(now.zone)
        return at.toLocalDate() == now.toLocalDate() && Duration.between(at, now) <= ASK_WITHIN
    }

    private fun asksAround(routine: Routine, at: ZonedDateTime): List<Ask> = when (routine) {
        is Routine.TimesOfDay -> {
            val date = at.toLocalDate()
            (-1L..2L).flatMap { d -> routine.slots.map { slotAsk(routine.slots, it, date.plusDays(d), at.zone) } }
        }
        is Routine.SteadyGap -> {
            val expected = routine.lastStart + routine.gapMinutes * MINUTE_MS
            listOf(Ask(expected, expected + margin(routine.spread) * MINUTE_MS, routine.lastLogged + 1))
        }
        else -> emptyList()
    }

    private fun slotAsk(slots: List<Slot>, slot: Slot, date: LocalDate, zone: ZoneId): Ask {
        val expected = date.atStartOfDay(zone).plusMinutes(slot.minute.toLong()).toInstant().toEpochMilli()
        // A dose counts for the nearest usual time, so never more than halfway back to the one before.
        val cover = minOf(MAX_COVER_MIN, slots.filter { it != slot }.minOfOrNull { clockDistance(it.minute, slot.minute) / 2 } ?: MAX_COVER_MIN)
        return Ask(expected, expected + margin(slot.spread) * MINUTE_MS, expected - cover * MINUTE_MS)
    }

    private fun rad(minute: Int) = minute.toDouble() / DAY_MIN * 2 * Math.PI

    private fun clockDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % DAY_MIN
        return minOf(d, DAY_MIN - d)
    }

    private fun round5(minutes: Int) = (minutes + 2) / 5 * 5

    private fun weightedMedian(values: List<Pair<Int, Double>>): Int {
        val sorted = values.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var sum = 0.0
        for ((v, w) in sorted) {
            sum += w
            if (sum >= half) return v
        }
        return sorted.last().first
    }
}
