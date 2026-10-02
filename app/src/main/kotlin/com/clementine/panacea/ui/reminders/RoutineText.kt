package com.clementine.panacea.ui.reminders

import com.clementine.panacea.reminder.Routine
import com.clementine.panacea.reminder.Routines
import com.clementine.panacea.ui.TimeFormats
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A learned routine in plain words, for the medication's page, the Reminders tab and the notification. */
object RoutineText {
    /** "around 9:00", "around 9:00 and 21:00", "about every 6 hours"; null until something is learned. */
    fun usually(routine: Routine, f: TimeFormats): String? = when (routine) {
        is Routine.TimesOfDay -> "around " + ReminderText.natural(routine.slots.map { clock(it.minute, f) })
        is Routine.SteadyGap -> every(routine.gapMinutes)
        else -> null
    }

    /** What the page says about it; [on] is whether learned reminders are turned on. */
    fun summary(routine: Routine, on: Boolean, nextAsk: ZonedDateTime?, now: ZonedDateTime, f: TimeFormats): String {
        val usually = usually(routine, f)
        val ask = when {
            !on -> "Turn this on to be asked when a dose seems to be missing."
            routine is Routine.TimesOfDay -> {
                val by = ReminderText.natural(routine.slots.map { clock(it.minute + Routines.margin(it.spread), f) }, "or")
                "If nothing is logged by $by, Panacea asks whether you took it."
            }
            nextAsk != null -> "If nothing is logged by ${moment(nextAsk, now, f)}, Panacea asks whether you took it."
            else -> "It asks again once the next dose is logged."
        }
        return when (routine) {
            is Routine.Learning -> {
                val doses = "${routine.doses} of ${Routines.MIN_DOSES} doses"
                "Still learning: $doses so far, on ${plural(routine.days, "day")}. " +
                    "It needs ${Routines.MIN_DOSES} doses over at least ${Routines.MIN_DAYS} days in the last 4 weeks."
            }
            is Routine.Irregular -> "Your doses don't follow a steady pattern yet, so Panacea won't ask for now."
            is Routine.TimesOfDay -> {
                val spread = routine.slots.singleOrNull()?.let { ", give or take ${minutes(it.spread)}" }.orEmpty()
                "You usually take it $usually$spread. $ask"
            }
            is Routine.SteadyGap -> "You usually take it $usually, give or take ${minutes(routine.spread)}. $ask"
        }
    }

    /** "Times of day · learned from 14 doses"; null until something is learned. */
    fun basis(routine: Routine): String? = when (routine) {
        is Routine.TimesOfDay -> "Times of day · learned from ${routine.doses} doses"
        is Routine.SteadyGap -> "Steady gap · learned from ${routine.doses} doses"
        else -> null
    }

    /** The Reminders tab's two lines: what it learned, and when it asks next. */
    fun card(routine: Routine, nextAsk: ZonedDateTime?, now: ZonedDateTime, f: TimeFormats): Pair<String, String> = when (routine) {
        is Routine.Learning -> "Still learning" to "${routine.doses} of ${Routines.MIN_DOSES} doses so far"
        is Routine.Irregular -> "No steady pattern yet" to "Won't ask for now"
        else -> "Usually ${usually(routine, f)}" to
            (nextAsk?.let { "Asks at ${moment(it, now, f)} if nothing is logged" } ?: "Asks again after the next dose")
    }

    fun question(name: String) = "Did you take $name?"

    /** "You usually take it around 9:00. Last logged yesterday at 9:04." */
    fun notification(routine: Routine, lastLogged: Long?, now: ZonedDateTime, f: TimeFormats): String {
        val usually = usually(routine, f)?.let { "You usually take it $it." }
        val last = lastLogged?.let {
            val at = Instant.ofEpochMilli(it).atZone(now.zone)
            "Last logged " + when (ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())) {
                0L -> "at ${at.format(f.time)}."
                1L -> "yesterday at ${at.format(f.time)}."
                else -> "${at.format(f.weekday)} at ${at.format(f.time)}."
            }
        } ?: "Nothing is logged yet."
        return listOfNotNull(usually, last).joinToString(" ")
    }

    /** "about every 6 hours", "about every 4½ hours", "about once a day", "about every 2 days". */
    fun every(minutes: Int): String {
        val hours = minutes / 60.0
        return when {
            minutes in 23 * 60..25 * 60 -> "about once a day"
            minutes > 25 * 60 -> "about every ${(hours / 24).roundToInt()} days"
            minutes >= 10 * 60 -> "about every ${hours.roundToInt()} hours"
            else -> {
                val halves = (hours * 2).roundToInt().coerceAtLeast(2)
                val text = if (halves % 2 == 0) "${halves / 2}" else "${halves / 2}½"
                if (halves == 2) "about every hour" else "about every $text hours"
            }
        }
    }

    private fun minutes(m: Int) = when {
        m < 60 -> "$m minutes"
        m % 60 == 0 -> plural(m / 60, "hour")
        else -> "${m / 60} h ${m % 60} min"
    }

    private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    private fun clock(minute: Int, f: TimeFormats): String {
        val m = Math.floorMod(minute, 24 * 60)
        return LocalTime.of(m / 60, m % 60).format(f.time)
    }

    /** "9:40", "9:40 tomorrow" or "Monday at 9:40". */
    private fun moment(at: ZonedDateTime, now: ZonedDateTime, f: TimeFormats): String =
        when (ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())) {
            0L -> at.format(f.time)
            1L -> "${at.format(f.time)} tomorrow"
            else -> "${at.format(f.weekday)} at ${at.format(f.time)}"
        }
}
