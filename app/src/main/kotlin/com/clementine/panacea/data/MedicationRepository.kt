package com.clementine.panacea.data

import androidx.room.withTransaction
import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.DoseRow
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationCounts
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationName
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.MetaEntity
import com.clementine.panacea.data.db.MetaKeys
import com.clementine.panacea.data.db.PanaceaDatabase
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.data.db.ReminderRow
import com.clementine.panacea.model.Amounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A dose just logged, with what's needed to take it back. */
data class TakenDose(
    val dose: DoseEntity,
    val medication: MedicationEntity,
    /** The medication's remembered amount before this dose changed it. */
    val previousMultiplier: Double,
)

class MedicationRepository(private val db: PanaceaDatabase) {
    private val medications = db.medicationDao()
    private val doses = db.doseDao()

    fun observeSummaries(): Flow<List<MedicationSummary>> = medications.observeSummaries()

    fun observeIngredients(): Flow<List<IngredientEntity>> = medications.observeIngredients()

    fun observeDosesSince(since: Long): Flow<List<DoseEntity>> = doses.observeSince(since)

    fun observeEnabledReminders(): Flow<List<ReminderEntity>> = db.reminderDao().observeEnabled()

    fun observeHistory(): Flow<List<DoseRow>> = doses.observeHistory()

    fun observeReminderRows(): Flow<List<ReminderRow>> = db.reminderDao().observeRows()

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) = db.reminderDao().setEnabled(id, enabled)

    /** Removes one dose from the history; the remembered amount stays as it is. */
    suspend fun removeDose(id: Long) = doses.delete(id)

    fun observePresets(): Flow<List<Double>> =
        db.metaDao().observe(MetaKeys.AMOUNT_PRESETS).map(Amounts::parsePresets)

    /** Logs [multiplier] of the medication at [takenAt] and remembers the amount for next time. */
    suspend fun takeDose(medicationId: Long, multiplier: Double, takenAt: Long): TakenDose? = db.withTransaction {
        val med = medications.get(medicationId) ?: return@withTransaction null
        val m = Amounts.round(multiplier)
        val dose = DoseEntity(
            medicationId = med.id,
            takenAt = takenAt,
            multiplier = m,
            amount = med.dose * m,
            unit = med.doseUnit,
            weight = med.weight?.let { it * m },
            weightUnit = med.weight?.let { med.weightUnit },
        )
        val id = doses.insert(dose)
        medications.setLastMultiplier(med.id, m)
        TakenDose(dose.copy(id = id), med, med.lastMultiplier)
    }

    suspend fun undoDose(taken: TakenDose) = db.withTransaction {
        doses.delete(taken.dose.id)
        medications.restoreLastMultiplier(taken.medication.id, taken.dose.multiplier, taken.previousMultiplier)
    }

    suspend fun medication(id: Long): Pair<MedicationEntity, List<IngredientEntity>>? {
        val med = medications.get(id) ?: return null
        return med to medications.ingredientsOf(id)
    }

    suspend fun names(): List<MedicationName> = medications.names()

    suspend fun counts(id: Long): MedicationCounts = medications.counts(id)

    suspend fun photoFiles(): List<String> = medications.photoFiles()

    /**
     * Adds [medication] (id 0) at the end of the list, or replaces the one with its id, along with its
     * ingredients. Returns the id and the row as it was before, if any.
     */
    suspend fun save(
        medication: MedicationEntity,
        ingredients: (Long) -> List<IngredientEntity>,
    ): Pair<Long, MedicationEntity?> = db.withTransaction {
        val previous = if (medication.id == 0L) null else medications.get(medication.id)
        val id = if (previous == null) {
            medications.insert(medication.copy(id = 0, sortOrder = medications.maxSortOrder() + 1))
        } else {
            medications.update(medication)
            medication.id
        }
        medications.deleteIngredients(id)
        medications.insertIngredients(ingredients(id))
        id to previous
    }

    /** Deletes the medication with its ingredients, doses and reminders. Returns the row it was. */
    suspend fun delete(id: Long): MedicationEntity? = db.withTransaction {
        val med = medications.get(id)
        medications.delete(id)
        med
    }

    suspend fun addPreset(value: Double) = db.withTransaction {
        val meta = db.metaDao()
        val presets = Amounts.parsePresets(meta.get(MetaKeys.AMOUNT_PRESETS))
        val updated = Amounts.withPreset(presets, value)
        if (updated != presets) meta.put(MetaEntity(MetaKeys.AMOUNT_PRESETS, updated.joinToString(",")))
    }
}
