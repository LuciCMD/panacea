package com.clementine.panacea.data.backup

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.data.legacy.Legacy34Mapper
import com.clementine.panacea.data.legacy.Legacy34Parser
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.sound.CustomSound
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.sound.SoundSetting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup holds, as database rows. Medication ids are 1, 2, 3 … in list order. */
data class BackupData(
    val medications: List<MedicationEntity>,
    val ingredients: List<IngredientEntity>,
    val doses: List<DoseEntity>,
    val reminders: List<ReminderEntity>,
    /** Null when the backup has none, so the phone's own are kept. */
    val amountPresets: List<Double>?,
    /** Null for a 3.4 backup, which had none. */
    val settings: BackupSettings?,
    /** When the backup was made (epoch ms), if it says. */
    val exportedAt: Long?,
    /** Written by Panacea 3, before photos, weights and learned reminders. */
    val legacy: Boolean,
    /** One plain sentence per thing that could not be read. */
    val problems: List<String> = emptyList(),
) {
    /** Photo file names the medications refer to. */
    val photos: Set<String> get() = medications.flatMap { listOfNotNull(it.photoFront, it.photoBack) }.toSet()

    /** Custom sound files the settings refer to. */
    val sounds: Set<String> get() = settings?.sounds?.values?.mapNotNull { it.custom?.file }?.toSet().orEmpty()
}

data class BackupSettings(
    /** A theme key, or "system"; null leaves the phone's choice. */
    val theme: String?,
    /** By sound event key. */
    val sounds: Map<String, SoundSetting>,
)

class BackupException(message: String) : Exception(message)

/**
 * Panacea's backup file, format version 3: JSON with every field, each medication carrying its
 * ingredients, doses and reminders. Reads it and the `panacea_backup.json` that 3.x wrote (which
 * says version 2, or nothing before 3.4).
 * Photos and sound files travel beside it in the backup's zip, by the names given here.
 */
object BackupFormat {
    const val VERSION = 3
    const val JSON_NAME = "backup.json"
    const val PHOTOS = "photos/"
    const val SOUNDS = "sounds/"

    fun write(data: BackupData, appVersion: String?, now: Long): String {
        val reminders = data.reminders.groupBy { it.medicationId }
        val ingredients = data.ingredients.groupBy { it.medicationId }
        val doses = data.doses.groupBy { it.medicationId }
        val root = JSONObject()
            .put("app", "Panacea")
            .put("backupVersion", VERSION)
            .put("appVersion", appVersion ?: JSONObject.NULL)
            .put("exportedAt", now)
            .put("medications", JSONArray(data.medications.sortedWith(compareBy({ it.sortOrder }, { it.name })).map { med ->
                medication(med)
                    .put("ingredients", JSONArray(ingredients[med.id].orEmpty().sortedBy { it.position }.map(::ingredient)))
                    .put("doses", JSONArray(doses[med.id].orEmpty().sortedBy { it.takenAt }.map(::dose)))
                    .put("reminders", JSONArray(reminders[med.id].orEmpty().sortedBy { it.id }.map(::reminder)))
            }))
        data.amountPresets?.let { root.put("amountPresets", JSONArray(it)) }
        data.settings?.let { s ->
            val sounds = JSONObject()
            s.sounds.forEach { (key, setting) ->
                sounds.put(key, JSONObject()
                    .put("mode", setting.mode.key)
                    .put("file", setting.custom?.file ?: JSONObject.NULL)
                    .put("name", setting.custom?.name ?: JSONObject.NULL))
            }
            root.put("settings", JSONObject().put("theme", s.theme ?: JSONObject.NULL).put("sounds", sounds))
        }
        return root.toString(1)
    }

