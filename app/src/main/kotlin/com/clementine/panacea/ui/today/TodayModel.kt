package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.reminder.Routines
import com.clementine.panacea.reminder.Schedule
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

data class TodayUi(
    val header: String,
    val strip: DayStrip,
    val cards: List<CardState>,
    /** Categories that have a medication, in their usual order. */
    val categories: List<Category>,
    val presets: List<Double>,
)

data class CardState(
    val medication: MedicationEntity,
    val type: MedicationType,
    val category: Category,
    val doseLine: String,
    /** "Took 7.5 mg at 8:27 · 3 h 39 min ago": what was last taken, and when. */
    val lastTakenLine: String,
    /** "Next at 21:00", "Usually around 23:30" (learned), or the overdue warning; null without either. */
    val dueLine: String?,
    val overdue: Boolean,
    /** Overdue, or close enough to its time that a dose now counts for it: Take speaks up. */
    val dueNow: Boolean,
    /** How far along the wait for the next reminder is, 0 to 1. */
    val progress: Float,
    /** What was had in the last 24 h, of it and of each ingredient, for the take sheet and its page. */
    val had: List<Had> = emptyList(),
)

/** Today's doses on a midnight-to-midnight line, with [axis] labels at 0, 6, 12, 18 and 24 h. */
data class DayStrip(val count: Int, val dots: List<StripDot>, val nowFraction: Float, val axis: List<String>)

data class StripDot(val fraction: Float, val label: String)

/** Builds the Today screen from the data, at a given moment. Pure, so it can be tested. */
object TodayModel {
    /** An unlogged reminder older than this is history, not something due. */
    private val OVERDUE_FOR = Duration.ofHours(12)

    /** A learned dose is due from this long before its usual time, as a fixed reminder's early window. */
    private const val LEARNED_EARLY_MINUTES = 60L

    fun build(
        summaries: List<MedicationSummary>,
        ingredients: List<IngredientEntity>,
        reminders: List<ReminderEntity>,
        doses: List<DoseEntity>,
        presets: List<Double>,
        now: ZonedDateTime,
        f: TimeFormats,
        /** The last four weeks of dose times of each medication learning its routine. */
        learningTimes: Map<Long, List<Long>> = emptyMap(),
    ): TodayUi {
        val ingredientsByMed = ingredients.groupBy { it.medicationId }
        val remindersByMed = reminders.filter { it.enabled }.groupBy { it.medicationId }
        val dosesByMed = doses.groupBy { it.medicationId }
        val medications = summaries.map { it.medication }
        val nowMs = now.toInstant().toEpochMilli()
        val cards = summaries.map { s ->
            card(s, ingredientsByMed[s.medication.id].orEmpty(), remindersByMed[s.medication.id].orEmpty(),
                dosesByMed[s.medication.id].orEmpty(), learningTimes[s.medication.id], now, f)
                .copy(had = Intake.last24h(s.medication, medications, ingredients, doses, nowMs))
        }
        return TodayUi(
            header = TodayText.header(now, f),
            strip = strip(summaries, doses, now, f),
            cards = cards,
            categories = Category.entries.filter { c -> cards.any { it.category == c } },
            presets = presets,
        )
    }

    private fun card(
        s: MedicationSummary,
        ingredients: List<IngredientEntity>,
        reminders: List<ReminderEntity>,
        doses: List<DoseEntity>,
        learningTimes: List<Long>?,
        now: ZonedDateTime,
        f: TimeFormats,
    ): CardState {
        val med = s.medication
        val nowMs = now.toInstant().toEpochMilli()

        val type = MedicationType.fromKey(med.type)
        val lastAmount = TodayText.doseAmount(s.lastAmount ?: 0.0, s.lastUnit ?: med.doseUnit, s.lastDoseMultiplier ?: 1.0, type)
        var lastTaken = TodayText.lastTaken(s.lastTakenAt, lastAmount, now, f)
        s.lastTakenAt?.takeIf { it <= nowMs }?.let { lastTaken += " · " + TodayText.ago(it, now) }

        // Fixed reminders say when it's due; without any, a learned routine says when it usually is.
        if (reminders.isEmpty() && med.learnRoutine && learningTimes != null) {
            learned(learningTimes, s.lastTakenAt, med.routineAskedFor, now)?.let { l ->
                return CardState(
                    medication = med,
                    type = MedicationType.fromKey(med.type),
                    category = Category.fromKey(med.category),
                    doseLine = TodayText.doseLine(med, ingredients),
                    lastTakenLine = lastTaken,
                    dueLine = TodayText.usually(l.expected, l.missed, now, f),
                    overdue = l.missed,
                    dueNow = l.missed || !now.isBefore(l.expected.minusMinutes(LEARNED_EARLY_MINUTES)),
                    progress = l.progress,
                )
            }
        }
        val due = due(reminders, doses.map { it.takenAt }, s.lastTakenAt, now)
        return CardState(
            medication = med,
            type = MedicationType.fromKey(med.type),
            category = Category.fromKey(med.category),
            doseLine = TodayText.doseLine(med, ingredients),
            lastTakenLine = lastTaken,
            dueLine = due.overdueAt?.let { TodayText.overdue(it, now, f) }
                ?: due.next?.let { TodayText.next(it, now, f) }
                // Learning, but too few doses yet to know a routine: say so, rather than nothing.
                ?: TodayText.LEARNING.takeIf { med.learnRoutine && reminders.isEmpty() },
            overdue = due.overdueAt != null,
            dueNow = due.overdueAt != null || due.soon,
            progress = if (due.overdueAt != null) 1f else due.progress,
        )
    }

