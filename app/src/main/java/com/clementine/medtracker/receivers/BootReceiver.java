package com.clementine.medtracker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.clementine.medtracker.utils.ReminderManager;

/**
 * Handles device boot to reschedule medication reminders
 * @author LuciCMD
 */
public class BootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            
            // Reschedule all reminders after boot or app update
            ReminderManager reminderManager = new ReminderManager(context);
            reminderManager.rescheduleAllReminders();
        }
    }
}