package com.clementine.medtracker.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.clementine.medtracker.MainActivity;
import com.clementine.medtracker.R;
import com.clementine.medtracker.utils.MedicationManager;
import com.clementine.medtracker.utils.ReminderManager;

/**
 * Handles medication reminder notifications and their action buttons.
 *
 * The notification is <b>ongoing</b> (not swipe-dismissable) and high importance, so it stays
 * up like an alarm until acted on. If a device still lets the user swipe it away, the
 * {@code deleteIntent} re-posts it ({@code REPOST}). Entry points, by the {@code action} extra:
 * <ul>
 *   <li>{@code FIRE} (default) - the scheduled alarm: show the notification and re-arm the next
 *       occurrence.</li>
 *   <li>{@code TAKE} - the "Taken" button: log a full dose without opening the app.</li>
 *   <li>{@code SNOOZE} - the "Snooze" button: re-fire a few minutes later.</li>
 *   <li>{@code REPOST} - the notification was swiped away: show it again.</li>
 * </ul>
 *
 * @author LuciCMD
 */
public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MED = "medication_name";
    private static final String EXTRA_ID = "reminder_id";
    private static final String ACTION_TAKE = "TAKE";
    private static final String ACTION_SNOOZE = "SNOOZE";
    private static final String ACTION_REPOST = "REPOST";
    private static final int SNOOZE_MINUTES = 10;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra(EXTRA_ACTION);
        String medicationName = intent.getStringExtra(EXTRA_MED);
        int reminderId = intent.getIntExtra(EXTRA_ID, 0);

        if (ACTION_TAKE.equals(action)) {
            if (medicationName != null) {
                // Full dose (multiplier 1.0); writes straight to storage via the data singleton
                new MedicationManager(context).takeMedication(medicationName, 1.0);
            }
            new ReminderManager(context).recordCompleted(reminderId);
            NotificationManagerCompat.from(context).cancel(reminderId);
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            new ReminderManager(context).snooze(reminderId, SNOOZE_MINUTES);
            NotificationManagerCompat.from(context).cancel(reminderId);
            return;
        }

        if (ACTION_REPOST.equals(action)) {
            // Swiped away without acting: put it back
            if (medicationName != null) {
                String label = labelFor(context, reminderId);
                showReminderNotification(context, medicationName, reminderId, label);
            }
            return;
        }

        // Default: the scheduled alarm fired
        if (medicationName != null) {
            boolean snoozed = intent.getBooleanExtra("snoozed", false);
            ReminderManager reminderManager = new ReminderManager(context);
            // A fresh scheduled fire counts a missed dose if the previous one is still pending;
            // a snooze re-fire is the same dose, so it doesn't.
            reminderManager.recordFired(reminderId, !snoozed);
            ReminderManager.Reminder reminder = reminderManager.getReminderById(reminderId);
            String label = (reminder != null) ? reminder.getLabel() : "";
            showReminderNotification(context, medicationName, reminderId, label);
            reminderManager.scheduleNextOccurrence(reminderId);
        }
    }

    private String labelFor(Context context, int reminderId) {
        ReminderManager.Reminder reminder = new ReminderManager(context).getReminderById(reminderId);
        return (reminder != null) ? reminder.getLabel() : "";
    }

    private void showReminderNotification(Context context, String medicationName,
                                          int reminderId, String label) {
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
            context, reminderId, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = "It's time to take your " + medicationName + ".";
        if (label != null && !label.trim().isEmpty()) {
            body += " (" + label.trim() + ")";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReminderManager.getChannelId())
            .setSmallIcon(R.mipmap.gobbler_ic_round)
            .setContentTitle("Time for " + medicationName)
            .setContentText("Don't forget to take " + medicationName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setDeleteIntent(actionIntent(context, ACTION_REPOST, medicationName, reminderId))
            .addAction(android.R.drawable.ic_menu_save, "Taken",
                       actionIntent(context, ACTION_TAKE, medicationName, reminderId))
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze",
                       actionIntent(context, ACTION_SNOOZE, medicationName, reminderId))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body + " Tap to open Panacea."));

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(reminderId, builder.build());
        } catch (SecurityException e) {
            // Notification permission not granted
        }
    }

    /** Builds a broadcast PendingIntent back to this receiver for a notification action. */
    private PendingIntent actionIntent(Context context, String action,
                                       String medicationName, int reminderId) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.putExtra(EXTRA_ACTION, action);
        intent.putExtra(EXTRA_MED, medicationName);
        intent.putExtra(EXTRA_ID, reminderId);
        // Distinct request codes per action so the PendingIntents don't overwrite each other
        int offset;
        switch (action) {
            case ACTION_TAKE: offset = 1; break;
            case ACTION_SNOOZE: offset = 2; break;
            default: offset = 3; break; // REPOST
        }
        return PendingIntent.getBroadcast(
            context, reminderId * 10 + offset, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
