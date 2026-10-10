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
import kotlin.math.ln
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

    /**
     * Taken a few times each waking day, a steady time apart, whenever the day starts: for a sleep
     * that moves, the clock says little but the gaps still hold
     */
    data class Rhythm(
        val perDay: Int,
        val gapMinutes: Int,
        val spread: Int,
        /** The usual break between waking days */
        val breakMinutes: Int,
        /** Null when the break varies too much to ask after it */
        val breakSpread: Int?,
        /** Doses since the last break, the last one included */
        val since: Int,
        val lastStart: Long,
        val lastLogged: Long,
        override val doses: Int,
    ) : Routine {
        val resting get() = since >= perDay
    }
}

/**
 * A question the routine raises: the dose expected at [expected] isn't logged by [ask]. A dose
 * logged from [coverFrom] on answers it.
 */
data class Ask(val expected: Long, val ask: Long, val coverFrom: Long) {
    fun coveredBy(doseTimes: List<Long>, now: Long) = doseTimes.any { it in coverFrom..now }
}

/**
 * Learns when a medication is taken from its doses. Three models are tried and the steadiest wins:
 * times of day (doses clustered on a 24-hour clock), a steady gap (the typical time between doses),
 * and a daily rhythm (so many doses a waking day, a steady gap apart, with a longer break between
 * days wherever it falls). Recent doses weigh more, so a changed routine is picked up within a week or two.
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

    /** Above this average error no model is trusted, unless its gap allows more (see [allowed]) */
    private const val MAX_ERROR_MIN = 60
    private const val MAX_LOOSE_ERROR_MIN = 150

    /** Times of day reads most naturally, then a steady gap; a later model must fit clearly better */
    private const val PREFER_GAP_MIN = 5
    private const val PREFER_RHYTHM_MIN = 10

    /** A break between waking days is at least this many times the usual gap */
    private const val MIN_BREAK_RATIO = 1.25

    /** How far apart, in within-group spreads, gaps and breaks must sit to be two groups and not one */
    private const val MIN_SEPARATION = 3.0

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
        val clock = timesOfDay(sittings, fitFrom, now)?.takeIf { it.second <= MAX_ERROR_MIN }
        val gap = steadyGap(sittings, fitFrom, times.last())?.takeIf { it.second <= allowed(it.first.gapMinutes, 10) }
        val rhythm = rhythm(sittings, fitFrom, times.last())
        return listOfNotNull<Pair<Routine, Double>>(
            clock,
            gap?.let { it.first to it.second + PREFER_GAP_MIN },
            rhythm?.let { it.first to it.second + PREFER_RHYTHM_MIN },
        ).minByOrNull { it.second }?.first ?: Routine.Irregular(sittings.size)
    }

    /** Error allowed for a model that waits [gap] minutes: 60, or a [share]th of a long gap, up to 150 */
    private fun allowed(gap: Int, share: Int) = (gap / share).coerceIn(MAX_ERROR_MIN, MAX_LOOSE_ERROR_MIN)

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

    /**
     * The model and its average error within a waking day in minutes, or null if the gaps don't fall
     * into short ones and a daily break. The break is the split of the gaps into two groups that keeps
     * each tightest (Otsu's method), on log minutes so 5 h against 13 h weighs like 1 h against 2.6 h
     */
    private fun rhythm(sittings: List<Sitting>, fitFrom: Long, lastLogged: Long): Pair<Routine.Rhythm, Double>? {
        val pairs = sittings.zipWithNext()
        val gaps = pairs.map { (a, b) -> (b.start - a.start) / MINUTE_MS.toDouble() to b.weight }.sortedBy { it.first }
        if (gaps.size < 4) return null
        val logs = gaps.map { ln(it.first.coerceAtLeast(1.0)) to it.second }

        /** Weighted mean and sum of squared deviations */
        fun stats(part: List<Pair<Double, Double>>): Pair<Double, Double> {
            val mean = part.sumOf { it.first * it.second } / part.sumOf { it.second }
            return mean to part.sumOf { it.second * (it.first - mean).pow(2) }
        }
        var split = -1
        var tightest = Double.MAX_VALUE
        for (k in 2..logs.size - 2) {
            val within = stats(logs.subList(0, k)).second + stats(logs.subList(k, logs.size)).second
            if (within < tightest) {
                tightest = within
                split = k
            }
        }
        if (split < 0) return null
        val apart = stats(logs.subList(split, logs.size)).first - stats(logs.subList(0, split)).first
        val pooled = sqrt(tightest / logs.sumOf { it.second })
        if (apart < ln(MIN_BREAK_RATIO) || apart < MIN_SEPARATION * pooled) return null
        val threshold = sqrt(gaps[split - 1].first * gaps[split].first)

        // About one break a day, or it isn't sleep
        val days = (sittings.last().start - sittings.first().start) / (DAY_MIN * MINUTE_MS.toDouble())
        if (days < 1 || (gaps.size - split) / days !in 0.6..1.6) return null

        // Each sitting's place in its waking day, from 1
        val place = IntArray(sittings.size)
        for (i in sittings.indices) {
            place[i] = if (i == 0 || sittings[i].start - sittings[i - 1].start >= threshold * MINUTE_MS) 1 else place[i - 1] + 1
        }
        // The first and last waking days may be cut short, so they count only when they're all there is
        val ends = sittings.indices.filter { it == sittings.lastIndex || place[it + 1] == 1 }
        val whole = ends.drop(1).dropLast(1).ifEmpty { ends }
        val perDay = weightedMedian(whole.map { place[it] to sittings[it].weight })
        if (perDay < 2) return null

        val gap = weightedMedian(gaps.subList(0, split).map { it.first.roundToInt() to it.second })
        val breakGap = weightedMedian(gaps.subList(split, gaps.size).map { it.first.roundToInt() to it.second })
        if (gap < MIN_GAP_MIN) return null

        // Judged as it would have guessed: the break after a day's last dose, the gap before it
        val within = ArrayList<Pair<Int, Double>>()
        val across = ArrayList<Pair<Int, Double>>()
        pairs.forEachIndexed { i, (a, b) ->
            if (b.start < fitFrom) return@forEachIndexed
            val actual = ((b.start - a.start) / MINUTE_MS).toInt()
            if (place[i] < perDay) within += abs(actual - gap) to b.weight else across += abs(actual - breakGap) to b.weight
        }
        if (within.size < 2) return null
        fun error(d: List<Pair<Int, Double>>) = d.sumOf { (e, w) -> w * e.coerceAtMost(ERROR_CAP_MIN) } / d.sumOf { it.second }
        val error = error(within)
        if (error > allowed(gap, 3)) return null
        val breakSpread = across.takeIf { it.size >= 2 && error(it) <= allowed(breakGap, 5) }
            ?.let { round5(weightedMedian(it)).coerceAtLeast(MIN_SPREAD_MIN) }

        return Routine.Rhythm(
            perDay = perDay,
            gapMinutes = round5(gap),
            spread = round5(weightedMedian(within)).coerceAtLeast(MIN_SPREAD_MIN),
            breakMinutes = round5(breakGap),
            breakSpread = breakSpread,
            since = place.last(),
            lastStart = sittings.last().start,
            lastLogged = lastLogged,
            doses = sittings.size,
        ) to error
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
        is Routine.Rhythm -> {
            val (wait, spread) = if (routine.resting) routine.breakMinutes to routine.breakSpread else routine.gapMinutes to routine.spread
            if (spread == null) {
                emptyList()
            } else {
                val expected = routine.lastStart + wait * MINUTE_MS
                listOf(Ask(expected, expected + margin(spread) * MINUTE_MS, routine.lastLogged + 1))
            }
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
