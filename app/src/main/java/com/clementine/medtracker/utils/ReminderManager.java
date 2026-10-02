package com.clementine.medtracker.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;

import com.clementine.medtracker.receivers.MedicationReminderReceiver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Manages medication reminders and notifications.
 *
 * Each reminder repeats on one of four schedules ({@link Reminder.RepeatType}):
 * hourly (every N hours within a daily window), daily (one or more times a day),
 * weekly (chosen weekdays + times), or monthly (a day-of-month + times). Every reminder
 * keeps exactly one pending alarm pointing at its next occurrence; when it fires the
 * receiver re-arms the next one, so multi-dose and hourly schedules never stack up.
 *
 * @author LuciCMD
 */
public class ReminderManager {
    private static final String TAG = "ReminderManager";
    private static final String PREFS_NAME = "ReminderPrefs";
    private static final String REMINDERS_KEY = "medication_reminders";
    // v2 channel: high importance so reminders heads-up like an alarm (channel importance is
    // sticky once created, so a new id is needed to upgrade from the old default-importance one)
    private static final String CHANNEL_ID = "medication_reminders_v2";
    private static final String CHANNEL_NAME = "Medication Reminders";

    private final Context context;
    private final SharedPreferences prefs;
    private final AlarmManager alarmManager;
    private final NotificationManagerCompat notificationManager;

    public static class Reminder {
        public enum RepeatType {
            HOURLY, DAILY, WEEKLY, MONTHLY;

            public static RepeatType fromKey(String key) {
                if (key != null) {
                    for (RepeatType type : values()) {
                        if (type.name().equals(key)) return type;
                    }
                }
                return DAILY;
            }
        }

        /** Days bitmask: bit i (0=Sunday .. 6=Saturday) set means the reminder may fire that day. */
        public static final int EVERY_DAY = 0x7F;
        private static final int MAX_TIMES = 12;

        private String medicationName;
        private RepeatType repeatType;
        private boolean enabled;
        private int daysMask;               // HOURLY / DAILY / WEEKLY
        private final List<int[]> times;    // DAILY / WEEKLY / MONTHLY: each {hour, minute}
        private int intervalHours;          // HOURLY: fire every N hours
        private int startHour, startMinute; // HOURLY: window start
        private int endHour, endMinute;     // HOURLY: window end
        private int dayOfMonth;             // MONTHLY: 1..31 (clamped to month length)
        private String label;
        private int id;

        // Adherence tracking (see ReminderManager.recordFired / recordCompleted)
        private int timesCompleted;
        private int timesMissed;
        private boolean pending;            // fired and awaiting a Taken/miss
        private long lastFiredAt;
        private long lastCompletedAt;

        public Reminder(String medicationName, RepeatType repeatType) {
            this.medicationName = medicationName;
            this.repeatType = (repeatType == null) ? RepeatType.DAILY : repeatType;
            this.enabled = true;
            this.daysMask = EVERY_DAY;
            this.times = new ArrayList<>();
            this.intervalHours = 4;
            this.startHour = 8;
            this.startMinute = 0;
            this.endHour = 22;
            this.endMinute = 0;
            this.dayOfMonth = 1;
            this.label = "";
            // Unique, stable id (persisted); avoids collisions between reminders on the same med/time
            this.id = (medicationName + "|" + System.nanoTime()).hashCode();
        }

        private static int normalizeDays(int daysMask) {
            int masked = daysMask & EVERY_DAY;
            return (masked == 0) ? EVERY_DAY : masked;
        }

        public String getMedicationName() { return medicationName; }
        public void setMedicationName(String medicationName) { this.medicationName = medicationName; }
        public RepeatType getRepeatType() { return repeatType; }
        public void setRepeatType(RepeatType repeatType) { this.repeatType = repeatType; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getDaysMask() { return daysMask; }
        public void setDaysMask(int daysMask) { this.daysMask = normalizeDays(daysMask); }
        public int getIntervalHours() { return intervalHours; }
        public void setIntervalHours(int intervalHours) { this.intervalHours = Math.max(1, intervalHours); }
        public int getStartHour() { return startHour; }
        public int getStartMinute() { return startMinute; }
        public void setStart(int hour, int minute) { this.startHour = hour; this.startMinute = minute; }
        public int getEndHour() { return endHour; }
        public int getEndMinute() { return endMinute; }
        public void setEnd(int hour, int minute) { this.endHour = hour; this.endMinute = minute; }
        public int getDayOfMonth() { return dayOfMonth; }
        public void setDayOfMonth(int dayOfMonth) { this.dayOfMonth = Math.min(31, Math.max(1, dayOfMonth)); }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = (label == null) ? "" : label; }
        public int getId() { return id; }

