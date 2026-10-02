package com.clementine.medtracker.utils;

import java.util.Locale;

/**
 * Human-readable text for a reminder's schedule and adherence status, shared by the
 * reminders list. Kept separate from the model so formatting has one home.
 *
 * @author LuciCMD
 */
public final class ReminderFormatter {
    private static final String[] DAY_LABELS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private ReminderFormatter() { }

    public static String fmtTime(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    /** e.g. "Every day" or "Mon Wed Fri". */
    public static String daysSummary(ReminderManager.Reminder reminder) {
        if (reminder.isEveryDay()) return "Every day";
        StringBuilder days = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (reminder.isDayEnabled(i + 1)) {   // Calendar.SUNDAY == 1
                if (days.length() > 0) days.append(" ");
                days.append(DAY_LABELS[i]);
            }
        }
        return days.toString();
    }

    public static String timesSummary(ReminderManager.Reminder reminder) {
        StringBuilder builder = new StringBuilder();
        for (int[] time : reminder.getTimes()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(fmtTime(time[0], time[1]));
        }
        return builder.toString();
    }

    /** One-line description of when the reminder repeats (varies by type). */
    public static String scheduleSummary(ReminderManager.Reminder reminder) {
        switch (reminder.getRepeatType()) {
            case HOURLY:
                return String.format(Locale.US, "Every %dh %s–%s · %s",
                        reminder.getIntervalHours(),
                        fmtTime(reminder.getStartHour(), reminder.getStartMinute()),
                        fmtTime(reminder.getEndHour(), reminder.getEndMinute()),
                        daysSummary(reminder));
            case WEEKLY:
                return "Weekly " + daysSummary(reminder) + " " + timesSummary(reminder);
            case MONTHLY:
                return "Monthly day " + reminder.getDayOfMonth() + " " + timesSummary(reminder);
            case DAILY:
            default:
                return "Daily " + timesSummary(reminder) + " · " + daysSummary(reminder);
        }
    }

    /** "in 2h 15m" / "in 3d 4h" / "in 45m" from a future absolute time (or "—"). */
    public static String nextInText(long nextTriggerMillis) {
        if (nextTriggerMillis <= 0) return "—";
        long deltaMs = nextTriggerMillis - System.currentTimeMillis();
        if (deltaMs <= 0) return "due now";
        long minutes = deltaMs / (60 * 1000);
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;
        if (days > 0) return String.format(Locale.US, "in %dd %dh", days, hours);
        if (hours > 0) return String.format(Locale.US, "in %dh %dm", hours, mins);
        return String.format(Locale.US, "in %dm", Math.max(1, mins));
    }

    /** "92% (23/25)" adherence, or "—" when nothing has been tracked yet. */
    public static String adherenceText(ReminderManager.Reminder reminder) {
        int total = reminder.totalAccounted();
        if (total == 0) return "—";
        int percent = Math.round(reminder.getTimesCompleted() * 100f / total);
        return String.format(Locale.US, "%d%% (%d/%d)", percent,
                reminder.getTimesCompleted(), total);
    }
}
