package com.clementine.panacea.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.model.WeightUnit

@Entity(tableName = "medication", indices = [Index(value = ["name"], unique = true)])
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Active amount in one pill or item, in [doseUnit]. */
    val dose: Double,
    val doseUnit: String,
    /** Physical mass of one pill or item in [weightUnit]; null when not known. */
    val weight: Double? = null,
    val weightUnit: String = WeightUnit.DEFAULT.key,
    /** A [com.clementine.panacea.model.Category] key. */
    val category: String,
    /** A [com.clementine.panacea.model.MedicationType] key. */
    val type: String,
    val sortOrder: Int,
    /** The amount multiplier used last, offered again next time. */
    val lastMultiplier: Double = 1.0,
    val learnRoutine: Boolean = false,
    /** Reminders for this medication stay quiet until this time (epoch ms). */
    val mutedUntil: Long = 0,
    /** Photos of the pill's two sides, as file names in the app's photo folder. Added in version 2. */
    val photoFront: String? = null,
    val photoBack: String? = null,
)

@Entity(
    tableName = "ingredient",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("medicationId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val position: Int,
    val name: String,
    /** Amount in one pill or item; scales with each dose's multiplier. */
    val amount: Double,
    val unit: String,
)

@Entity(
    tableName = "dose",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["medicationId", "takenAt"]), Index("takenAt")],
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Epoch ms. */
    val takenAt: Long,
    val multiplier: Double,
    /** Active amount taken (dose × multiplier) in [unit], as the medication was at the time. */
    val amount: Double,
    val unit: String,
    /** Physical mass taken (weight × multiplier) in [weightUnit]; null when the weight wasn't known. */
    val weight: Double? = null,
    val weightUnit: String? = null,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("medicationId")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val repeat: RepeatType,
    val enabled: Boolean,
    /** Bit 0 = Sunday … bit 6 = Saturday. */
    val daysMask: Int,
    /** Minutes after midnight, for daily, weekly and monthly reminders. */
    val times: List<Int>,
    val intervalHours: Int,
    /** Minutes after midnight bounding an hourly reminder. */
    val windowStart: Int,
    val windowEnd: Int,
    val dayOfMonth: Int,
    val note: String,
    /** How early a dose may be logged and still count for the next reminder; null = the default rule. */
    val earlyWindowMinutes: Int? = null,
    val timesCompleted: Int = 0,
    val timesMissed: Int = 0,
    val pending: Boolean = false,
    val lastFiredAt: Long = 0,
    val lastCompletedAt: Long = 0,
)

/** Small app-wide values, written in the same transactions as the data they describe. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** A dose with the medication it belongs to, for the history list. */
data class DoseRow(
    @Embedded val dose: DoseEntity,
    val name: String,
    val type: String,
)

data class ReminderRow(
    @Embedded val reminder: ReminderEntity,
    val medicationName: String,
)

data class MedicationName(val id: Long, val name: String)

/** What goes with a medication when it's removed. */
data class MedicationCounts(val doses: Int, val reminders: Int)

data class MedicationSummary(
    @Embedded val medication: MedicationEntity,
    val lastTakenAt: Long?,
)