        public int getTimesCompleted() { return timesCompleted; }
        public int getTimesMissed() { return timesMissed; }
        public boolean isPending() { return pending; }
        public long getLastFiredAt() { return lastFiredAt; }
        public long getLastCompletedAt() { return lastCompletedAt; }
        void setPending(boolean pending) { this.pending = pending; }
        void setLastFiredAt(long lastFiredAt) { this.lastFiredAt = lastFiredAt; }
        void setLastCompletedAt(long lastCompletedAt) { this.lastCompletedAt = lastCompletedAt; }
        void incrementCompleted() { this.timesCompleted++; }
        void incrementMissed() { this.timesMissed++; }

        /** Total scheduled doses accounted for; adherence = completed / total. */
        public int totalAccounted() { return timesCompleted + timesMissed; }

        public List<int[]> getTimes() { return times; }

        public void setTimes(List<int[]> newTimes) {
            times.clear();
            if (newTimes != null) {
                for (int[] time : newTimes) {
                    if (times.size() >= MAX_TIMES) break;
                    if (time != null && time.length == 2) times.add(new int[]{time[0], time[1]});
                }
            }
            if (times.isEmpty()) times.add(new int[]{8, 0});
        }

        public boolean isEveryDay() { return (daysMask & EVERY_DAY) == EVERY_DAY; }

        /** @param calendarDayOfWeek Calendar.SUNDAY (1) .. Calendar.SATURDAY (7). */
        public boolean isDayEnabled(int calendarDayOfWeek) {
            int bit = 1 << (calendarDayOfWeek - 1);
            return (daysMask & bit) != 0;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("medicationName", medicationName);
            json.put("repeatType", repeatType.name());
            json.put("enabled", enabled);
            json.put("daysMask", daysMask);
            json.put("intervalHours", intervalHours);
            json.put("startHour", startHour);
            json.put("startMinute", startMinute);
            json.put("endHour", endHour);
            json.put("endMinute", endMinute);
            json.put("dayOfMonth", dayOfMonth);
            json.put("label", label);
            json.put("id", id);
            json.put("timesCompleted", timesCompleted);
            json.put("timesMissed", timesMissed);
            json.put("pending", pending);
            json.put("lastFiredAt", lastFiredAt);
            json.put("lastCompletedAt", lastCompletedAt);
            JSONArray timesArray = new JSONArray();
            for (int[] time : times) {
                JSONObject t = new JSONObject();
                t.put("h", time[0]);
                t.put("m", time[1]);
                timesArray.put(t);
            }
            json.put("times", timesArray);
            return json;
        }

        public static Reminder fromJson(JSONObject json) throws JSONException {
            Reminder reminder = new Reminder(
                    json.getString("medicationName"),
                    RepeatType.fromKey(json.optString("repeatType", null)));
            reminder.enabled = json.optBoolean("enabled", true);
            reminder.daysMask = normalizeDays(json.optInt("daysMask", EVERY_DAY));
            reminder.intervalHours = Math.max(1, json.optInt("intervalHours", 4));
            reminder.startHour = json.optInt("startHour", 8);
            reminder.startMinute = json.optInt("startMinute", 0);
            reminder.endHour = json.optInt("endHour", 22);
            reminder.endMinute = json.optInt("endMinute", 0);
            reminder.dayOfMonth = Math.min(31, Math.max(1, json.optInt("dayOfMonth", 1)));
            reminder.label = json.optString("label", "");
            if (json.has("id")) reminder.id = json.getInt("id");
            reminder.timesCompleted = json.optInt("timesCompleted", 0);
            reminder.timesMissed = json.optInt("timesMissed", 0);
            reminder.pending = json.optBoolean("pending", false);
            reminder.lastFiredAt = json.optLong("lastFiredAt", 0);
            reminder.lastCompletedAt = json.optLong("lastCompletedAt", 0);

            reminder.times.clear();
            JSONArray timesArray = json.optJSONArray("times");
            if (timesArray != null) {
                for (int i = 0; i < timesArray.length() && reminder.times.size() < MAX_TIMES; i++) {
                    JSONObject t = timesArray.getJSONObject(i);
                    reminder.times.add(new int[]{t.getInt("h"), t.getInt("m")});
                }
            } else if (json.has("hour")) {
                // Migrate a pre-3.2 reminder (single hour/minute, daily) into the times list
                reminder.times.add(new int[]{json.getInt("hour"), json.optInt("minute", 0)});
            }
            if (reminder.times.isEmpty()) reminder.times.add(new int[]{8, 0});
            return reminder;
        }

