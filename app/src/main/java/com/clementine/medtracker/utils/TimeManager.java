package com.clementine.medtracker.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Handles all time-related operations and updates for the medication tracker
 * @author LuciCMD
 */
public class TimeManager {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US);
    private final Handler updateHandler;
    private Runnable updateRunnable;
    private TimeUpdateListener listener;
    private static final String TAG = "TimeManager";

    public interface TimeUpdateListener {
        void onTimeUpdate(String timeSinceLastTaken);
    }

    public TimeManager() {
        this.updateHandler = new Handler(Looper.getMainLooper());
    }

    public void setUpdateListener(TimeUpdateListener listener) {
        this.listener = listener;
    }

    public void startTimeUpdates(final String lastTakenTime) {
        stopTimeUpdates(); // Stop any existing updates

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    String timeSince = getTimeSinceLastTaken(lastTakenTime);
                    listener.onTimeUpdate(timeSince);
                }
                updateHandler.postDelayed(this, 1000); // Update every second
            }
        };
        updateHandler.post(updateRunnable);
        Log.d(TAG, "Started time updates for: " + lastTakenTime);
    }

    public void stopTimeUpdates() {
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
            updateRunnable = null;
            Log.d(TAG, "Stopped time updates");
        }
    }

    public String getCurrentTimeStamp() {
        return DATE_FORMAT.format(new Date());
    }

    public String getTimeSinceLastTaken(String lastTakenTime) {
        if (lastTakenTime == null || lastTakenTime.equals("Last Gobbled: N/A")) {
            return "Time Since Gobbled: N/A";
        }

        try {
            Date lastTaken = DATE_FORMAT.parse(lastTakenTime);
            if (lastTaken == null) {
                Log.e(TAG, "Failed to parse date: " + lastTakenTime);
                return "Time Since Gobbled: Error";
            }

            long timeDiff = new Date().getTime() - lastTaken.getTime();

            // Calculate time components
            long days = TimeUnit.MILLISECONDS.toDays(timeDiff);
            long hours = TimeUnit.MILLISECONDS.toHours(timeDiff) % 24;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff) % 60;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(timeDiff) % 60;

            return String.format(Locale.US, "%dd %dh %dm %ds", days, hours, minutes, seconds);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date: " + lastTakenTime, e);
            return "Time Since Gobbled: Error";
        }
    }

    public boolean isWithinLast24Hours(String timestamp) {
        try {
            Date time = DATE_FORMAT.parse(timestamp);
            if (time == null) {
                Log.e(TAG, "Failed to parse timestamp: " + timestamp);
                return false;
            }

            long timeDiff = new Date().getTime() - time.getTime();
            return timeDiff < TimeUnit.HOURS.toMillis(24);
        } catch (ParseException e) {
            Log.e(TAG, "Error checking 24-hour period: " + timestamp, e);
            return false;
        }
    }

    public Date parseTimestamp(String timestamp) {
        try {
            Date parsedDate = DATE_FORMAT.parse(timestamp);
            if (parsedDate == null) {
                Log.e(TAG, "Failed to parse timestamp: " + timestamp);
                return new Date(); // Return current time as fallback
            }
            return parsedDate;
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
            return new Date(); // Return current time as fallback
        }
    }

    public long getMillisSinceLastTaken(String lastTakenTime) {
        try {
            Date lastTaken = DATE_FORMAT.parse(lastTakenTime);
            if (lastTaken == null) {
                Log.e(TAG, "Failed to parse last taken time: " + lastTakenTime);
                return -1;
            }
            return new Date().getTime() - lastTaken.getTime();
        } catch (ParseException e) {
            Log.e(TAG, "Error calculating time difference: " + lastTakenTime, e);
            return -1;
        }
    }

    public static SimpleDateFormat getDateFormat() {
        return DATE_FORMAT;
    }
}