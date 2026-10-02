package com.clementine.panacea.ui.history

import com.clementine.panacea.data.db.DoseRow
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.TimeFormats
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class HistoryDay(val title: String, val doses: List<HistoryItem>)

data class HistoryItem(
    val id: Long,
    val name: String,
    val type: MedicationType,
    /** "400 mg · × 2 · 0.62 g" */
    val amount: String,
    val time: String,
    /** "Ibuprofen 400 mg taken yesterday at 22:10 will be removed from your history." */
    val removeQuestion: String,
)

/** Groups the dose history by day, newest first. Pure, so it can be tested. */
object HistoryModel {
    private const val WEEK_DAYS = 6L

    fun build(rows: List<DoseRow>, now: ZonedDateTime, f: TimeFormats): List<HistoryDay> =
        rows.groupBy { Instant.ofEpochMilli(it.dose.takenAt).atZone(now.zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayRows) ->
                val days = ChronoUnit.DAYS.between(date, now.toLocalDate())
                val title = when {
                    days == 0L -> "Today"
                    days == 1L -> "Yesterday"
                    days in 2..WEEK_DAYS -> date.format(f.weekday)
                    date.year == now.year -> date.format(f.longDate)
                    else -> "${date.format(f.longDate)} ${date.year}"
                }
                val items = dayRows.sortedByDescending { it.dose.takenAt }.map { item(it, days, date.format(f.shortDate), now, f) }
                HistoryDay(title, items)
            }

    private fun item(row: DoseRow, days: Long, shortDate: String, now: ZonedDateTime, f: TimeFormats): HistoryItem {
        val d = row.dose
        val main = if (d.amount > 0) "${formatAmount(d.amount)} ${d.unit}" else "× ${formatAmount(d.multiplier)}"
        val parts = listOfNotNull(
            main,
            "× ${formatAmount(d.multiplier)}".takeIf { d.amount > 0 && d.multiplier != 1.0 },
            d.weight?.let { "${formatAmount(it)} ${WeightUnit.fromKey(d.weightUnit).key}" },
        )
        val time = Instant.ofEpochMilli(d.takenAt).atZone(now.zone).format(f.time)
        val day = when (days) {
            0L -> ""
            1L -> "yesterday "
            else -> "on $shortDate "
        }
        return HistoryItem(
            id = d.id,
            name = row.name,
            type = MedicationType.fromKey(row.type),
            amount = parts.joinToString(" · "),
            time = time,
            removeQuestion = "${row.name} $main taken ${day}at $time will be removed from your history.",
        )
    }
}
