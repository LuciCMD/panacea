package com.clementine.panacea.reminder

import android.content.Context
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MetaEntity
import com.clementine.panacea.data.db.MetaKeys
import com.clementine.panacea.data.db.PanaceaDatabase
import com.clementine.panacea.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Runs the reminders: keeps one alarm armed per reminder, decides at each alarm whether to notify,
 * and handles the notification's actions and mutes. Every change goes through one lock, so an
 * alarm and a dose logged at the same moment can't undo each other.
 */
class Reminders(context: Context, private val db: PanaceaDatabase, private val medications: MedicationRepository) {
    private val alarms = Alarms(context)
    private val notifier = Notifier(context)
    private val lock = Mutex()
    private val reminderDao = db.reminderDao()
    private val medicationDao = db.medicationDao()
    private val doseDao = db.doseDao()
    private val metaDao = db.metaDao()

    val notificationsAllowed: Boolean get() = notifier.allowed

    fun observeMuteAll(): Flow<Long> = metaDao.observe(MetaKeys.MUTE_ALL_UNTIL).map { it?.toLongOrNull() ?: 0L }

    /** Keeps the alarms in step with the reminder table for as long as the app runs. */
    suspend fun watch() {
        var known = emptySet<Long>()
        reminderDao.observeAll().collect { list ->
            lock.withLock {
                armAll(list)
                val ids = list.mapTo(HashSet()) { it.id }
                // Removed, or gone with their medication.
                (known - ids).forEach {
                    alarms.cancel(it)
                    notifier.cancel(it)
                }
                known = ids
            }
        }
    }

    /**
     * After boot, an update, a clock change or the app starting: arms every alarm again, shows what
     * came due while none could fire, and ends mutes that ran out meanwhile.
     */
    suspend fun resync() = lock.withLock {
        val list = reminderDao.all()
        armAll(list)
        val now = ZonedDateTime.now()
        list.forEach { r -> ReminderEngine.missedWhileAway(r, now)?.let { fireLocked(r.id, it.toInstant().toEpochMilli(), snoozed = false) } }
        val nowMs = now.toInstant().toEpochMilli()
        val all = muteAllUntil()
        if (all > nowMs) alarms.armMuteEnd(null, all)
        medicationDao.all().filter { it.mutedUntil > nowMs }.forEach { alarms.armMuteEnd(it.id, it.mutedUntil) }
        releaseLocked(null)
    }

    /** [snoozed]: a snooze ran out, rather than the reminder's own time coming. */
    suspend fun fire(reminderId: Long, at: Long, snoozed: Boolean) = lock.withLock { fireLocked(reminderId, at, snoozed) }

    /** Taken on the notification: logs the medication's usual amount. */
    suspend fun taken(reminderId: Long) = lock.withLock {
        notifier.cancel(reminderId)
        val r = reminderDao.get(reminderId) ?: return@withLock
        val med = medicationDao.get(r.medicationId) ?: return@withLock
        val now = System.currentTimeMillis()
        medications.takeDose(med.id, med.lastMultiplier, now)
        doseLoggedLocked(med.id, now)
    }

    /** A dose was logged in the app; it settles any reminder it counts for. */
    suspend fun doseLogged(medicationId: Long, takenAt: Long) = lock.withLock { doseLoggedLocked(medicationId, takenAt) }

    suspend fun snooze(reminderId: Long) = lock.withLock {
        notifier.cancel(reminderId)
        val r = reminderDao.get(reminderId) ?: return@withLock
        alarms.snooze(reminderId, r.lastFiredAt, System.currentTimeMillis() + SNOOZE_MS)
    }

    /** The notification was swiped away while still due: put it back. */
    suspend fun repost(reminderId: Long) = lock.withLock {
        val r = reminderDao.get(reminderId) ?: return@withLock
        val med = medicationDao.get(r.medicationId) ?: return@withLock
        if (r.enabled && r.pending && mutedUntil(med) <= System.currentTimeMillis()) notifier.show(r, med)
    }

