package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.counted
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The words on the Today screen, kept apart from Compose so they can be tested. */
object TodayText {
    private const val WEEK_DAYS = 6L

    /** "50 mg · with Caffeine · weighs 0.2 g", leaving out what isn't set. */
    fun doseLine(med: MedicationEntity, ingredients: List<IngredientEntity>): String {
        val parts = mutableListOf<String>()
        if (med.dose > 0) parts += "${formatAmount(med.dose)} ${med.doseUnit}"
        if (ingredients.isNotEmpty()) {
            val first = ingredients.first().name
            val names = if (ingredients.size == 1) first else "$first and ${ingredients.size - 1} more"
            parts += if (med.dose > 0) "with $names" else names
        }
        med.weight?.let { parts += "weighs ${weight(it, med.weightUnit)}" }
        return parts.joinToString(" · ").ifEmpty { "No dose set" }
    }

    /** What [multiplier] of the medication amounts to: "100 mg", or "2 tablets" when no dose is set. */
    fun amount(med: MedicationEntity, multiplier: Double): String =
        if (med.dose > 0) "${formatAmount(med.dose * multiplier)} ${med.doseUnit}" else MedicationType.fromKey(med.type).pieces(multiplier)

    /** What a logged dose was: "7.5 mg" as logged, or "2 tablets" when it had no amount. */
    fun doseAmount(amount: Double, unit: String, multiplier: Double, type: MedicationType): String =
        if (amount > 0) "${formatAmount(amount)} $unit" else type.pieces(multiplier)

    /** What [multiplier] of the medication weighs, or null when its weight isn't known. */
    fun weightOf(med: MedicationEntity, multiplier: Double): String? =
        med.weight?.let { weight(it * multiplier, med.weightUnit) }

    fun multiplier(m: Double): String = "× ${formatAmount(m)}"

    private fun weight(value: Double, unit: String) = "${formatAmount(value)} ${WeightUnit.fromKey(unit).key}"

    /** "Took 7.5 mg at 8:27", "Took 7.5 mg yesterday at 22:10", "Took 7.5 mg on Monday at 8:00". */
    fun lastTaken(lastTakenAt: Long?, amount: String, now: ZonedDateTime, f: TimeFormats): String {
        if (lastTakenAt == null) return "Not taken yet"
        val taken = Instant.ofEpochMilli(lastTakenAt).atZone(now.zone)
        val time = taken.format(f.time)
        return when (val days = daysBetween(taken, now)) {
            0L -> "Took $amount at $time"
            1L -> "Took $amount yesterday at $time"
            in 2..WEEK_DAYS -> "Took $amount on ${taken.format(f.weekday)} at $time"
            else -> if (days < 0) "Took $amount at $time" else "Took $amount on ${taken.format(f.shortDate)} at $time"
        }
    }

    fun next(at: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String {
        val time = at.format(f.time)
        return when (daysBetween(now, at)) {
            0L -> "Next at $time"
            1L -> "Next at $time tomorrow"
            in 2..WEEK_DAYS -> "Next on ${at.format(f.weekday)} at $time"
            else -> "Next on ${at.format(f.shortDate)} at $time"
        }
    }

    /** A learned routine: "Usually around 23:30", or "… · not logged yet" once it has asked. */
    fun usually(at: ZonedDateTime, missed: Boolean, now: ZonedDateTime, f: TimeFormats): String {
        val time = at.format(f.time)
        val text = when (daysBetween(now, at)) {
            -1L -> "Usually around $time yesterday"
            0L -> "Usually around $time"
            1L -> "Usually around $time tomorrow"
            else -> "Usually around $time on ${at.format(f.weekday)}"
        }
        return if (missed) "$text · not logged yet" else text
    }

    fun overdue(at: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String {
        val time = at.format(f.time)
        val whenDue = if (daysBetween(at, now) == 0L) "Due at $time" else "Due yesterday at $time"
        return "$whenDue · ${ago(at.toInstant().toEpochMilli(), now)}, not logged yet"
    }

    /** "just now", "12 min ago", "4 h 12 min ago", then "1 day 4 h ago", and past a week "9 days ago". */
    fun ago(at: Long, now: ZonedDateTime): String {
        val minutes = (now.toInstant().toEpochMilli() - at) / 60_000
        if (minutes < 1) return "just now"
        val h = minutes / 60
        val m = minutes % 60
        val days = counted((h / 24).toInt(), "day")
        return when {
            h == 0L -> "$m min ago"
            h < 24 -> if (m == 0L) "$h h ago" else "$h h $m min ago"
            h < 7 * 24 && h % 24 != 0L -> "$days ${h % 24} h ago"
            else -> "$days ago"
        }
    }

    /**
     * "15 mg in the last 24 h", "975 mg Acetaminophen in the last 24 h, from Oxycodone and Tylenol",
     * "2 doses in the last 24 h", or "Nothing in the last 24 h".
     */
    fun had(h: Had): String {
        val from = if (h.from.size > 1) ", from ${andList(h.from)}" else ""
        val what = when {
            h.unit == null -> counted(h.total.toInt(), "dose")
            h.total <= 0 -> return if (h.name == null) "Nothing in the last 24 h" else "No ${h.name} in the last 24 h"
            else -> "${formatAmount(h.total)} ${h.unit}" + (h.name?.let { " $it" } ?: "")
        }
        return "$what in the last 24 h$from"
    }

    /** What taking [multiplier] now would make it: "22.5 mg with this one", "3 with this one". */
    fun withThisOne(h: Had, multiplier: Double): String =
        if (h.unit == null) "${formatAmount(h.total + 1)} with this one"
        else "${formatAmount(h.total + h.perOne * multiplier)} ${h.unit} with this one"

    private fun andList(names: List<String>): String =
        if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()

    /** The due line of a medication learning its routine before it has one. */
    const val LEARNING = "Learning when you usually take it"

    /** Today's ⓘ: how to read a card. */
    const val HOW_TO_READ =
        "The ring around each medication fills as its next dose comes due, and changes colour once one is missed. " +
            "The × button sets how many each Take logs."

    /** The day strip's ⓘ. */
    const val STRIP =
        "Each dot is a dose logged today, placed at the time it was taken. The upright line is now."

    fun header(now: ZonedDateTime, f: TimeFormats) = "${now.format(f.longDate)} · ${now.format(f.time)}"

    fun dosesToday(count: Int) = if (count == 1) "1 logged" else "$count logged"

    fun logged(name: String, amount: String, takenAt: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String {
        val time = takenAt.format(f.time)
        return when (daysBetween(takenAt, now)) {
            0L -> if (ChronoUnit.MINUTES.between(takenAt, now) < 1) "$name $amount logged at $time" else "$name $amount logged for $time"
            else -> "$name $amount logged for yesterday at $time"
        }
    }

    fun removed(name: String, amount: String) = "$name $amount removed"

    private fun daysBetween(a: ZonedDateTime, b: ZonedDateTime) =
        ChronoUnit.DAYS.between(a.toLocalDate(), b.withZoneSameInstant(a.zone).toLocalDate())
}
