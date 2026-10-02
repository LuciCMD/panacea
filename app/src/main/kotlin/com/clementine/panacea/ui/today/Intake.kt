package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import java.time.Duration

/**
 * How much of something was had in the last 24 hours, across every medication that has it: one
 * named like it, or one taken with it as an ingredient (as each dose recorded it). [name] is null for the medication itself, and
 * [unit] null when it's counted in doses (no dose set). [perOne] is what one more of the medication
 * adds, and [from] names the medications it came from.
 */
data class Had(val name: String?, val total: Double, val unit: String?, val perOne: Double, val from: List<String>)

/** What has been had lately, so the moment of taking more can say so. Pure, so it can be tested. */
object Intake {
    private val DAY_MS = Duration.ofHours(24).toMillis()

    /** The medication's own amount first, then each of its ingredients, over the last 24 hours. */
    fun last24h(
        med: MedicationEntity,
        medications: List<MedicationEntity>,
        ingredients: List<IngredientEntity>,
        doses: List<DoseEntity>,
        nowMs: Long,
    ): List<Had> {
        val recent = doses.filter { nowMs - it.takenAt in 0..DAY_MS }
        val byId = medications.associateBy { it.id }
        val ingredientsOf = ingredients.groupBy { it.medicationId }
        val own = if (med.dose > 0) {
            substance(med.name, med.doseUnit, med.dose, recent, byId).copy(name = null)
        } else {
            Had(null, recent.count { it.medicationId == med.id }.toDouble(), null, 1.0, listOf(med.name))
        }
        val contained = ingredientsOf[med.id].orEmpty()
            .filter { it.amount > 0 }
            .sortedBy { it.position }
            .map { substance(it.name, it.unit, it.amount, recent, byId) }
        return listOf(own) + contained
    }

    private fun substance(
        name: String,
        unit: String,
        perOne: Double,
        recent: List<DoseEntity>,
        byId: Map<Long, MedicationEntity>,
    ): Had {
        var total = 0.0
        val from = linkedSetOf<String>()
        recent.forEach { dose ->
            val med = byId[dose.medicationId] ?: return@forEach
            val amount = if (same(med.name, name) && same(dose.unit, unit)) {
                dose.amount
            } else {
                dose.ingredients.firstOrNull { same(it.name, name) && same(it.unit, unit) }?.amount ?: 0.0
            }
            if (amount > 0) {
                total += amount
                from += med.name
            }
        }
        return Had(name.trim(), total, unit.trim(), perOne, from.toList())
    }

    /** Names and units match however they were typed: "Acetaminophen " and "acetaminophen" are one. */
    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
}
