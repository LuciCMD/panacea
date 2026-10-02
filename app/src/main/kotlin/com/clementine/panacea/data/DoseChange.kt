package com.clementine.panacea.data

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.TakenIngredient
import kotlin.math.abs
import kotlin.math.max

/**
 * A medication's dose, pill weight or ingredients going from [old] to [new]. Doses logged at the old
 * value were either right (the prescription or the pill changed) or wrong (it was entered wrong), and
 * only the user knows which, so they're changed only when asked.
 */
class DoseChange(
    private val old: MedicationEntity,
    private val new: MedicationEntity,
    private val oldIngredients: List<IngredientEntity> = emptyList(),
    private val newIngredients: List<IngredientEntity> = emptyList(),
) {
    val dose = old.dose != new.dose || old.doseUnit != new.doseUnit
    val weight = old.weight != new.weight || (new.weight != null && old.weightUnit != new.weightUnit)
    val ingredients = TakenIngredient.of(oldIngredients, 1.0) != TakenIngredient.of(newIngredients, 1.0)

    /** Whether [d] was logged at the old value of something that changed. */
    fun affects(d: DoseEntity) = (dose && atOldDose(d)) || (weight && atOldWeight(d)) || (ingredients && atOldIngredients(d))

    /** [d] as if it had been logged at the new values. */
    fun fixed(d: DoseEntity): DoseEntity {
        var out = d
        if (dose && atOldDose(d)) out = out.copy(amount = new.dose * d.multiplier, unit = new.doseUnit)
        if (weight && atOldWeight(d)) {
            out = out.copy(weight = new.weight?.let { it * d.multiplier }, weightUnit = new.weight?.let { new.weightUnit })
        }
        if (ingredients && atOldIngredients(d)) out = out.copy(ingredients = TakenIngredient.of(newIngredients, d.multiplier))
        return out
    }

    private fun atOldIngredients(d: DoseEntity): Boolean {
        val then = TakenIngredient.of(oldIngredients, d.multiplier)
        return d.ingredients.size == then.size && d.ingredients.zip(then).all { (a, b) ->
            a.name.equals(b.name, ignoreCase = true) && a.unit == b.unit && same(a.amount, b.amount)
        }
    }

    /** Doses logged at another dose (an earlier prescription, say) are left alone. */
    private fun atOldDose(d: DoseEntity) = d.unit == old.doseUnit && same(d.amount, old.dose * d.multiplier)

    private fun atOldWeight(d: DoseEntity): Boolean {
        val w = old.weight ?: return d.weight == null
        return d.weight != null && d.weightUnit == old.weightUnit && same(d.weight, w * d.multiplier)
    }

    private fun same(a: Double, b: Double) = abs(a - b) <= 1e-9 * max(1.0, abs(b))
}
