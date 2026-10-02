package com.clementine.panacea.reminder

import android.app.AlarmManager
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
        // "panacea://reminder/12", "panacea://snooze/12", "panacea://learned/3", "panacea://learned-again/3", "panacea://mute/3" or "panacea://mute/all"
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
                    REPOST -> id?.let { reminders.repost(it) }
                    MUTE_END -> reminders.muteEnded(id)
                    ASK -> id?.let { reminders.ask(it) }
                    TOOK_NOW -> id?.let { reminders.tookNow(it) }
                    ASK_AGAIN, REPOST_ASK -> id?.let { reminders.askAgain(it) }
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
        const val REPOST = "com.clementine.panacea.reminder.REPOST"
        const val MUTE_END = "com.clementine.panacea.reminder.MUTE_END"

        /** A learned reminder's time to ask; the id is the medication's. */
        const val ASK = "com.clementine.panacea.reminder.ASK"
        const val TOOK_NOW = "com.clementine.panacea.reminder.TOOK_NOW"

        /** A learned question put off with Later… comes due; the id is the medication's. */
        const val ASK_AGAIN = "com.clementine.panacea.reminder.ASK_AGAIN"

        /** A learned question was swiped away while still open. */
        const val REPOST_ASK = "com.clementine.panacea.reminder.REPOST_ASK"

        /** When the reminder came due, epoch ms. */
        const val EXTRA_AT = "at"
    }
}

/** Boot, an app update and clock changes all leave alarms missing or wrong; set them again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts; anything else that reaches it is ignored.
        if (intent.action !in ACTIONS) return
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

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
