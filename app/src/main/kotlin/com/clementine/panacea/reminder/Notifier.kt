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
import com.clementine.panacea.ui.reminders.RoutineText
import com.clementine.panacea.ui.timeFormats
import java.time.ZonedDateTime

/**
 * Reminder notifications: one per reminder, with Taken, Snooze 10 Min and Mute…; and one question per
 * medication learning its routine, with Took It Now, Took It Earlier and Mute….
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        // 3.4's channel id, so a phone keeps the importance and sound the user gave it.
        val channel = NotificationChannel(CHANNEL_ID, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Reminders to take your medications"
        val learned = NotificationChannel(LEARNED_CHANNEL_ID, "Learned Reminders", NotificationManager.IMPORTANCE_HIGH)
        learned.description = "Asks whether you took a medication when a usual dose isn't logged"
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, learned))
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

    /** "Did you take Sertraline?" A question rather than a reminder, so a swipe puts it away. */
    fun ask(med: MedicationEntity, routine: Routine, lastLogged: Long?, now: ZonedDateTime) {
        val text = RoutineText.notification(routine, lastLogged, now, timeFormats(context))
        val notification = NotificationCompat.Builder(context, LEARNED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setSubText("Learned reminder")
            .setContentTitle(RoutineText.question(med.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(activity(MainActivity.ACTION_OPEN_MEDICATION, med.id))
            .addAction(0, "Took It Now", learned(ReminderReceiver.TOOK_NOW, med.id))
            .addAction(0, "Took It Earlier", activity(MainActivity.ACTION_TOOK_EARLIER, med.id))
            .addAction(0, "Mute…", activity(MainActivity.ACTION_MUTE, med.id))
            .build()
        try {
            manager.notify(LEARNED_TAG, notificationId(med.id), notification)
        } catch (_: SecurityException) {
            // Notifications aren't allowed; the medication's page says so.
        }
    }

    fun cancelAsk(medicationId: Long) = manager.cancel(LEARNED_TAG, notificationId(medicationId))

    /** Every notification the app is showing. */
    fun cancelAll() = manager.cancelAll()

    private fun learned(action: String, medicationId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("panacea://learned/$medicationId"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

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
        const val LEARNED_CHANNEL_ID = "learned_reminders"

        /** Learned questions are told apart from reminders by this tag, as ids may collide. */
        private const val LEARNED_TAG = "learned"
        private const val ACCENT = 0xFFB39DF0.toInt()

        /** Imported 3.4 ids are any 32-bit number, so ids are folded rather than cut. */
        fun notificationId(reminderId: Long): Int = reminderId.hashCode()
    }
}
