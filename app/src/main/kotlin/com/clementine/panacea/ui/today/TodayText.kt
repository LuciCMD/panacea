package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
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

    /** What [multiplier] of the medication amounts to: "100 mg", or "× 2" when no dose is set. */
    fun amount(med: MedicationEntity, multiplier: Double): String =
        if (med.dose > 0) "${formatAmount(med.dose * multiplier)} ${med.doseUnit}" else "× ${formatAmount(multiplier)}"

    /** What [multiplier] of the medication weighs, or null when its weight isn't known. */
    fun weightOf(med: MedicationEntity, multiplier: Double): String? =
        med.weight?.let { weight(it * multiplier, med.weightUnit) }

    fun multiplier(m: Double): String = "× ${formatAmount(m)}"

    private fun weight(value: Double, unit: String) = "${formatAmount(value)} ${WeightUnit.fromKey(unit).key}"

    fun lastTaken(lastTakenAt: Long?, now: ZonedDateTime, f: TimeFormats): String {
        if (lastTakenAt == null) return "Not taken yet"
        val taken = Instant.ofEpochMilli(lastTakenAt).atZone(now.zone)
        val time = taken.format(f.time)
        return when (val days = daysBetween(taken, now)) {
            0L -> "Taken at $time"
            1L -> "Last taken yesterday at $time"
            in 2..WEEK_DAYS -> "Last taken ${taken.format(f.weekday)} at $time"
            else -> if (days < 0) "Taken at $time" else "Last taken ${taken.format(f.shortDate)} at $time"
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

    /** "just now", "12 min ago", "4 h ago", "4 h 12 min ago". */
    fun ago(at: Long, now: ZonedDateTime): String {
        val minutes = (now.toInstant().toEpochMilli() - at) / 60_000
        if (minutes < 1) return "just now"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min ago"
            m == 0L -> "$h h ago"
            else -> "$h h $m min ago"
        }
    }

    fun inLast24h(total: Double, unit: String) = "${formatAmount(total)} $unit in the last 24 h"

    /** For a medication with no dose set: "2 doses in the last 24 h". */
    fun dosesInLast24h(count: Int) = "${counted(count, "dose")} in the last 24 h"

    fun header(now: ZonedDateTime, f: TimeFormats) = "${now.format(f.longDate)} · ${now.format(f.time)}"

    fun dosesToday(count: Int) = if (count == 1) "1 logged" else "$count logged"

    fun logged(name: String, amount: String, takenAt: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String {
        val time = takenAt.format(f.time)
        return when (daysBetween(takenAt, now)) {
            0L -> if (ChronoUnit.MINUTES.between(takenAt, now) < 1) "$name $amount logged at $time" else "$name $amount logged for $time"
            else -> "$name $amount logged for yesterday at $time"
        }
    }

    fun removeQuestion(name: String, amount: String, takenAt: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String {
        val day = if (daysBetween(takenAt, now) == 0L) "" else "yesterday "
        return "$name $amount taken ${day}at ${takenAt.format(f.time)} will be removed from your history."
    }

    private fun daysBetween(a: ZonedDateTime, b: ZonedDateTime) =
        ChronoUnit.DAYS.between(a.toLocalDate(), b.withZoneSameInstant(a.zone).toLocalDate())
}
