package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
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
    val lastTakenLine: String,
    /** "Next at 21:00", or the overdue warning; null without reminders. */
    val dueLine: String?,
    val overdue: Boolean,
    /** How far along the wait for the next reminder is, 0 to 1. */
    val progress: Float,
)

/** Today's doses on a midnight-to-midnight line, with [axis] labels at 0, 6, 12, 18 and 24 h. */
data class DayStrip(val count: Int, val dots: List<StripDot>, val nowFraction: Float, val axis: List<String>)

data class StripDot(val fraction: Float, val label: String)

/** Builds the Today screen from the data, at a given moment. Pure, so it can be tested. */
object TodayModel {
    /** An unlogged reminder older than this is history, not something due. */
    private val OVERDUE_FOR = Duration.ofHours(12)
    private val ONE_DAY = Duration.ofHours(24)
    private val RECENT = Duration.ofHours(12)

    fun build(
        summaries: List<MedicationSummary>,
        ingredients: List<IngredientEntity>,
        reminders: List<ReminderEntity>,
        doses: List<DoseEntity>,
        presets: List<Double>,
        now: ZonedDateTime,
        f: TimeFormats,
    ): TodayUi {
        val ingredientsByMed = ingredients.groupBy { it.medicationId }
        val remindersByMed = reminders.filter { it.enabled }.groupBy { it.medicationId }
        val dosesByMed = doses.groupBy { it.medicationId }
        val cards = summaries.map { s ->
            card(s, ingredientsByMed[s.medication.id].orEmpty(), remindersByMed[s.medication.id].orEmpty(),
                dosesByMed[s.medication.id].orEmpty(), now, f)
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
        now: ZonedDateTime,
        f: TimeFormats,
    ): CardState {
        val med = s.medication
        val nowMs = now.toInstant().toEpochMilli()

        var lastTaken = TodayText.lastTaken(s.lastTakenAt, now, f)
        s.lastTakenAt?.takeIf { nowMs - it in 0..RECENT.toMillis() }?.let { lastTaken += " · " + TodayText.ago(it, now) }
        val recent = doses.filter { nowMs - it.takenAt in 0..ONE_DAY.toMillis() && it.unit == med.doseUnit }
        if (med.dose > 0 && recent.size >= 2) lastTaken += " · " + TodayText.inLast24h(recent.sumOf { it.amount }, med.doseUnit)

        val due = due(reminders, doses.map { it.takenAt }, s.lastTakenAt, now)
        return CardState(
            medication = med,
            type = MedicationType.fromKey(med.type),
            category = Category.fromKey(med.category),
            doseLine = TodayText.doseLine(med, ingredients),
            lastTakenLine = lastTaken,
            dueLine = due.overdueAt?.let { TodayText.overdue(it, now, f) } ?: due.next?.let { TodayText.next(it, now, f) },
            overdue = due.overdueAt != null,
            progress = if (due.overdueAt != null) 1f else due.progress,
        )
    }

    data class Due(val overdueAt: ZonedDateTime?, val next: ZonedDateTime?, val progress: Float)

    /**
     * Where a medication stands against its reminders. A dose logged within a reminder's early window
     * counts for it, so a reminder already taken care of is skipped rather than shown as next.
     */
    fun due(reminders: List<ReminderEntity>, doseTimes: List<Long>, lastTakenAt: Long?, now: ZonedDateTime): Due {
        val nowMs = now.toInstant().toEpochMilli()
        var overdueAt: ZonedDateTime? = null
        var best: Pair<ZonedDateTime, Float>? = null
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
            }
        }
        return Due(overdueAt, best?.first, best?.second ?: 0f)
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
        val axis = listOf(0, 6, 12, 18).map { LocalTime.of(it, 0).format(f.time) }
        // Midnight again closes the day; "24:00" reads better than a second "0:00".
        val end = if (axis.first().any { it.isLetter() }) axis.first() else "24:00"
        return DayStrip(today.size, dots, (nowMs - startMs) / length, axis + end)
    }
}
