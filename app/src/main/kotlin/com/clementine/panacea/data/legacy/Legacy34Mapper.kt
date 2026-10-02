package com.clementine.panacea.data.legacy

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity

/** Rows ready to insert into an empty database. */
data class LegacyImport(
    val medications: List<MedicationEntity>,
    val ingredients: List<IngredientEntity>,
    val doses: List<DoseEntity>,
    val reminders: List<ReminderEntity>,
    val amountPresets: List<Double>?,
    val problems: List<String>,
)

/** Turns a [LegacySnapshot] into database rows. Medication ids are assigned here (1, 2, 3 … in display order). */
object Legacy34Mapper {
    fun map(snapshot: LegacySnapshot): LegacyImport {
        val problems = snapshot.problems.toMutableList()
        val position = snapshot.order.withIndex().associate { (index, name) -> name to index }
        val unique = snapshot.medications.distinctBy { it.name }
        if (unique.size < snapshot.medications.size) {
            val skipped = snapshot.medications.size - unique.size
            problems += if (skipped == 1) "1 medication with a repeated name was skipped." else "$skipped medications with a repeated name were skipped."
        }
        // Display order: 3.4's saved order, then anything it missed, by name
        val ordered = unique.sortedWith(compareBy({ position[it.name] ?: Int.MAX_VALUE }, { it.name }))

        val medications = ordered.mapIndexed { index, med ->
            MedicationEntity(
                id = index + 1L,
                name = med.name,
                dose = med.dose,
                doseUnit = med.unit,
                category = med.category,
                type = med.type,
                sortOrder = index,
                lastMultiplier = snapshot.lastAmounts[med.name] ?: 1.0,
            )
        }
        val idByName = medications.associate { it.name to it.id }

        val ingredients = ordered.flatMap { med ->
            med.ingredients.mapIndexed { index, ingredient ->
                IngredientEntity(
                    medicationId = idByName.getValue(med.name),
                    position = index,
                    name = ingredient.name,
                    amount = ingredient.amount,
                    unit = ingredient.unit,
                )
            }
        }
        val doses = ordered.flatMap { med ->
            med.doses.map { dose ->
                DoseEntity(
                    medicationId = idByName.getValue(med.name),
                    takenAt = dose.takenAt,
                    multiplier = dose.fraction,
                    amount = dose.amount,
                    unit = med.unit,
                )
            }
        }

        val usedIds = mutableSetOf<Long>()
        val reminders = snapshot.reminders.mapNotNull { reminder ->
            val medicationId = idByName[reminder.medicationName]
            if (medicationId == null) {
                problems += "A reminder for \"${reminder.medicationName}\" was skipped: that medication no longer exists."
                return@mapNotNull null
            }
            // Keep 3.4's ids so anything keyed by them still matches; 0 lets Room assign one.
            val id = reminder.id?.toLong()?.takeIf { it != 0L && usedIds.add(it) } ?: 0L
            ReminderEntity(
                id = id,
                medicationId = medicationId,
                repeat = reminder.repeat,
                enabled = reminder.enabled,
                daysMask = reminder.daysMask,
                times = reminder.times,
                intervalHours = reminder.intervalHours,
                windowStart = reminder.windowStart,
                windowEnd = reminder.windowEnd,
                dayOfMonth = reminder.dayOfMonth,
                note = reminder.note,
                timesCompleted = reminder.timesCompleted,
                timesMissed = reminder.timesMissed,
                pending = reminder.pending,
                lastFiredAt = reminder.lastFiredAt,
                lastCompletedAt = reminder.lastCompletedAt,
            )
        }

        return LegacyImport(medications, ingredients, doses, reminders, snapshot.amountPresets, problems)
    }
}
