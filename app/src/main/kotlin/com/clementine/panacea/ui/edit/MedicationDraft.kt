package com.clementine.panacea.ui.edit

import com.clementine.panacea.ui.counted
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationCounts
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationName
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.DEFAULT_DOSE_UNIT
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.parseAmount
import com.clementine.panacea.model.plainAmount

data class IngredientDraft(val name: String = "", val amount: String = "", val unit: String = DEFAULT_DOSE_UNIT) {
    val isBlank get() = name.isBlank() && amount.isBlank()
}

/** The add / edit form as typed: numbers stay text until saved, so half-typed values survive. */
data class MedicationDraft(
    val id: Long = 0,
    val name: String = "",
    val dose: String = "",
    val doseUnit: String = DEFAULT_DOSE_UNIT,
    val weight: String = "",
    val weightUnit: String = WeightUnit.DEFAULT.key,
    val category: Category = Category.UNCATEGORIZED,
    val type: MedicationType = MedicationType.ORAL_TABLET,
    val ingredients: List<IngredientDraft> = emptyList(),
    val photoFront: String? = null,
    val photoBack: String? = null,
) {
    val isNew get() = id == 0L
}

data class DraftProblems(
    val name: String? = null,
    val dose: String? = null,
    val weight: String? = null,
    /** By ingredient row. */
    val ingredients: Map<Int, String> = emptyMap(),
) {
    val none get() = name == null && dose == null && weight == null && ingredients.isEmpty()
}

/** Turns drafts into rows and back, and says what's wrong with one. */
object Drafts {
    const val MAX_INGREDIENTS = 10
    const val MAX_NAME = 80

    private const val NUMBER = "Enter a number, such as 50 or 0.5."

    fun from(med: MedicationEntity, ingredients: List<IngredientEntity>) = MedicationDraft(
        id = med.id,
        name = med.name,
        dose = if (med.dose > 0) plainAmount(med.dose) else "",
        doseUnit = med.doseUnit,
        weight = med.weight?.let(::plainAmount).orEmpty(),
        weightUnit = WeightUnit.fromKey(med.weightUnit).key,
        category = Category.fromKey(med.category),
        type = MedicationType.fromKey(med.type),
        ingredients = ingredients.sortedBy { it.position }
            .map { IngredientDraft(it.name, if (it.amount > 0) plainAmount(it.amount) else "", it.unit) },
        photoFront = med.photoFront,
        photoBack = med.photoBack,
    )

    /** A number as people type it; see [parseAmount]. */
    fun parse(text: String): Double? = parseAmount(text)

    fun check(d: MedicationDraft, existing: List<MedicationName>): DraftProblems {
        val name = d.name.trim()
        val nameProblem = when {
            name.isEmpty() -> "Give it a name."
            name.length > MAX_NAME -> "Keep the name under $MAX_NAME characters."
            existing.any { it.id != d.id && it.name.trim().equals(name, ignoreCase = true) } ->
                "You already have a medication called $name."
            else -> null
        }
        val doseProblem = if (d.dose.isNotBlank() && parse(d.dose) == null) NUMBER else null
        val weightProblem = if (d.weight.isNotBlank() && (parse(d.weight) ?: 0.0) <= 0.0) {
            "Enter a weight above 0, or leave it empty."
        } else {
            null
        }
        val ingredientProblems = d.ingredients.withIndex().mapNotNull { (i, ing) ->
            when {
                ing.isBlank -> null
                ing.name.isBlank() -> i to "Give this ingredient a name."
                ing.amount.isNotBlank() && parse(ing.amount) == null -> i to NUMBER
                else -> null
            }
        }.toMap()
        return DraftProblems(nameProblem, doseProblem, weightProblem, ingredientProblems)
    }

    /** The row to save. Settings the form doesn't show are kept from [existing]. */
    fun toEntity(d: MedicationDraft, existing: MedicationEntity?, sortOrder: Int): MedicationEntity {
        val base = existing ?: MedicationEntity(name = "", dose = 0.0, doseUnit = DEFAULT_DOSE_UNIT, category = "", type = "", sortOrder = sortOrder)
        return base.copy(
            name = d.name.trim(),
            dose = parse(d.dose) ?: 0.0,
            doseUnit = d.doseUnit,
            weight = parse(d.weight)?.takeIf { it > 0 },
            weightUnit = d.weightUnit,
            category = d.category.key,
            type = d.type.key,
            photoFront = d.photoFront,
            photoBack = d.photoBack,
        )
    }

    /** What removing a medication takes with it, for the confirm. */
    fun removeQuestion(c: MedicationCounts): String {
        val parts = listOfNotNull(
            c.doses.takeIf { it > 0 }?.let { counted(it, "logged dose") },
            c.reminders.takeIf { it > 0 }?.let { counted(it, "reminder") },
        )
        if (parts.isEmpty()) return "It has no logged doses or reminders yet."
        val verb = if (parts.size == 1 && (c.doses + c.reminders) == 1) "goes" else "go"
        return "Its ${parts.joinToString(" and ")} $verb with it. This can't be undone."
    }

    fun toIngredients(d: MedicationDraft, medicationId: Long): List<IngredientEntity> =
        d.ingredients.filterNot { it.isBlank }.take(MAX_INGREDIENTS).mapIndexed { i, ing ->
            IngredientEntity(medicationId = medicationId, position = i, name = ing.name.trim(), amount = parse(ing.amount) ?: 0.0, unit = ing.unit)
        }
}
