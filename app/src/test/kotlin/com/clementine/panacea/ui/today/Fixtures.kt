package com.clementine.panacea.ui.today

import com.clementine.panacea.ui.TimeFormats
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A fixed moment and English 24-hour formats, so wording tests read like the screen. */
internal object Fixtures {
    val zone: ZoneId = ZoneId.of("Europe/Helsinki")

    /** Friday, 2 October 2026, 13:16. */
    val now: ZonedDateTime = at(10, 2, 13, 16)

    val formats = TimeFormats(
        time = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH),
        shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH),
        weekday = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH),
        longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH),
        locale = Locale.ENGLISH,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    fun at(month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    fun ms(month: Int, day: Int, hour: Int, minute: Int): Long = at(month, day, hour, minute).toInstant().toEpochMilli()
}