    /** Quiets [medicationId]'s reminders, or all of them when null, until [until]. */
    suspend fun mute(medicationId: Long?, until: Long) = lock.withLock {
        val targets = if (medicationId == null) {
            metaDao.put(MetaEntity(MetaKeys.MUTE_ALL_UNTIL, until.toString()))
            reminderDao.all()
        } else {
            medicationDao.setMutedUntil(medicationId, until)
            reminderDao.ofMedication(medicationId)
        }
        // Showing ones are held like any other and come back when the mute ends.
        targets.forEach { notifier.cancel(it.id) }
        alarms.armMuteEnd(medicationId, until)
    }

    suspend fun unmute(medicationId: Long?) = lock.withLock {
        if (medicationId == null) metaDao.put(MetaEntity(MetaKeys.MUTE_ALL_UNTIL, "0")) else medicationDao.setMutedUntil(medicationId, 0)
        alarms.cancelMuteEnd(medicationId)
        releaseLocked(medicationId)
    }

    /** A mute's alarm went off; it may have been changed since. */
    suspend fun muteEnded(medicationId: Long?) = lock.withLock {
        val until = if (medicationId == null) muteAllUntil() else medicationDao.get(medicationId)?.mutedUntil ?: 0
        if (until > System.currentTimeMillis()) alarms.armMuteEnd(medicationId, until) else releaseLocked(medicationId)
    }

    private fun armAll(list: List<ReminderEntity>) {
        val now = ZonedDateTime.now()
        list.forEach { r ->
            val next = if (r.enabled) Schedule.nextAfter(r, now) else null
            if (next != null) {
                alarms.arm(r.id, next.toInstant().toEpochMilli())
            } else {
                alarms.cancel(r.id)
                notifier.cancel(r.id)
            }
        }
    }

    private suspend fun fireLocked(reminderId: Long, at: Long, snoozed: Boolean) {
        val r = reminderDao.get(reminderId) ?: return alarms.cancel(reminderId)
        val med = medicationDao.get(r.medicationId) ?: return
        val now = System.currentTimeMillis()
        val doses = doseDao.timesSince(med.id, at - DAY_MS)
        val fired = ReminderEngine.fire(r, at, doses, mutedUntil(med), now, snoozed)
        if (fired.reminder != r) reminderDao.update(fired.reminder)
        when (fired.action) {
            FireAction.NOTIFY -> notifier.show(fired.reminder, med)
            FireAction.HOLD, FireAction.QUIET -> notifier.cancel(r.id)
        }
        if (!snoozed && r.enabled) Schedule.nextAfter(r, ZonedDateTime.now())?.let { alarms.arm(r.id, it.toInstant().toEpochMilli()) }
    }

    private suspend fun doseLoggedLocked(medicationId: Long, takenAt: Long) {
        val now = System.currentTimeMillis()
        reminderDao.ofMedication(medicationId).filter { ReminderEngine.countsFor(it, takenAt) }.forEach {
            reminderDao.update(ReminderEngine.completed(it, now))
            notifier.cancel(it.id)
        }
    }

    /** Shows reminders held by a mute that has ended, unless a dose has counted for them since. */
    private suspend fun releaseLocked(medicationId: Long?) {
        val now = ZonedDateTime.now()
        val nowMs = now.toInstant().toEpochMilli()
        val list = if (medicationId == null) reminderDao.all() else reminderDao.ofMedication(medicationId)
        list.filter { ReminderEngine.showOnRelease(it, now) }.forEach { r ->
            val med = medicationDao.get(r.medicationId) ?: return@forEach
            if (mutedUntil(med) > nowMs) return@forEach
            val doses = doseDao.timesSince(med.id, r.lastFiredAt - DAY_MS)
            if (doses.any { ReminderEngine.countsFor(r, it) }) {
                reminderDao.update(ReminderEngine.completed(r, nowMs))
            } else {
                notifier.show(r, med)
            }
        }
    }

    private suspend fun mutedUntil(med: MedicationEntity) = maxOf(med.mutedUntil, muteAllUntil())

    private suspend fun muteAllUntil() = metaDao.get(MetaKeys.MUTE_ALL_UNTIL)?.toLongOrNull() ?: 0L

    private companion object {
        const val SNOOZE_MS = 10 * 60_000L
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
