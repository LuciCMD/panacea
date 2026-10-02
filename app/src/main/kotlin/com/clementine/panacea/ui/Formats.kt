package com.clementine.panacea.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Patterns built once from the phone's language and 12/24-hour setting. */
data class TimeFormats(
    val time: DateTimeFormatter,
    /** "28 Sep" */
    val shortDate: DateTimeFormatter,
    /** "Monday" */
    val weekday: DateTimeFormatter,
    /** "Friday, 2 October" */
    val longDate: DateTimeFormatter,
    val locale: Locale = Locale.getDefault(),
    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek,
)

fun timeFormats(context: Context): TimeFormats {
    val locale = Locale.getDefault()
    fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return TimeFormats(
        time = pattern(if (DateFormat.is24HourFormat(context)) "Hmm" else "hmma"),
        shortDate = pattern("dMMM"),
        weekday = pattern("EEEE"),
        longDate = pattern("EEEEdMMMM"),
        locale = locale,
    )
}
