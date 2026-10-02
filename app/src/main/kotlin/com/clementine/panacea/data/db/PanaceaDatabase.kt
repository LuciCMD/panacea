package com.clementine.panacea.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MedicationEntity::class,
        IngredientEntity::class,
        DoseEntity::class,
        ReminderEntity::class,
        MetaEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // pill photos
        AutoMigration(from = 2, to = 3), // learned reminders
        AutoMigration(from = 3, to = 4, spec = TakenIngredients::class), // ingredients kept with each dose
        AutoMigration(from = 4, to = 5), // recently removed
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

    /** One ingredient per line: name, amount and unit, tab-separated (a tab or newline in a name becomes a space). */
    @TypeConverter
    fun takenToText(taken: List<TakenIngredient>): String =
        taken.joinToString("\n") { listOf(clean(it.name), it.amount.toString(), clean(it.unit)).joinToString("\t") }

    @TypeConverter
    fun textToTaken(text: String): List<TakenIngredient> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val amount = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        TakenIngredient(parts[0], amount, parts.getOrElse(2) { "" })
    }.toList()

    private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}

/**
 * Doses logged before version 4 take their medication's ingredients as they are now: the best record
 * there is, since only the multiplier was kept.
 */
class TakenIngredients : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val byMedication = HashMap<Long, MutableList<IngredientEntity>>()
        db.query("SELECT medicationId, position, name, amount, unit FROM ingredient").use { c ->
            while (c.moveToNext()) {
                byMedication.getOrPut(c.getLong(0)) { mutableListOf() } +=
                    IngredientEntity(medicationId = c.getLong(0), position = c.getInt(1), name = c.getString(2), amount = c.getDouble(3), unit = c.getString(4))
            }
        }
        if (byMedication.isEmpty()) return
        val converters = Converters()
        val filled = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, medicationId, multiplier FROM dose").use { c ->
            while (c.moveToNext()) {
                val ingredients = byMedication[c.getLong(1)] ?: continue
                val text = converters.takenToText(TakenIngredient.of(ingredients, c.getDouble(2)))
                if (text.isNotEmpty()) filled += c.getLong(0) to text
            }
        }
        filled.forEach { (id, text) -> db.execSQL("UPDATE dose SET ingredients = ? WHERE id = ?", arrayOf<Any>(text, id)) }
    }
}
