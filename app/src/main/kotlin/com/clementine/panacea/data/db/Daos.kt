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

    @Insert
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)
}

@Dao
interface DoseDao {
    @Insert
    suspend fun insertAll(doses: List<DoseEntity>)
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: MetaEntity)
}
