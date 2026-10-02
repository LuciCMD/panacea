package com.clementine.panacea.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Units offered for a dose and each ingredient: the active amount. The same list as 3.4. */
val DoseUnits: List<String> = listOf(
    "mg", "mcg", "g", "mL", "IU", "mEq",
    "drop(s)", "spray(s)", "puff(s)", "tablet(s)", "capsule(s)", "unit(s)", "%",
)
const val DEFAULT_DOSE_UNIT = "mg"

/** Units for the physical weight of one pill or item. Keys are stored; never rename one. */
enum class WeightUnit(val key: String, val grams: Double) {
    MICROGRAM("mcg", 1e-6),
    MILLIGRAM("mg", 1e-3),
    GRAM("g", 1.0),
    KILOGRAM("kg", 1000.0),
    OUNCE("oz", 28.349523125),
    POUND("lb", 453.59237);

    /** [value] in this unit, expressed in [target]. Exact factors, so a unit change never drifts. */
    fun convert(value: Double, target: WeightUnit): Double =
        if (target == this) value else value * grams / target.grams

    companion object {
        val DEFAULT = GRAM

        fun fromKey(key: String?): WeightUnit = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** An amount for display: up to three decimals, no trailing zeros ("50", "0.25", "28.35"). */
fun formatAmount(value: Double): String {
    if (!value.isFinite()) return "?"
    return BigDecimal.valueOf(value)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
