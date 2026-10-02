package com.clementine.panacea.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clementine.panacea.PanaceaApp
import kotlinx.coroutines.launch

/** The app's own alarms and the notification's buttons. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PanaceaApp).container
        val reminders = container.reminders
        // "panacea://reminder/12", "panacea://snooze/12", "panacea://mute/12" or "panacea://mute/all"
        val id = intent.data?.lastPathSegment?.toLongOrNull()
        val at = intent.getLongExtra(EXTRA_AT, 0)
        val done = goAsync()
        container.appScope.launch {
            try {
                container.started.await()
                when (intent.action) {
                    FIRE -> id?.let { reminders.fire(it, at, snoozed = false) }
                    SNOOZE_END -> id?.let { reminders.fire(it, at, snoozed = true) }
                    TAKEN -> id?.let { reminders.taken(it) }
                    SNOOZE -> id?.let { reminders.snooze(it) }
                    REPOST -> id?.let { reminders.repost(it) }
                    MUTE_END -> reminders.muteEnded(id)
                }
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val FIRE = "com.clementine.panacea.reminder.FIRE"
        const val SNOOZE_END = "com.clementine.panacea.reminder.SNOOZE_END"
        const val TAKEN = "com.clementine.panacea.reminder.TAKEN"
        const val SNOOZE = "com.clementine.panacea.reminder.SNOOZE"
        const val REPOST = "com.clementine.panacea.reminder.REPOST"
        const val MUTE_END = "com.clementine.panacea.reminder.MUTE_END"

        /** When the reminder came due, epoch ms. */
        const val EXTRA_AT = "at"
    }
}

/** Boot, an app update and clock changes all leave alarms missing or wrong; set them again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PanaceaApp).container
        val done = goAsync()
        container.appScope.launch {
            try {
                container.started.await()
                container.reminders.resync()
            } finally {
                done.finish()
            }
        }
    }
}
