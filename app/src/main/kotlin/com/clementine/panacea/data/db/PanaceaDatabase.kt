package com.clementine.panacea.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        MedicationEntity::class,
        IngredientEntity::class,
        DoseEntity::class,
        ReminderEntity::class,
        MetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // pill photos
    ],
)
@TypeConverters(Converters::class)
abstract class PanaceaDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun metaDao(): MetaDao

    companion object {
        fun build(context: Context): PanaceaDatabase =
            Room.databaseBuilder(context, PanaceaDatabase::class.java, "panacea.db").build()
    }
}

class Converters {
    @TypeConverter
    fun minutesToText(minutes: List<Int>): String = minutes.joinToString(",")

    @TypeConverter
    fun textToMinutes(text: String): List<Int> =
        if (text.isBlank()) emptyList() else text.split(',').mapNotNull { it.trim().toIntOrNull() }
}
