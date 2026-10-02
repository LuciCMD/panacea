package com.clementine.panacea.reminder

import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.formatAmount
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** What to do about a reminder that just came due. */
enum class FireAction {
    /** Show the notification. */
    NOTIFY,

    /** Muted: keep it pending and show it when the mute ends. */
    HOLD,

    /** A dose already counts for it, or it's been dealt with; say nothing. */
    QUIET,
}

data class Fired(val reminder: ReminderEntity, val action: FireAction)

/**
 * The reminder rules, apart from Android so they can be tested. A reminder is *pending* from the
 * moment it fires until a dose counts for it; one still pending when it fires again was missed.
 */
object ReminderEngine {
    /** Reminders missed while the phone was off are still shown if they came due this recently. */
    val CATCH_UP: Duration = Duration.ofHours(6)

    /** A held reminder older than this, or from an earlier day, isn't worth showing when a mute ends. */
    private val RELEASE_WITHIN = Duration.ofHours(12)

    /**
     * [r] came due at [at] (or its snooze ended, if [snoozed]). [doseTimes] are the medication's
     * recent doses, [mutedUntil] the later of its own mute and Mute All.
     */
    fun fire(r: ReminderEntity, at: Long, doseTimes: List<Long>, mutedUntil: Long, now: Long, snoozed: Boolean): Fired {
        if (!r.enabled) return Fired(r, FireAction.QUIET)
        val covered = covered(r, at, doseTimes, now)
        val muted = mutedUntil > now
        if (snoozed) {
            // The snoozed reminder may have been dealt with in the meantime.
            if (!r.pending) return Fired(r, FireAction.QUIET)
            if (covered) return Fired(completed(r, now), FireAction.QUIET)
            return Fired(r, if (muted) FireAction.HOLD else FireAction.NOTIFY)
        }
        val missedBefore = if (r.pending) r.timesMissed + 1 else r.timesMissed
        val fired = r.copy(timesMissed = missedBefore, pending = true, lastFiredAt = at)
        if (covered) return Fired(completed(fired, now), FireAction.QUIET)
        return Fired(fired, if (muted) FireAction.HOLD else FireAction.NOTIFY)
    }

    /** A dose was logged at [takenAt]: true if it counts for [r]'s pending reminder. */
    fun countsFor(r: ReminderEntity, takenAt: Long): Boolean {
        if (!r.pending) return false
        return takenAt >= r.lastFiredAt - Schedule.earlyWindowMinutes(r) * 60_000L
    }

    fun completed(r: ReminderEntity, now: Long) =
        r.copy(pending = false, timesCompleted = r.timesCompleted + 1, lastCompletedAt = now)

    /**
     * The dose that settled [r] was undone: [before] as it was, due again, or null when [r] has moved
     * on since (it fired again, or was edited), so the undo has nothing to put back.
     */
    fun reopened(r: ReminderEntity, before: ReminderEntity): ReminderEntity? =
        before.takeIf { r == completed(before, r.lastCompletedAt) }

    /** A mute ended: whether [r], held while it lasted, should be shown now. */
    fun showOnRelease(r: ReminderEntity, now: ZonedDateTime): Boolean {
        if (!r.enabled || !r.pending) return false
        val firedAt = Instant.ofEpochMilli(r.lastFiredAt).atZone(now.zone)
        // "Until Tomorrow" means not today: yesterday's reminders don't all come out at midnight.
        return firedAt.toLocalDate() == now.toLocalDate() && Duration.between(firedAt, now) <= RELEASE_WITHIN
    }

    /** The reminder time [r] missed while no alarm could fire (phone off, app stopped), if recent. */
    fun missedWhileAway(r: ReminderEntity, now: ZonedDateTime): ZonedDateTime? {
        if (!r.enabled) return null
        val latest = Schedule.latestAtOrBefore(r, now) ?: return null
        val ms = latest.toInstant().toEpochMilli()
        if (ms <= r.lastFiredAt) return null
        return latest.takeIf { Duration.between(it, now) <= CATCH_UP }
    }

    /**
     * Marks [r] as up to date with its schedule at [now], so a newly added or edited reminder doesn't
     * count a time already past as missed while away. A pending reminder keeps its last time.
     */
    fun settled(r: ReminderEntity, now: ZonedDateTime): ReminderEntity {
        if (r.pending) return r
        val latest = Schedule.latestAtOrBefore(r, now)?.toInstant()?.toEpochMilli() ?: return r
        return r.copy(lastFiredAt = maxOf(r.lastFiredAt, latest))
    }

    private fun covered(r: ReminderEntity, at: Long, doseTimes: List<Long>, now: Long): Boolean {
        val from = at - Schedule.earlyWindowMinutes(r) * 60_000L
        return doseTimes.any { it in from..now }
    }

    /** "Time for Omeprazole" */
    fun title(med: MedicationEntity) = "Time for ${med.name}"

    /** "20 mg, 1 capsule. With lunch." */
    fun text(med: MedicationEntity, note: String): String {
        val m = med.lastMultiplier
        val count = MedicationType.fromKey(med.type).pieces(m)
        val amount = if (med.dose > 0) "${formatAmount(med.dose * m)} ${med.doseUnit}, $count" else count
        val n = note.trim().trimEnd('.')
        return if (n.isEmpty()) "$amount." else "$amount. $n."
    }

}
