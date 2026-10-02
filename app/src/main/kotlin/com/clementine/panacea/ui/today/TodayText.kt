package com.clementine.panacea.ui.today

import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.model.formatAmount
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The words on a Today card, kept apart from Compose so they can be tested. */
object TodayText {

    /** "50 mg", or "50 mg · weighs 0.2 g" once a weight is set. */
    fun doseLine(med: MedicationEntity): String {
        val dose = "${formatAmount(med.dose)} ${med.doseUnit}"
        val weight = med.weight ?: return dose
        return "$dose · weighs ${formatAmount(weight)} ${WeightUnit.fromKey(med.weightUnit).key}"
    }

    fun lastTaken(lastTakenAt: Long?, now: ZonedDateTime, time: DateTimeFormatter, date: DateTimeFormatter): String {
        if (lastTakenAt == null) return "Not taken yet"
        val taken = Instant.ofEpochMilli(lastTakenAt).atZone(now.zone)
        val days = ChronoUnit.DAYS.between(taken.toLocalDate(), now.toLocalDate())
        return when (days) {
            0L -> "Taken at ${taken.format(time)}"
            1L -> "Last taken yesterday at ${taken.format(time)}"
            else -> "Last taken ${taken.format(date)} at ${taken.format(time)}"
        }
    }
}
