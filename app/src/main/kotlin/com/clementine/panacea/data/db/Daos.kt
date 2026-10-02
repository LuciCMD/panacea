package com.clementine.panacea.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query(
        """
        SELECT m.*, l.takenAt AS lastTakenAt, l.amount AS lastAmount, l.unit AS lastUnit,
            l.multiplier AS lastDoseMultiplier
        FROM medication m
        LEFT JOIN dose l ON l.id = (
            SELECT d.id FROM dose d WHERE d.medicationId = m.id ORDER BY d.takenAt DESC, d.id DESC LIMIT 1
        )
        WHERE m.removedAt IS NULL
        ORDER BY m.sortOrder, m.name
        """
    )
    fun observeSummaries(): Flow<List<MedicationSummary>>

    /** A medication in use; a removed one is null here. */
    @Query("SELECT * FROM medication WHERE id = :id AND removedAt IS NULL")
    suspend fun get(id: Long): MedicationEntity?

    /** Any medication, removed or not, for restoring one. */
    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun getAny(id: Long): MedicationEntity?

    @Query("SELECT * FROM medication WHERE id = :id AND removedAt IS NULL")
    fun observe(id: Long): Flow<MedicationEntity?>

    /** Every medication, removed ones too: their recent doses still count toward what's been had. */
    @Query("SELECT * FROM medication")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication WHERE removedAt IS NOT NULL ORDER BY removedAt DESC")
    fun observeRemoved(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun removedBefore(before: Long): List<MedicationEntity>

    @Query("UPDATE medication SET removedAt = :at WHERE id = :id")
    suspend fun setRemovedAt(id: Long, at: Long?)

    @Query("SELECT * FROM ingredient WHERE medicationId = :id ORDER BY position")
    suspend fun ingredientsOf(id: Long): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE medicationId = :id ORDER BY position")
    fun observeIngredientsOf(id: Long): Flow<List<IngredientEntity>>

    @Query("SELECT id, name FROM medication WHERE removedAt IS NULL")
    suspend fun names(): List<MedicationName>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM medication")
    suspend fun maxSortOrder(): Int

    @Query("SELECT photoFront FROM medication WHERE photoFront IS NOT NULL UNION SELECT photoBack FROM medication WHERE photoBack IS NOT NULL")
    suspend fun photoFiles(): List<String>

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("DELETE FROM medication WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ingredient WHERE medicationId = :id")
    suspend fun deleteIngredients(id: Long)

    @Query("SELECT (SELECT COUNT(*) FROM dose WHERE medicationId = :id) AS doses, (SELECT COUNT(*) FROM reminder WHERE medicationId = :id) AS reminders")
    suspend fun counts(id: Long): MedicationCounts

    @Query("SELECT * FROM ingredient ORDER BY medicationId, position")
    fun observeIngredients(): Flow<List<IngredientEntity>>

    @Query("UPDATE medication SET mutedUntil = :until WHERE id = :id")
    suspend fun setMutedUntil(id: Long, until: Long)

    @Query("UPDATE medication SET mutedUntil = 0 WHERE mutedUntil > 0")
    suspend fun clearMutes()

    @Query("SELECT * FROM medication")
    suspend fun all(): List<MedicationEntity>

    @Query("SELECT id, name FROM medication WHERE removedAt IS NULL ORDER BY sortOrder, name")
    fun observeNames(): Flow<List<MedicationName>>

    @Query("SELECT * FROM medication WHERE learnRoutine = 1 AND removedAt IS NULL")
    fun observeLearning(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication WHERE learnRoutine = 1 AND removedAt IS NULL")
    suspend fun learning(): List<MedicationEntity>

    /** Turning learning on starts from now, so it doesn't ask a question already past. */
    @Query("UPDATE medication SET learnRoutine = :on, routineAskedFor = CASE WHEN :on THEN :now ELSE routineAskedFor END WHERE id = :id")
    suspend fun setLearnRoutine(id: Long, on: Boolean, now: Long)

    @Query("UPDATE medication SET routineAskedFor = :at WHERE id = :id")
    suspend fun setRoutineAskedFor(id: Long, at: Long)

    @Query("UPDATE medication SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("UPDATE medication SET lastMultiplier = :multiplier WHERE id = :id")
    suspend fun setLastMultiplier(id: Long, multiplier: Double)

    /** Puts back [previous] unless the amount was changed again since [current] was set. */
    @Query("UPDATE medication SET lastMultiplier = :previous WHERE id = :id AND lastMultiplier = :current")
    suspend fun restoreLastMultiplier(id: Long, current: Double, previous: Double)

    @Insert
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Query("SELECT * FROM ingredient")
    suspend fun allIngredients(): List<IngredientEntity>

    @Query("SELECT COUNT(*) FROM medication")
    suspend fun count(): Int

    /** Empties the medication tables, for a restore. Each table by name, not trusting the cascade alone. */
    @Query("DELETE FROM ingredient")
    suspend fun deleteAllIngredients()

    @Query("DELETE FROM medication")
    suspend fun deleteAll()

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)
}

@Dao
interface DoseDao {
    @Insert
    suspend fun insert(dose: DoseEntity): Long

    @Query("DELETE FROM dose WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM dose WHERE id = :id")
    suspend fun get(id: Long): DoseEntity?

    @Query(
        """
        SELECT d.*, m.name AS name, m.type AS type, m.photoFront AS photo FROM dose d
        JOIN medication m ON m.id = d.medicationId
        ORDER BY d.takenAt DESC
        """
    )
    fun observeHistory(): Flow<List<DoseRow>>

    @Query("SELECT * FROM dose WHERE medicationId = :id ORDER BY takenAt DESC")
    fun observeOf(id: Long): Flow<List<DoseEntity>>

    @Query("SELECT * FROM dose WHERE medicationId = :id")
    suspend fun of(id: Long): List<DoseEntity>

    @Update
    suspend fun update(doses: List<DoseEntity>)

    @Query("SELECT takenAt FROM dose WHERE medicationId = :id AND takenAt >= :since")
    suspend fun timesSince(id: Long, since: Long): List<Long>

    /** Dose times of every medication learning its routine, since [since]. */
    @Query(
        """
        SELECT d.medicationId AS medicationId, d.takenAt AS takenAt FROM dose d
        JOIN medication m ON m.id = d.medicationId
        WHERE m.learnRoutine = 1 AND d.takenAt >= :since
        """
    )
    fun observeLearningTimes(since: Long): Flow<List<DoseTime>>

    @Query("SELECT * FROM dose WHERE takenAt >= :since ORDER BY takenAt")
    fun observeSince(since: Long): Flow<List<DoseEntity>>

    @Query("SELECT MIN(takenAt) FROM dose")
    fun observeFirstTakenAt(): Flow<Long?>

    @Insert
    suspend fun insertAll(doses: List<DoseEntity>)

    @Query("SELECT * FROM dose")
    suspend fun all(): List<DoseEntity>

    @Query("SELECT COUNT(*) FROM dose")
    suspend fun count(): Int

    @Query("DELETE FROM dose")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT r.*, m.name AS medicationName, m.mutedUntil AS medicationMutedUntil FROM reminder r
        JOIN medication m ON m.id = r.medicationId
        WHERE m.removedAt IS NULL
        ORDER BY m.sortOrder, m.name, r.id
        """
    )
    fun observeRows(): Flow<List<ReminderRow>>

    @Query("UPDATE reminder SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM reminder WHERE medicationId = :id ORDER BY id")
    fun observeOf(id: Long): Flow<List<ReminderEntity>>

    @Query("SELECT r.* FROM reminder r JOIN medication m ON m.id = r.medicationId WHERE r.enabled = 1 AND m.removedAt IS NULL")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    /** Reminders of medications in use: a removed medication's stay silent. */
    @Query("SELECT r.* FROM reminder r JOIN medication m ON m.id = r.medicationId WHERE m.removedAt IS NULL")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT r.* FROM reminder r JOIN medication m ON m.id = r.medicationId WHERE m.removedAt IS NULL")
    suspend fun active(): List<ReminderEntity>

    /** Every reminder, a removed medication's too, for a backup. */
    @Query("SELECT * FROM reminder")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE medicationId = :id")
    suspend fun ofMedication(id: Long): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminder")
    suspend fun deleteAll()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(meta: MetaEntity)
}
