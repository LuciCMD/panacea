package com.clementine.panacea.data.legacy

import com.clementine.panacea.model.Category
import com.clementine.panacea.model.DEFAULT_DOSE_UNIT
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.RepeatType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything 3.4 stored, read as it was. */
data class LegacySnapshot(
    val medications: List<LegacyMedication>,
    val order: List<String>,
    val reminders: List<LegacyReminder>,
    /** Saved amount presets; null when 3.4 never saved any. */
    val amountPresets: List<Double>?,
    /** The multiplier each medication used last, by name. */
    val lastAmounts: Map<String, Double>,
    /** One plain sentence per thing that could not be read. */
    val problems: List<String>,
)

data class LegacyMedication(
    val name: String,
    val dose: Double,
    val unit: String,
    val category: String,
    val type: String,
    val ingredients: List<LegacyIngredient>,
    val doses: List<LegacyDose>,
)

data class LegacyIngredient(val name: String, val amount: Double, val unit: String)

data class LegacyDose(val takenAt: Long, val amount: Double, val fraction: Double)

data class LegacyReminder(
    val id: Int?,
    val medicationName: String,
    val repeat: RepeatType,
    val enabled: Boolean,
    val daysMask: Int,
    val times: List<Int>,
    val intervalHours: Int,
    val windowStart: Int,
    val windowEnd: Int,
    val dayOfMonth: Int,
    val note: String,
    val timesCompleted: Int,
    val timesMissed: Int,
    val pending: Boolean,
    val lastFiredAt: Long,
    val lastCompletedAt: Long,
)

/**
 * Reads 3.4's SharedPreferences JSON and its backup files, applying every migration 3.4 applied on
 * load (legacy secondary drug, missing dose fraction, pre-3.2 single-time reminders).
 *
 * Unlike 3.4, one unreadable item never hides the rest: it is skipped and named in [LegacySnapshot.problems].
 */
object Legacy34Parser {
    const val MAX_INGREDIENTS = 10
    private const val MAX_TIMES = 12
    private const val EVERY_DAY = 0x7F
    private const val DEFAULT_TIME = 8 * 60

    fun parsePrefs(
        medicationData: String?,
        order: String?,
        reminders: String?,
        amountPresets: String?,
        lastAmounts: String?,
    ): LegacySnapshot {
        val problems = mutableListOf<String>()
        return LegacySnapshot(
            medications = parseMedicationMap(medicationData, problems),
            order = parseNames(order, problems),
            reminders = parseReminders(reminders?.let { readArray(it, "Reminders", problems) }, problems),
            amountPresets = amountPresets?.let { parsePresets(it, problems) },
            lastAmounts = lastAmounts?.let { parseLastAmounts(it, problems) }.orEmpty(),
            problems = problems,
        )
    }

    /** A `panacea_backup.json` written by 3.x. Throws [JSONException] when it isn't one. */
    fun parseBackup(json: String): LegacySnapshot {
        val root = JSONObject(json)
        val meds = root.optJSONArray("medications")
            ?: throw JSONException("Not a Panacea backup: it has no medications")
        val problems = mutableListOf<String>()
        val medications = (0 until meds.length()).mapNotNull { i ->
            val med = meds.optJSONObject(i)
            if (med == null) problems += "Medication ${i + 1} in the backup could not be read."
            med?.let { parseMedication(it, problems) }
        }
        val order = root.optJSONArray("order")
        return LegacySnapshot(
            medications = medications,
            order = if (order == null) emptyList() else (0 until order.length()).mapNotNull { order.optString(it, null) },
            reminders = parseReminders(root.optJSONArray("reminders"), problems),
            amountPresets = null,
            lastAmounts = emptyMap(),
            problems = problems,
        )
    }

