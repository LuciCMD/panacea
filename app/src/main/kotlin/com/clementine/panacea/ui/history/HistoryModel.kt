package com.clementine.panacea.ui.history

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.DoseRow
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.counted
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** One day of doses; [summary] is "3 doses", or "2 doses · 15 mg" on a medication's own page. */
data class HistoryDay(val title: String, val summary: String, val doses: List<HistoryItem>)

data class HistoryItem(
    val id: Long,
    val name: String,
    val type: MedicationType,
    /** The medication's front photo, if it has one. */
    val photo: String?,
    /** "400 mg · × 2 · 0.62 g" */
    val amount: String,
    val time: String,
    /** "Today at 8:27", "Monday at 8:27", "Tuesday, 1 September at 8:00": when, in full, for the dose sheet. */
    val taken: String,
    /** "With 487.5 mg Acetaminophen · 30 mg Caffeine", as the dose was logged; null without any. */
    val ingredients: String?,
    /** "Ibuprofen 400 mg taken yesterday at 22:10 will be removed from your history." */
    val removeQuestion: String,
    val dose: DoseEntity,
)

/** Groups the dose history by day, newest first. Pure, so it can be tested. */
object HistoryModel {
    private const val WEEK_DAYS = 6L

    /** [oneMedication]: the doses are all one medication's, so each day also totals its amount. */
    fun build(rows: List<DoseRow>, now: ZonedDateTime, f: TimeFormats, oneMedication: Boolean = false): List<HistoryDay> =
        rows.groupBy { Instant.ofEpochMilli(it.dose.takenAt).atZone(now.zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayRows) ->
                val days = ChronoUnit.DAYS.between(date, now.toLocalDate())
                val items = dayRows.sortedByDescending { it.dose.takenAt }.map { item(it, date, days, now, f) }
                HistoryDay(dayTitle(date, days, now, f), summary(dayRows.map { it.dose }, oneMedication), items)
            }

    /** "400 mg · × 2 · 0.62 g": the amount, the multiplier when it isn't one, and the weight. */
    fun amountLine(d: DoseEntity): String {
        val main = if (d.amount > 0) "${formatAmount(d.amount)} ${d.unit}" else "× ${formatAmount(d.multiplier)}"
        return listOfNotNull(
            main,
            "× ${formatAmount(d.multiplier)}".takeIf { d.amount > 0 && d.multiplier != 1.0 },
            d.weight?.let { "${formatAmount(it)} ${WeightUnit.fromKey(d.weightUnit).key}" },
        ).joinToString(" · ")
    }

    private fun dayTitle(date: LocalDate, days: Long, now: ZonedDateTime, f: TimeFormats) = when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days in 2..WEEK_DAYS -> date.format(f.weekday)
        date.year == now.year -> date.format(f.longDate)
        else -> "${date.format(f.longDate)} ${date.year}"
    }

    private fun summary(doses: List<DoseEntity>, oneMedication: Boolean): String {
        val count = counted(doses.size, "dose")
        if (!oneMedication) return count
        // A unit changed along the way is totalled on its own.
        val amounts = doses.filter { it.amount > 0 }.groupBy { it.unit }.map { (unit, list) -> "${formatAmount(list.sumOf { it.amount })} $unit" }
        return (listOf(count) + amounts).joinToString(" · ")
    }

    private fun item(row: DoseRow, date: LocalDate, days: Long, now: ZonedDateTime, f: TimeFormats): HistoryItem {
        val d = row.dose
        val main = if (d.amount > 0) "${formatAmount(d.amount)} ${d.unit}" else "× ${formatAmount(d.multiplier)}"
        val time = Instant.ofEpochMilli(d.takenAt).atZone(now.zone).format(f.time)
        val day = when (days) {
            0L -> ""
            1L -> "yesterday "
            else -> "on ${date.format(f.shortDate)} "
        }
        return HistoryItem(
            id = d.id,
            name = row.name,
            type = MedicationType.fromKey(row.type),
            photo = row.photo,
            amount = amountLine(d),
            time = time,
            taken = "${dayTitle(date, days, now, f)} at $time",
            ingredients = d.ingredients.takeIf { it.isNotEmpty() }
                ?.joinToString(" · ", prefix = "With ") { "${formatAmount(it.amount)} ${it.unit} ${it.name}" },
            removeQuestion = "${row.name} $main taken ${day}at $time will be removed from your history.",
            dose = d,
        )
    }
}
