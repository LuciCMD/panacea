package com.clementine.panacea.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clementine.panacea.MainActivity
import com.clementine.panacea.R
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.ReminderEntity

/** Reminder notifications: one per reminder, with Taken, Snooze 10 Min and Mute…. */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        // 3.4's channel id, so a phone keeps the importance and sound the user gave it.
        val channel = NotificationChannel(CHANNEL_ID, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Reminders to take your medications"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    val allowed: Boolean get() = manager.areNotificationsEnabled()

    fun show(r: ReminderEntity, med: MedicationEntity) {
        val text = ReminderEngine.text(med, r.note)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(ReminderEngine.title(med))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(r.lastFiredAt)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Stays until it's dealt with, as in 3.4; swiping it away only brings it back.
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(activity(MainActivity.ACTION_OPEN_MEDICATION, med.id))
            .setDeleteIntent(broadcast(ReminderReceiver.REPOST, r.id))
            .addAction(0, "Taken", broadcast(ReminderReceiver.TAKEN, r.id))
            .addAction(0, "Snooze 10 Min", broadcast(ReminderReceiver.SNOOZE, r.id))
            .addAction(0, "Mute…", activity(MainActivity.ACTION_MUTE, med.id))
            .build()
        try {
            manager.notify(notificationId(r.id), notification)
        } catch (_: SecurityException) {
            // Notifications aren't allowed; the Reminders tab says so.
        }
    }

    fun cancel(reminderId: Long) = manager.cancel(notificationId(reminderId))

    private fun broadcast(action: String, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("panacea://reminder/$reminderId"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun activity(action: String, medicationId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setData(Uri.parse("panacea://medication/$medicationId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "medication_reminders_v2"
        private const val ACCENT = 0xFFB39DF0.toInt()

        /** Imported 3.4 ids are any 32-bit number, so ids are folded rather than cut. */
        fun notificationId(reminderId: Long): Int = reminderId.hashCode()
    }
}