    private fun parseMedicationMap(json: String?, problems: MutableList<String>): List<LegacyMedication> {
        if (json.isNullOrBlank()) return emptyList()
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            problems += "The medication list could not be read (${e.message})."
            return emptyList()
        }
        return root.keys().asSequence().toList().mapNotNull { key ->
            val med = root.optJSONObject(key)
            if (med == null) problems += "Medication \"$key\" could not be read."
            med?.let { parseMedication(it, problems) }
        }
    }

    private fun parseMedication(json: JSONObject, problems: MutableList<String>): LegacyMedication? {
        val name = json.optString("name", "").trim()
        if (name.isEmpty()) {
            problems += "A medication without a name was skipped."
            return null
        }
        val dose = json.optDouble("dosage", 0.0).takeIf { it.isFinite() } ?: 0.0
        val doses = parseHistory(name, json.optJSONArray("usageHistory"), problems)
            .map { record ->
                if (!record.fraction.isNaN()) record
                else record.copy(fraction = if (dose > 0) record.amount / dose else 1.0)
            }
            .sortedBy { it.takenAt }
        return LegacyMedication(
            name = name,
            dose = dose,
            unit = json.optString("unit", "").ifBlank { DEFAULT_DOSE_UNIT },
            category = json.optString("category", "").ifBlank { Category.UNCATEGORIZED.key },
            type = json.optString("type", "").ifBlank { MedicationType.UNSPECIFIED.key },
            ingredients = parseIngredients(json),
            doses = doses,
        )
    }

    private fun parseIngredients(json: JSONObject): List<LegacyIngredient> {
        val array = json.optJSONArray("ingredients")
        if (array == null) {
            val legacyName = json.optString("secondaryDrugName", "").trim()
            if (legacyName.isEmpty()) return emptyList()
            return listOf(LegacyIngredient(legacyName, json.optDouble("secondaryDrugDosage", 0.0), DEFAULT_DOSE_UNIT))
        }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .map {
                LegacyIngredient(
                    name = it.optString("name", "").trim(),
                    amount = it.optDouble("dosage", 0.0),
                    unit = it.optString("unit", "").ifBlank { DEFAULT_DOSE_UNIT },
                )
            }
            .filter { it.name.isNotEmpty() }
            .take(MAX_INGREDIENTS)
    }

    /** Fractions missing in pre-3.1 records come back as NaN, filled in by [parseMedication]. */
    private fun parseHistory(name: String, array: JSONArray?, problems: MutableList<String>): List<LegacyDose> {
        if (array == null) return emptyList()
        var unreadable = 0
        val doses = (0 until array.length()).mapNotNull { i ->
            try {
                val record = array.getJSONObject(i)
                LegacyDose(
                    takenAt = record.getLong("timeTaken"),
                    amount = record.getDouble("dosageTaken"),
                    fraction = if (record.has("fraction")) record.getDouble("fraction") else Double.NaN,
                )
            } catch (e: JSONException) {
                unreadable++
                null
            }
        }
        if (unreadable > 0) problems += "$unreadable dose record(s) of \"$name\" could not be read."
        return doses
    }

    private fun parseReminders(array: JSONArray?, problems: MutableList<String>): List<LegacyReminder> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                parseReminder(array.getJSONObject(i))
            } catch (e: JSONException) {
                problems += "Reminder ${i + 1} could not be read."
                null
            }
        }
    }

    private fun parseReminder(json: JSONObject): LegacyReminder {
        val times = mutableListOf<Int>()
        val array = json.optJSONArray("times")
        if (array != null) {
            for (i in 0 until minOf(array.length(), MAX_TIMES)) {
                val t = array.getJSONObject(i)
                times += t.getInt("h") * 60 + t.getInt("m")
            }
        } else if (json.has("hour")) {
            // Pre-3.2: one daily time
            times += json.getInt("hour") * 60 + json.optInt("minute", 0)
        }
        if (times.isEmpty()) times += DEFAULT_TIME
        val mask = json.optInt("daysMask", EVERY_DAY) and EVERY_DAY
        return LegacyReminder(
            id = if (json.has("id")) json.getInt("id") else null,
            medicationName = json.getString("medicationName").trim(),
            repeat = RepeatType.entries.firstOrNull { it.name == json.optString("repeatType", "") } ?: RepeatType.DAILY,
            enabled = json.optBoolean("enabled", true),
            daysMask = if (mask == 0) EVERY_DAY else mask,
            times = times,
            intervalHours = maxOf(1, json.optInt("intervalHours", 4)),
            windowStart = json.optInt("startHour", 8) * 60 + json.optInt("startMinute", 0),
            windowEnd = json.optInt("endHour", 22) * 60 + json.optInt("endMinute", 0),
            dayOfMonth = json.optInt("dayOfMonth", 1).coerceIn(1, 31),
            note = json.optString("label", ""),
            timesCompleted = json.optInt("timesCompleted", 0),
            timesMissed = json.optInt("timesMissed", 0),
            pending = json.optBoolean("pending", false),
            lastFiredAt = json.optLong("lastFiredAt", 0),
            lastCompletedAt = json.optLong("lastCompletedAt", 0),
        )
    }

    private fun parseNames(json: String?, problems: MutableList<String>): List<String> {
        val array = json?.let { readArray(it, "The medication order", problems) } ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, null) }
    }

    private fun parsePresets(json: String, problems: MutableList<String>): List<Double>? {
        val array = readArray(json, "Amount presets", problems) ?: return null
        return (0 until array.length()).map { array.optDouble(it) }.filter { it.isFinite() && it > 0 }
    }

    private fun parseLastAmounts(json: String, problems: MutableList<String>): Map<String, Double> {
        val obj = try {
            JSONObject(json)
        } catch (e: JSONException) {
            problems += "Remembered amounts could not be read."
            return emptyMap()
        }
        return obj.keys().asSequence()
            .associateWith { obj.optDouble(it) }
            .filterValues { it.isFinite() && it > 0 }
    }

    private fun readArray(json: String, what: String, problems: MutableList<String>): JSONArray? =
        try {
            JSONArray(json)
        } catch (e: JSONException) {
            problems += "$what could not be read."
            null
        }
}