    /** Reads a backup of either version. Throws [BackupException] when the file isn't one. */
    fun read(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupException("That file isn't a Panacea backup.")
        }
        val version = root.optInt("backupVersion", 1)
        if (version > VERSION) throw BackupException("This backup was made by a newer Panacea. Update the app, then restore it.")
        return if (version >= VERSION) readCurrent(root) else readLegacy(json, root)
    }

    private fun readLegacy(json: String, root: JSONObject): BackupData {
        if (root.optJSONArray("medications") == null) throw BackupException("That file isn't a Panacea backup.")
        val rows = Legacy34Mapper.map(Legacy34Parser.parseBackup(json))
        return BackupData(
            medications = rows.medications,
            ingredients = rows.ingredients,
            doses = rows.doses,
            reminders = rows.reminders,
            amountPresets = rows.amountPresets,
            settings = null,
            exportedAt = root.optLong("exportedAt").takeIf { it > 0 },
            legacy = true,
            problems = rows.problems,
        )
    }

    private fun readCurrent(root: JSONObject): BackupData {
        val meds = root.optJSONArray("medications") ?: throw BackupException("That file isn't a Panacea backup.")
        val problems = mutableListOf<String>()
        val medications = mutableListOf<MedicationEntity>()
        val ingredients = mutableListOf<IngredientEntity>()
        val doses = mutableListOf<DoseEntity>()
        val reminders = mutableListOf<ReminderEntity>()
        val names = HashSet<String>()
        val reminderIds = HashSet<Long>()

        for (i in 0 until meds.length()) {
            val m = meds.optJSONObject(i)
            val name = m?.optString("name")?.trim().orEmpty()
            if (m == null || name.isEmpty()) {
                problems += "Medication ${i + 1} in the backup could not be read."
                continue
            }
            if (!names.add(name)) {
                problems += "A second medication named \"$name\" was skipped."
                continue
            }
            val id = medications.size + 1L
            medications += MedicationEntity(
                id = id,
                name = name,
                dose = m.number("dose") ?: 0.0,
                doseUnit = m.text("doseUnit") ?: "mg",
                weight = m.number("weight"),
                weightUnit = WeightUnit.fromKey(m.text("weightUnit")).key,
                category = m.text("category") ?: "Uncategorized",
                type = m.text("type") ?: "UNSPECIFIED",
                sortOrder = medications.size,
                lastMultiplier = m.number("lastMultiplier")?.takeIf { it > 0 } ?: 1.0,
                learnRoutine = m.optBoolean("learnRoutine"),
                mutedUntil = m.optLong("mutedUntil"),
                photoFront = m.fileName("photoFront"),
                photoBack = m.fileName("photoBack"),
                routineAskedFor = m.optLong("routineAskedFor"),
            )
            m.objects("ingredients").forEachIndexed { position, g ->
                val ingredientName = g.text("name")
                if (ingredientName == null) {
                    problems += "An ingredient of \"$name\" could not be read."
                } else {
                    ingredients += IngredientEntity(medicationId = id, position = position, name = ingredientName, amount = g.number("amount") ?: 0.0, unit = g.text("unit") ?: "mg")
                }
            }
            var unreadable = 0
            m.objects("doses").forEach { d ->
                val takenAt = d.optLong("takenAt")
                val multiplier = d.number("multiplier")
                if (takenAt <= 0 || multiplier == null) {
                    unreadable++
                } else {
                    doses += DoseEntity(
                        medicationId = id,
                        takenAt = takenAt,
                        multiplier = multiplier,
                        amount = d.number("amount") ?: 0.0,
                        unit = d.text("unit") ?: medications.last().doseUnit,
                        weight = d.number("weight"),
                        weightUnit = d.text("weightUnit"),
                    )
                }
            }
            if (unreadable > 0) problems += "${if (unreadable == 1) "1 dose" else "$unreadable doses"} of \"$name\" could not be read."
            m.objects("reminders").forEach { r ->
                val repeat = r.text("repeat")?.let { key -> RepeatType.entries.firstOrNull { it.name == key } }
                if (repeat == null) {
                    problems += "A reminder for \"$name\" could not be read."
                    return@forEach
                }
                // Keep ids so a reminder stays the same reminder; 0 lets the database assign one.
                val reminderId = r.optLong("id").takeIf { it != 0L && reminderIds.add(it) } ?: 0L
                reminders += ReminderEntity(
                    id = reminderId,
                    medicationId = id,
                    repeat = repeat,
                    enabled = r.optBoolean("enabled", true),
                    daysMask = r.optInt("daysMask", 0x7F),
                    times = r.optJSONArray("times")?.let { t -> (0 until t.length()).map { t.optInt(it) } }.orEmpty(),
                    intervalHours = r.optInt("intervalHours", 1),
                    windowStart = r.optInt("windowStart"),
                    windowEnd = r.optInt("windowEnd"),
                    dayOfMonth = r.optInt("dayOfMonth", 1),
                    note = r.text("note").orEmpty(),
                    earlyWindowMinutes = if (r.isNull("earlyWindowMinutes")) null else r.optInt("earlyWindowMinutes"),
                    timesCompleted = r.optInt("timesCompleted"),
                    timesMissed = r.optInt("timesMissed"),
                    pending = r.optBoolean("pending"),
                    lastFiredAt = r.optLong("lastFiredAt"),
                    lastCompletedAt = r.optLong("lastCompletedAt"),
                )
            }
        }

        val settings = root.optJSONObject("settings")?.let { s ->
            val sounds = s.optJSONObject("sounds")
            BackupSettings(
                theme = s.text("theme"),
                sounds = sounds?.keys()?.asSequence()?.toList().orEmpty().mapNotNull { key ->
                    val o = sounds?.optJSONObject(key) ?: return@mapNotNull null
                    val file = o.fileName("file")
                    key to SoundSetting(SoundMode.fromKey(o.text("mode")), file?.let { CustomSound(it, o.text("name") ?: it) })
                }.toMap(),
            )
        }

        return BackupData(
            medications = medications,
            ingredients = ingredients,
            doses = doses,
            reminders = reminders,
            amountPresets = root.optJSONArray("amountPresets")?.let { a -> (0 until a.length()).map { a.optDouble(it) }.filter { it.isFinite() && it > 0 } },
            settings = settings,
            exportedAt = root.optLong("exportedAt").takeIf { it > 0 },
            legacy = false,
            problems = problems,
        )
    }

    /**
     * The rows as they go into the database: nothing is showing on this phone yet, and a medication
     * learning its routine starts from [now], so restoring never asks about a dose long past.
     */
    fun forRestore(data: BackupData, now: Long): BackupData = data.copy(
        medications = data.medications.map { if (it.learnRoutine) it.copy(routineAskedFor = maxOf(it.routineAskedFor, now)) else it },
        reminders = data.reminders.map { it.copy(pending = false) },
    )

    /** Whether [name] is a plain file name, safe to use inside the app's folders. */
    fun isSafeName(name: String) = name.length in 1..128 && !name.startsWith('.') && name.all { it.isLetterOrDigit() || it in "._-" }

    private fun medication(m: MedicationEntity) = JSONObject()
        .put("name", m.name)
        .put("dose", m.dose)
        .put("doseUnit", m.doseUnit)
        .put("weight", m.weight ?: JSONObject.NULL)
        .put("weightUnit", m.weightUnit)
        .put("category", m.category)
        .put("type", m.type)
        .put("lastMultiplier", m.lastMultiplier)
        .put("learnRoutine", m.learnRoutine)
        .put("mutedUntil", m.mutedUntil)
        .put("routineAskedFor", m.routineAskedFor)
        .put("photoFront", m.photoFront ?: JSONObject.NULL)
        .put("photoBack", m.photoBack ?: JSONObject.NULL)

    private fun ingredient(g: IngredientEntity) = JSONObject()
        .put("name", g.name)
        .put("amount", g.amount)
        .put("unit", g.unit)

    private fun dose(d: DoseEntity) = JSONObject()
        .put("takenAt", d.takenAt)
        .put("multiplier", d.multiplier)
        .put("amount", d.amount)
        .put("unit", d.unit)
        .put("weight", d.weight ?: JSONObject.NULL)
        .put("weightUnit", d.weightUnit ?: JSONObject.NULL)

    private fun reminder(r: ReminderEntity) = JSONObject()
        .put("id", r.id)
        .put("repeat", r.repeat.name)
        .put("enabled", r.enabled)
        .put("daysMask", r.daysMask)
        .put("times", JSONArray(r.times))
        .put("intervalHours", r.intervalHours)
        .put("windowStart", r.windowStart)
        .put("windowEnd", r.windowEnd)
        .put("dayOfMonth", r.dayOfMonth)
        .put("note", r.note)
        .put("earlyWindowMinutes", r.earlyWindowMinutes ?: JSONObject.NULL)
        .put("timesCompleted", r.timesCompleted)
        .put("timesMissed", r.timesMissed)
        .put("pending", r.pending)
        .put("lastFiredAt", r.lastFiredAt)
        .put("lastCompletedAt", r.lastCompletedAt)

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.number(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.fileName(key: String): String? = text(key)?.takeIf(::isSafeName)

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}
