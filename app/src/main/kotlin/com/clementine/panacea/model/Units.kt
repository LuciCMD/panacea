package com.clementine.panacea.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.math.RoundingMode
import java.util.Locale

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

/**
 * An amount for the screen: up to three decimals, no trailing zeros, grouped the phone's way
 * ("50", "0.25", "1,050"). Not for fields or files; those take [plainAmount].
 */
fun formatAmount(value: Double, locale: Locale = Locale.getDefault()): String {
    if (!value.isFinite()) return "?"
    val format = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = true
    }
    return format.format(BigDecimal.valueOf(value))
}

/** An amount to edit or to write to a file: "1050", "0.25", with no grouping, so it reads back as it is. */
fun plainAmount(value: Double): String {
    if (!value.isFinite()) return "?"
    return BigDecimal.valueOf(value)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/**
 * A number as people type it: "0.5", "0,5", " 50 ", "1,000" or "1.000,5". A single comma or point is the
 * decimal separator, unless it splits off thousands the way the phone groups them ("1,000" in English,
 * "1.000" in German). With both, the last one is the decimal separator. Null if it isn't a number, or
 * is below zero.
 */
fun parseAmount(text: String, locale: Locale = Locale.getDefault()): Double? {
    // Spaces, no-break spaces and apostrophes are thousands separators somewhere.
    val t = text.trim().filterNot { it.code in SEPARATOR_CODES }
    val group = DecimalFormatSymbols.getInstance(locale).groupingSeparator
    val thousands = Regex("""[1-9]\d{0,2}(${Regex.escape(group.toString())}\d{3})+""")
    val normal = when {
        ',' in t && '.' in t -> {
            val decimal = if (t.lastIndexOf(',') > t.lastIndexOf('.')) ',' else '.'
            t.filterNot { it == (if (decimal == ',') '.' else ',') }.replace(decimal, '.')
        }
        (',' in t || '.' in t) && thousands.matches(t) -> t.filterNot { it == group }
        else -> t.replace(',', '.')
    }
    return normal.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

private val SEPARATOR_CODES = setOf(0x20, 0xA0, 0x202F, 0x27)
