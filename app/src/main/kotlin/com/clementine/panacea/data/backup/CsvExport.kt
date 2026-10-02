package com.clementine.panacea.data.backup

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.model.formatAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Every dose as one row of a spreadsheet, oldest first. Dates and times are ISO so any spreadsheet
 * reads them. A report only: it can't be restored.
 */
object CsvExport {
    val HEADER = listOf("Date", "Time", "Medication", "Amount", "Unit", "Multiplier", "Weight", "Weight Unit", "Ingredients", "Day Total")

    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun write(medications: List<MedicationEntity>, ingredients: List<IngredientEntity>, doses: List<DoseEntity>, zone: ZoneId): String {
        val byId = medications.associateBy { it.id }
        val ingredientsOf = ingredients.groupBy { it.medicationId }.mapValues { (_, list) -> list.sortedBy { it.position } }
        // The running total of a medication on one day, in one unit.
        val totals = HashMap<Triple<Long, LocalDate, String>, Double>()
        val out = StringBuilder()
        // Excel needs the byte order mark to read UTF-8 names correctly.
        out.append('﻿').append(HEADER.joinToString(",")).append("\r\n")
        for (d in doses.sortedBy { it.takenAt }) {
            val med = byId[d.medicationId] ?: continue
            val at = Instant.ofEpochMilli(d.takenAt).atZone(zone)
            val key = Triple(med.id, at.toLocalDate(), d.unit)
            val total = (totals[key] ?: 0.0) + d.amount
            totals[key] = total
            val row = listOf(
                at.format(DATE),
                at.format(TIME),
                med.name,
                formatAmount(d.amount),
                d.unit,
                formatAmount(d.multiplier),
                d.weight?.let(::formatAmount).orEmpty(),
                if (d.weight != null) d.weightUnit.orEmpty() else "",
                ingredientsOf[med.id].orEmpty().joinToString("; ") { "${it.name} ${formatAmount(it.amount * d.multiplier)} ${it.unit}" },
                "${formatAmount(total)} ${d.unit}",
            )
            out.append(row.joinToString(",", transform = ::cell)).append("\r\n")
        }
        return out.toString()
    }

    /** Quotes a value holding a comma, quote or line break; a leading = + - @ is defused so no spreadsheet runs it. */
    fun cell(value: String): String {
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@') && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