        /** Short "HH:MM" for the first (or only) time; used in list summaries. */
        public String getTimeString() {
            int[] first = times.isEmpty() ? new int[]{0, 0} : times.get(0);
            return String.format(Locale.US, "%02d:%02d", first[0], first[1]);
        }
    }

    public ReminderManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminders to take your medications");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public static String getChannelId() { return CHANNEL_ID; }

    public void addReminder(Reminder reminder) {
        List<Reminder> reminders = getReminders();
        reminders.add(reminder);
        saveReminders(reminders);
        if (reminder.isEnabled()) scheduleReminder(reminder);
    }

    public void removeReminder(Reminder reminder) {
        List<Reminder> reminders = getReminders();
        reminders.removeIf(r -> r.getId() == reminder.getId());
        saveReminders(reminders);
        cancelReminder(reminder);
        notificationManager.cancel(reminder.getId());
    }

    public void updateReminder(Reminder reminder) {
        List<Reminder> reminders = getReminders();
        for (int i = 0; i < reminders.size(); i++) {
            if (reminders.get(i).getId() == reminder.getId()) {
                reminders.set(i, reminder);
                break;
            }
        }
        saveReminders(reminders);
        cancelReminder(reminder);
        if (reminder.isEnabled()) scheduleReminder(reminder);
    }