    /** [soon]: [next] is close enough that a dose now would count for it. */
    data class Due(val overdueAt: ZonedDateTime?, val next: ZonedDateTime?, val progress: Float, val soon: Boolean = false)

    /**
     * Where a medication stands against its reminders. A dose logged within a reminder's early window
     * counts for it, so a reminder already taken care of is skipped rather than shown as next.
     */
    fun due(reminders: List<ReminderEntity>, doseTimes: List<Long>, lastTakenAt: Long?, now: ZonedDateTime): Due {
        val nowMs = now.toInstant().toEpochMilli()
        var overdueAt: ZonedDateTime? = null
        var best: Pair<ZonedDateTime, Float>? = null
        var soon = false
        for (r in reminders) {
            val early = Schedule.earlyWindowMinutes(r)
            val latest = Schedule.latestAtOrBefore(r, now)
            val upcoming = Schedule.nextAfter(r, now)
            val upcomingDone = upcoming != null && Schedule.isCovered(upcoming, early, doseTimes, nowMs)
            val next = if (upcomingDone) Schedule.nextAfter(r, upcoming) else upcoming
            val previous = if (upcomingDone) upcoming else latest

            if (latest != null && Duration.between(latest, now) <= OVERDUE_FOR) {
                // A dose inside the upcoming reminder's early window belongs to that one.
                val until = if (upcomingDone) upcoming.minusMinutes(early.toLong()).toInstant().toEpochMilli() - 1 else nowMs
                if (!Schedule.isCovered(latest, early, doseTimes, until) && (overdueAt == null || latest.isAfter(overdueAt))) {
                    overdueAt = latest
                }
            }
            if (next != null && (best == null || next.isBefore(best.first))) {
                best = next to progress(previous, early, lastTakenAt, next, now)
                soon = !now.isBefore(next.minusMinutes(early.toLong()))
            }
        }
        return Due(overdueAt, best?.first, best?.second ?: 0f, soon)
    }

    data class Learned(val expected: ZonedDateTime, val missed: Boolean, val progress: Float)

    /**
     * Where a medication stands against its learned routine: the usual dose it asked about and nobody
     * answered yet (only ones asked since learning was turned on, [askedFor]), or else the next usual
     * dose not already taken. Null while there's no routine to go by.
     */
    fun learned(doseTimes: List<Long>, lastTakenAt: Long?, askedFor: Long, now: ZonedDateTime): Learned? {
        val nowMs = now.toInstant().toEpochMilli()
        val routine = Routines.learn(doseTimes, now)
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(now.zone)

        Routines.latestAsk(routine, now)
            ?.takeIf { it.ask >= askedFor && Routines.stillWorthAsking(it, now) && !it.coveredBy(doseTimes, nowMs) }
            ?.let { return Learned(at(it.expected), missed = true, progress = 1f) }

        val next = generateSequence(Routines.nextAsk(routine, now)) { Routines.nextAsk(routine, at(it.ask)) }
            .take(8)
            .firstOrNull { !it.coveredBy(doseTimes, nowMs) } ?: return null
        val progress = when {
            lastTakenAt == null -> 0f
            next.expected <= lastTakenAt -> 1f
            else -> ((nowMs - lastTakenAt).toFloat() / (next.expected - lastTakenAt)).coerceIn(0f, 1f)
        }
        return Learned(at(next.expected), missed = false, progress = progress)
    }

    /** Elapsed share of the wait from the last dose (or the previous reminder) to [next]. */
    private fun progress(previous: ZonedDateTime?, early: Int, lastTakenAt: Long?, next: ZonedDateTime, now: ZonedDateTime): Float {
        val start = when {
            previous == null -> lastTakenAt ?: return 0f
            lastTakenAt != null && lastTakenAt >= previous.minusMinutes(early.toLong()).toInstant().toEpochMilli() -> lastTakenAt
            else -> previous.toInstant().toEpochMilli()
        }
        val end = next.toInstant().toEpochMilli()
        if (end <= start) return 1f
        return ((now.toInstant().toEpochMilli() - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    private fun strip(summaries: List<MedicationSummary>, doses: List<DoseEntity>, now: ZonedDateTime, f: TimeFormats): DayStrip {
        val dayStart = now.toLocalDate().atStartOfDay(now.zone)
        val dayEnd = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val startMs = dayStart.toInstant().toEpochMilli()
        val length = (dayEnd.toInstant().toEpochMilli() - startMs).toFloat()
        val nowMs = now.toInstant().toEpochMilli()
        val names = summaries.associate { it.medication.id to it.medication.name }
        val today = doses.filter { it.takenAt in startMs..nowMs }.sortedBy { it.takenAt }
        val dots = today.map {
            val time = Instant.ofEpochMilli(it.takenAt).atZone(now.zone).format(f.time)
            StripDot((it.takenAt - startMs) / length, "${names[it.medicationId] ?: "Removed medication"} at $time")
        }
        return DayStrip(today.size, dots, (nowMs - startMs) / length, axis(f))
    }

    /** Labels for a midnight-to-midnight line: 0, 6, 12, 18 and 24 h. */
    fun axis(f: TimeFormats): List<String> {
        val axis = listOf(0, 6, 12, 18).map { LocalTime.of(it, 0).format(f.time) }
        // Midnight again closes the day; "24:00" reads better than a second "0:00".
        val end = if (axis.first().any { it.isLetter() }) axis.first() else "24:00"
        return axis + end
    }
}
