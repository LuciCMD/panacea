package com.clementine.panacea.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The app's alarms: one per reminder at its next time, one per snoozed reminder, and one per mute.
 * Each is told apart by its data URI, so setting one again replaces it.
 */
class Alarms(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun arm(reminderId: Long, at: Long) = set(intent(ReminderReceiver.FIRE, "reminder/$reminderId", at), at)

    fun snooze(reminderId: Long, firedAt: Long, until: Long) =
        set(intent(ReminderReceiver.SNOOZE_END, "snooze/$reminderId", firedAt), until)

    /** [medicationId] null is Mute All. */
    fun armMuteEnd(medicationId: Long?, at: Long) = set(intent(ReminderReceiver.MUTE_END, muteKey(medicationId), at), at)

    fun cancel(reminderId: Long) {
        cancel(intent(ReminderReceiver.FIRE, "reminder/$reminderId", 0))
        cancel(intent(ReminderReceiver.SNOOZE_END, "snooze/$reminderId", 0))
    }

    fun cancelMuteEnd(medicationId: Long?) = cancel(intent(ReminderReceiver.MUTE_END, muteKey(medicationId), 0))

    private fun muteKey(medicationId: Long?) = if (medicationId == null) "mute/all" else "mute/$medicationId"

    private fun intent(action: String, path: String, at: Long) = Intent(context, ReminderReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("panacea://$path"))
        .putExtra(ReminderReceiver.EXTRA_AT, at)

    private fun set(intent: Intent, at: Long) {
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Exact even in Doze; if the permission was taken away, as close as Android allows.
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(intent: Intent) {
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        manager.cancel(pending)
        pending.cancel()
    }
}
