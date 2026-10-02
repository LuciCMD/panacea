package com.clementine.panacea.ui.medication

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.DoseRow
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.history.HistoryDay
import com.clementine.panacea.ui.history.HistoryModel
import com.clementine.panacea.ui.reminders.ReminderCard
import com.clementine.panacea.ui.reminders.ReminderText
import com.clementine.panacea.ui.reminders.reminderCard
import java.time.ZonedDateTime

/** A label and its value, as in "Pill Weight  0.35 g per tablet". */
data class Fact(val label: String, val value: String)

data class TotalRow(val label: String, val doses: String, val amount: String, val weight: String) {
    /** The row read out as one line. */
    fun spoken(showAmount: Boolean, showWeight: Boolean): String {
        val parts = listOfNotNull(
            if (doses == "1") "1 dose" else "$doses doses",
            amount.takeIf { showAmount && it != NONE },
            weight.takeIf { showWeight && it != NONE },
        )
        return "$label: ${parts.joinToString(", ")}"
    }

    companion object {
        const val NONE = "–"
    }
}

/** Columns with nothing in them anywhere are left out. */
data class Totals(val showAmount: Boolean, val showWeight: Boolean, val rows: List<TotalRow>)

data class MedicationUi(
    val medication: MedicationEntity,
    val type: MedicationType,
    /** "Prescribed · Tablet" */
    val subtitle: String,
    val facts: List<Fact>,
    /** Name and amount per pill; the amount is empty when it isn't known. */
    val ingredients: List<Fact>,
    /** Null until a dose is logged. */
    val totals: Totals?,
    val history: List<HistoryDay>,
    val reminders: List<ReminderCard>,
    /** "Muted until 14:00" while this medication's reminders are muted. */
    val muted: String?,
)

/** Builds a medication's own page from its rows, at a given moment. Pure, so it can be tested. */
object MedicationModel {
    fun build(
        med: MedicationEntity,
        ingredients: List<IngredientEntity>,
        doses: List<DoseEntity>,
        reminders: List<ReminderEntity>,
        now: ZonedDateTime,
        f: TimeFormats,
    ): MedicationUi {
        val type = MedicationType.fromKey(med.type)
        val category = Category.fromKey(med.category)
        val noun = type.noun
        return MedicationUi(
            medication = med,
            type = type,
            subtitle = listOfNotNull(category.label.takeIf { category != Category.UNCATEGORIZED }, type.label).joinToString(" · "),
            facts = listOf(
                Fact("Dose", if (med.dose > 0) "${formatAmount(med.dose)} ${med.doseUnit} per $noun" else "Not set"),
                Fact("Pill Weight", med.weight?.let { "${formatAmount(it)} ${WeightUnit.fromKey(med.weightUnit).key} per $noun" } ?: "Not set"),
            ),
            ingredients = ingredients.sortedBy { it.position }.map {
                Fact(it.name, if (it.amount > 0) "${formatAmount(it.amount)} ${it.unit}" else "")
            },
            totals = totals(med, doses, now),
            history = HistoryModel.build(doses.map { DoseRow(it, med.name, med.type) }, now, f),
            reminders = reminders.map { reminderCard(it, med.name, now, f, med.mutedUntil) },
            muted = if (med.mutedUntil > now.toInstant().toEpochMilli()) ReminderText.mutedUntil(med.mutedUntil, now, f) else null,
        )
    }

    fun totals(med: MedicationEntity, doses: List<DoseEntity>, now: ZonedDateTime): Totals? {
        if (doses.isEmpty()) return null
        val unit = WeightUnit.fromKey(med.weightUnit)
        val periods = listOf(
            "Last 24 Hours" to now.minusHours(24),
            "Last 7 Days" to now.minusDays(7),
            "Last 30 Days" to now.minusDays(30),
            "All Time" to null,
        )
        val rows = periods.map { (label, since) ->
            val from = since?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
            val inPeriod = doses.filter { it.takenAt >= from }
            val weights = inPeriod.mapNotNull { weightOf(it, med, unit) }
            TotalRow(
                label = label,
                doses = inPeriod.size.toString(),
                amount = amounts(inPeriod, med.doseUnit),
                weight = if (weights.isEmpty()) TotalRow.NONE else "${formatAmount(weights.sum())} ${unit.key}",
            )
        }
        return Totals(
            showAmount = doses.any { it.amount > 0 },
            showWeight = doses.any { weightOf(it, med, unit) != null },
            rows = rows,
        )
    }

    /**
     * What one dose weighed, in [unit]: as logged, or else from the pill's weight now. Doses logged
     * before a weight was set (every dose from 3.4) would otherwise never count.
     */
    private fun weightOf(dose: DoseEntity, med: MedicationEntity, unit: WeightUnit): Double? {
        dose.weight?.let { return WeightUnit.fromKey(dose.weightUnit).convert(it, unit) }
        return med.weight?.let { WeightUnit.fromKey(med.weightUnit).convert(it * dose.multiplier, unit) }
    }

    /** "300 mg", or "300 mg + 5 mL" if the unit was changed along the way; the current unit first. */
    private fun amounts(doses: List<DoseEntity>, current: String): String {
        val byUnit = doses.filter { it.amount > 0 }.groupBy { it.unit }
        if (byUnit.isEmpty()) return TotalRow.NONE
        return byUnit.entries
            .sortedWith(compareBy({ it.key != current }, { it.key }))
            .joinToString(" + ") { (unit, list) -> "${formatAmount(list.sumOf { it.amount })} $unit" }
    }
}
