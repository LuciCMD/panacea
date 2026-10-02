package com.clementine.panacea.model

import java.math.BigDecimal
import java.math.RoundingMode

/** The amount multiplier: how many pills or items one dose is. */
object Amounts {
    val DEFAULT_PRESETS = listOf(0.25, 0.5, 1.0, 2.0)
    const val MAX_PRESETS = 12
    const val MIN = 0.25
    const val MAX = 20.0

    /** Presets as stored ("0.25,0.5,1"); the defaults when none were ever saved. */
    fun parsePresets(csv: String?): List<Double> {
        if (csv == null) return DEFAULT_PRESETS
        return csv.split(',').mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 && it.isFinite() }.distinct().sorted()
    }

    fun withPreset(presets: List<Double>, value: Double): List<Double> {
        val v = round(value)
        if (v <= 0 || presets.any { it == v } || presets.size >= MAX_PRESETS) return presets
        return (presets + v).sorted()
    }

    /** Half steps, quarter steps at half and below. */
    fun more(m: Double): Double = round(if (m < 0.5) m + 0.25 else m + 0.5).coerceAtMost(MAX)

    fun less(m: Double): Double = round(if (m > 0.5) m - 0.5 else m - 0.25).coerceAtLeast(MIN)

    fun round(m: Double): Double =
        if (m.isFinite()) BigDecimal.valueOf(m).setScale(3, RoundingMode.HALF_UP).toDouble() else 1.0
}