    public List<Reminder> getReminders() {
        List<Reminder> reminders = new ArrayList<>();
        try {
            String reminderJson = prefs.getString(REMINDERS_KEY, "[]");
            JSONArray array = new JSONArray(reminderJson);
            for (int i = 0; i < array.length(); i++) {
                reminders.add(Reminder.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading reminders", e);
        }
        return reminders;
    }

    public List<Reminder> getRemindersForMedication(String medicationName) {
        List<Reminder> medReminders = new ArrayList<>();
        for (Reminder reminder : getReminders()) {
            if (reminder.getMedicationName().equals(medicationName)) medReminders.add(reminder);
        }
        return medReminders;
    }

    private void saveReminders(List<Reminder> reminders) {
        try {
            JSONArray array = new JSONArray();
            for (Reminder reminder : reminders) array.put(reminder.toJson());
            prefs.edit().putString(REMINDERS_KEY, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving reminders", e);
        }
    }

    private void scheduleReminder(Reminder reminder) {
        long triggerAt = computeNextTrigger(reminder, System.currentTimeMillis());
        if (triggerAt > 0) scheduleAt(reminder, triggerAt, false);
    }

    /** Next fire time for a reminder (absolute ms), or -1 if it can never fire. Public for the UI. */
    public long getNextTriggerTime(Reminder reminder) {
        return computeNextTrigger(reminder, System.currentTimeMillis());
    }

    /**
     * Next fire time strictly after {@code fromMillis} across all of the reminder's slots.
     * Scans day by day (bounded) for the next active day, then the earliest slot on it.
     */
    private long computeNextTrigger(Reminder reminder, long fromMillis) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(fromMillis);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        // 400 days covers monthly reminders landing in a later month; loop is statically bounded
        for (int i = 0; i < 400; i++) {
            if (isDayActive(reminder, day)) {
                long slot = firstSlotAfter(reminder, day, fromMillis);
                if (slot > 0) return slot;
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
        }
        return -1;
    }

    private boolean isDayActive(Reminder reminder, Calendar day) {
        if (reminder.getRepeatType() == Reminder.RepeatType.MONTHLY) {
            int target = Math.min(reminder.getDayOfMonth(), day.getActualMaximum(Calendar.DAY_OF_MONTH));
            return day.get(Calendar.DAY_OF_MONTH) == target;
        }
        return reminder.isDayEnabled(day.get(Calendar.DAY_OF_WEEK));
    }

    /** Earliest slot on {@code day} whose datetime is after {@code fromMillis}, else -1. */
    private long firstSlotAfter(Reminder reminder, Calendar day, long fromMillis) {
        for (int minutesOfDay : slotMinutesForDay(reminder)) {
            Calendar slot = (Calendar) day.clone();
            slot.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
            slot.set(Calendar.MINUTE, minutesOfDay % 60);
            if (slot.getTimeInMillis() > fromMillis) return slot.getTimeInMillis();
        }
        return -1;
    }

    /** Sorted minutes-of-day when this reminder fires on an active day. */
    private int[] slotMinutesForDay(Reminder reminder) {
        if (reminder.getRepeatType() == Reminder.RepeatType.HOURLY) {
            List<Integer> list = new ArrayList<>();
            int start = reminder.getStartHour() * 60 + reminder.getStartMinute();
            int end = reminder.getEndHour() * 60 + reminder.getEndMinute();
            int step = Math.max(1, reminder.getIntervalHours()) * 60;
            for (int t = start; t <= end && list.size() < 48; t += step) list.add(t);
            int[] mins = new int[list.size()];
            for (int i = 0; i < list.size(); i++) mins[i] = list.get(i);
            return mins;
        }
        List<int[]> times = reminder.getTimes();
        int[] mins = new int[times.size()];
        for (int i = 0; i < times.size(); i++) mins[i] = times.get(i)[0] * 60 + times.get(i)[1];
        Arrays.sort(mins);
        return mins;
    }

    /**
     * Sets the single exact alarm that fires the reminder at {@code triggerAtMillis}.
     * {@code snoozed} marks a snooze re-fire so it isn't counted as a new missed dose.
     */
    private void scheduleAt(Reminder reminder, long triggerAtMillis, boolean snoozed) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.putExtra("medication_name", reminder.getMedicationName());
        intent.putExtra("reminder_id", reminder.getId());
        intent.putExtra("snoozed", snoozed);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, reminder.getId(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Scheduled " + reminder.getMedicationName() + " (" + reminder.getRepeatType() + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule reminder", e);
        }
    }

    private void cancelReminder(Reminder reminder) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, reminder.getId(), intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    public Reminder getReminderById(int id) {
        for (Reminder reminder : getReminders()) {
            if (reminder.getId() == id) return reminder;
        }
        return null;
    }

    /** Re-arms the reminder for its next occurrence. Called after it fires so it keeps recurring. */
    public void scheduleNextOccurrence(int id) {
        Reminder reminder = getReminderById(id);
        if (reminder != null && reminder.isEnabled()) scheduleReminder(reminder);
    }

    /** Re-fires the reminder after {@code minutes} (the notification "Snooze" action). */
    public void snooze(int id, int minutes) {
        Reminder reminder = getReminderById(id);
        if (reminder == null) return;
        scheduleAt(reminder, System.currentTimeMillis() + (long) minutes * 60 * 1000, true);
    }

    /**
     * A reminder fired. If the previous fire was still pending (never taken), that dose counts as
     * missed. Marks it pending again. Snooze re-fires pass through {@code countMissed = false}.
     */
    public void recordFired(int id, boolean countMissed) {
        Reminder reminder = getReminderById(id);
        if (reminder == null) return;
        if (countMissed && reminder.isPending()) reminder.incrementMissed();
        reminder.setPending(true);
        reminder.setLastFiredAt(System.currentTimeMillis());
        persist(reminder);
    }

    /** The user took the dose for a pending reminder (via Taken, or an in-app take). */
    public void recordCompleted(int id) {
        Reminder reminder = getReminderById(id);
        if (reminder == null || !reminder.isPending()) return;
        reminder.incrementCompleted();
        reminder.setPending(false);
        reminder.setLastCompletedAt(System.currentTimeMillis());
        persist(reminder);
    }

    /** Saves a mutated reminder in place (no reschedule); used for adherence counters. */
    private void persist(Reminder updated) {
        List<Reminder> reminders = getReminders();
        for (int i = 0; i < reminders.size(); i++) {
            if (reminders.get(i).getId() == updated.getId()) {
                reminders.set(i, updated);
                break;
            }
        }
        saveReminders(reminders);
    }

    /**
     * Clears any showing reminder notifications for a medication (used when the user takes it in
     * the app) and counts pending ones as completed. The next scheduled alarm stays armed.
     */
    public void cancelNotificationsForMedication(String medicationName) {
        for (Reminder reminder : getRemindersForMedication(medicationName)) {
            if (reminder.isPending()) recordCompleted(reminder.getId());
            notificationManager.cancel(reminder.getId());
        }
    }

    public void removeMedicationReminders(String medicationName) {
        List<Reminder> reminders = getReminders();
        List<Reminder> toRemove = new ArrayList<>();
        for (Reminder reminder : reminders) {
            if (reminder.getMedicationName().equals(medicationName)) {
                toRemove.add(reminder);
                cancelReminder(reminder);
                notificationManager.cancel(reminder.getId());
            }
        }
        reminders.removeAll(toRemove);
        saveReminders(reminders);
    }

    public void rescheduleAllReminders() {
        for (Reminder reminder : getReminders()) {
            if (reminder.isEnabled()) scheduleReminder(reminder);
        }
    }

    /** Wholesale replace used by JSON restore: cancels every existing alarm, then saves and schedules. */
    public void replaceAll(List<Reminder> newReminders) {
        for (Reminder existing : getReminders()) cancelReminder(existing);
        saveReminders(newReminders);
        for (Reminder reminder : newReminders) {
            if (reminder.isEnabled()) scheduleReminder(reminder);
        }
    }
}
