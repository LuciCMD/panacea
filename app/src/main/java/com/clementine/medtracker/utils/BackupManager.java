package com.clementine.medtracker.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.clementine.medtracker.data.MedicationDataManager;
import com.clementine.medtracker.models.Medication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-fidelity JSON backup and restore.
 *
 * Unlike the CSV export (a flattened, read-only report), this round-trips everything:
 * medications with their dosages, categories, types, ingredients and full usage
 * history, the saved display order, and reminders. This is the safe way to move data
 * between installs / devices and to recover after a reinstall.
 *
 * Older (v1) backups still import cleanly: {@link Medication#fromJson} migrates the legacy
 * single secondary drug into the ingredient list.
 *
 * @author LuciCMD
 */
public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final int BACKUP_VERSION = 2;

    private final Context context;
    private final MedicationDataManager dataManager;
    private final MedicationOrderManager orderManager;
    private final ReminderManager reminderManager;

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.dataManager = MedicationDataManager.getInstance(context);
        this.orderManager = new MedicationOrderManager(context);
        this.reminderManager = new ReminderManager(context);
    }

    public void exportBackup(Uri uri) throws IOException, JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Panacea");
        root.put("backupVersion", BACKUP_VERSION);
        root.put("exportedAt", System.currentTimeMillis());

        JSONArray medsArray = new JSONArray();
        for (Medication medication : dataManager.getAllMedications()) {
            medsArray.put(medication.toJson());
        }
        root.put("medications", medsArray);

        JSONArray orderArray = new JSONArray();
        for (String name : orderManager.loadMedicationOrder()) {
            orderArray.put(name);
        }
        root.put("order", orderArray);

        JSONArray remindersArray = new JSONArray();
        for (ReminderManager.Reminder reminder : reminderManager.getReminders()) {
            remindersArray.put(reminder.toJson());
        }
        root.put("reminders", remindersArray);

        try (OutputStream out = context.getContentResolver().openOutputStream(uri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write(root.toString(2));
            writer.flush();
        }
        Log.d(TAG, "Backup export complete");
    }

    /**
     * Restores from a backup file, replacing current data.
     * @return number of medications imported
     */
    public int importBackup(Uri uri) throws IOException, JSONException {
        String content = readAll(uri);
        JSONObject root = new JSONObject(content);

        JSONArray medsArray = root.optJSONArray("medications");
        if (medsArray == null) {
            throw new JSONException("File is not a valid Panacea backup (no medications)");
        }

        List<Medication> medications = new ArrayList<>();
        for (int i = 0; i < medsArray.length(); i++) {
            medications.add(Medication.fromJson(medsArray.getJSONObject(i)));
        }
        dataManager.replaceAll(medications);

        // Order: use the saved order where present, then append anything new
        List<String> order = new ArrayList<>();
        JSONArray orderArray = root.optJSONArray("order");
        if (orderArray != null) {
            for (int i = 0; i < orderArray.length(); i++) {
                order.add(orderArray.getString(i));
            }
        }
        for (Medication medication : medications) {
            if (!order.contains(medication.getName())) {
                order.add(medication.getName());
            }
        }
        order.retainAll(dataManager.getMedicationNames());
        orderManager.saveMedicationOrder(order);

        // Reminders (optional)
        JSONArray remindersArray = root.optJSONArray("reminders");
        if (remindersArray != null) {
            List<ReminderManager.Reminder> reminders = new ArrayList<>();
            for (int i = 0; i < remindersArray.length(); i++) {
                reminders.add(ReminderManager.Reminder.fromJson(remindersArray.getJSONObject(i)));
            }
            reminderManager.replaceAll(reminders);
        }

        Log.d(TAG, "Backup import complete: " + medications.size() + " medications");
        return medications.size();
    }

    private String readAll(Uri uri) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
