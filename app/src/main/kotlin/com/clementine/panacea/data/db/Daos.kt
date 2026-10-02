package com.clementine.panacea.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query(
        """
        SELECT m.*, (SELECT MAX(d.takenAt) FROM dose d WHERE d.medicationId = m.id) AS lastTakenAt
        FROM medication m
        ORDER BY m.sortOrder, m.name
        """
    )
    fun observeSummaries(): Flow<List<MedicationSummary>>

    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun get(id: Long): MedicationEntity?

    @Query("SELECT * FROM ingredient ORDER BY medicationId, position")
    fun observeIngredients(): Flow<List<IngredientEntity>>

    @Query("UPDATE medication SET lastMultiplier = :multiplier WHERE id = :id")
    suspend fun setLastMultiplier(id: Long, multiplier: Double)

    /** Puts back [previous] unless the amount was changed again since [current] was set. */
    @Query("UPDATE medication SET lastMultiplier = :previous WHERE id = :id AND lastMultiplier = :current")
    suspend fun restoreLastMultiplier(id: Long, current: Double, previous: Double)

    @Insert
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)
}

@Dao
interface DoseDao {
    @Insert
    suspend fun insert(dose: DoseEntity): Long

    @Query("DELETE FROM dose WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT d.*, m.name AS name, m.type AS type FROM dose d
        JOIN medication m ON m.id = d.medicationId
        ORDER BY d.takenAt DESC
        """
    )
    fun observeHistory(): Flow<List<DoseRow>>

    @Query("SELECT * FROM dose WHERE takenAt >= :since ORDER BY takenAt")
    fun observeSince(since: Long): Flow<List<DoseEntity>>

    @Insert
    suspend fun insertAll(doses: List<DoseEntity>)
}

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT r.*, m.name AS medicationName FROM reminder r
        JOIN medication m ON m.id = r.medicationId
        ORDER BY m.sortOrder, m.name, r.id
        """
    )
    fun observeRows(): Flow<List<ReminderRow>>

    @Query("UPDATE reminder SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM reminder WHERE enabled = 1")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)
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
