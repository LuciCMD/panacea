package com.clementine.panacea.data.legacy

import android.content.Context
import androidx.room.withTransaction
import com.clementine.panacea.data.db.MetaEntity
import com.clementine.panacea.data.db.PanaceaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportOutcome {
    data object AlreadyDone : ImportOutcome
    data class Imported(
        val medications: Int,
        val doses: Int,
        val reminders: Int,
        val problems: List<String>,
    ) : ImportOutcome
}

/**
 * Brings a 3.4 install's data into the database, once. Everything is written in one transaction
 * with the marker that says it happened, so a crash midway leaves nothing half-imported and the
 * next start tries again. The 3.4 files are only read, never changed or deleted.
 */
class Legacy34Importer(private val context: Context, private val db: PanaceaDatabase) {

    suspend fun importIfNeeded(): ImportOutcome = withContext(Dispatchers.IO) {
        if (db.metaDao().get(KEY_DONE) != null) return@withContext ImportOutcome.AlreadyDone

        val rows = Legacy34Mapper.map(
            Legacy34Parser.parsePrefs(
                medicationData = read("MedTracker", "medication_data"),
                order = read("MedTrackerOrder", "medication_order"),
                reminders = read("ReminderPrefs", "medication_reminders"),
                amountPresets = read("AmountPrefs", "presets"),
                lastAmounts = read("AmountPrefs", "last_amounts"),
            )
        )
        val imported = db.withTransaction {
            val meta = db.metaDao()
            if (meta.get(KEY_DONE) != null) return@withTransaction false
            db.medicationDao().insertAll(rows.medications)
            db.medicationDao().insertIngredients(rows.ingredients)
            db.doseDao().insertAll(rows.doses)
            db.reminderDao().insertAll(rows.reminders)
            rows.amountPresets?.let { meta.put(MetaEntity(KEY_AMOUNT_PRESETS, it.joinToString(","))) }
            if (rows.problems.isNotEmpty()) meta.put(MetaEntity(KEY_PROBLEMS, rows.problems.joinToString("\n")))
            meta.put(
                MetaEntity(
                    KEY_DONE,
                    "${System.currentTimeMillis()}: ${rows.medications.size} medications, " +
                        "${rows.doses.size} doses, ${rows.reminders.size} reminders",
                )
            )
            true
        }
        if (!imported) return@withContext ImportOutcome.AlreadyDone
        ImportOutcome.Imported(rows.medications.size, rows.doses.size, rows.reminders.size, rows.problems)
    }

    private fun read(file: String, key: String): String? =
        context.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, null)

    companion object {
        const val KEY_DONE = "legacy34.imported"
        const val KEY_PROBLEMS = "legacy34.problems"
        const val KEY_AMOUNT_PRESETS = "amount.presets"
    }
}
