package com.clementine.panacea.data

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.MedicationEntity
import kotlin.math.abs
import kotlin.math.max

/**
 * A medication's dose or pill weight going from [old] to [new]. Doses logged at the old value were
 * either right (the prescription or the pill changed) or wrong (it was entered wrong), and only the
 * user knows which, so they're changed only when asked.
 */
class DoseChange(private val old: MedicationEntity, private val new: MedicationEntity) {
    val dose = old.dose != new.dose || old.doseUnit != new.doseUnit
    val weight = old.weight != new.weight || (new.weight != null && old.weightUnit != new.weightUnit)

    /** Whether [d] was logged at the old value of something that changed. */
    fun affects(d: DoseEntity) = (dose && atOldDose(d)) || (weight && atOldWeight(d))

    /** [d] as if it had been logged at the new values. */
    fun fixed(d: DoseEntity): DoseEntity {
        var out = d
        if (dose && atOldDose(d)) out = out.copy(amount = new.dose * d.multiplier, unit = new.doseUnit)
        if (weight && atOldWeight(d)) {
            out = out.copy(weight = new.weight?.let { it * d.multiplier }, weightUnit = new.weight?.let { new.weightUnit })
        }
        return out
    }

    /** Doses logged at another dose (an earlier prescription, say) are left alone. */
    private fun atOldDose(d: DoseEntity) = d.unit == old.doseUnit && same(d.amount, old.dose * d.multiplier)

    private fun atOldWeight(d: DoseEntity): Boolean {
        val w = old.weight ?: return d.weight == null
        return d.weight != null && d.weightUnit == old.weightUnit && same(d.weight, w * d.multiplier)
    }

    private fun same(a: Double, b: Double) = abs(a - b) <= 1e-9 * max(1.0, abs(b))
}
