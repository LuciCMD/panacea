package com.clementine.medtracker.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.clementine.medtracker.models.Medication;

/**
 * Manages medication data persistence and retrieval using SharedPreferences
 * @author LuciCMD
 */
public class MedicationDataManager {
    private static final String PREFS_NAME = "MedTracker";
    private static final String MEDICATION_DATA_KEY = "medication_data";
    private static MedicationDataManager instance;
    private final SharedPreferences sharedPrefs;
    private final Map<String, Medication> medicationCache;

    private MedicationDataManager(Context context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        medicationCache = new HashMap<>();
        loadMedications();
    }

    public static synchronized MedicationDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new MedicationDataManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadMedications() {
        String medicationDataJson = sharedPrefs.getString(MEDICATION_DATA_KEY, "{}");
        try {
            JSONObject medicationData = new JSONObject(medicationDataJson);
            Iterator<String> keys = medicationData.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                JSONObject medicationJson = medicationData.getJSONObject(name);
                medicationCache.put(name, Medication.fromJson(medicationJson));
            }
        } catch (JSONException e) {
            Log.e("MedicationDataManager", "Error loading medications", e);
        }
    }

    private void saveMedications() {
        try {
            JSONObject medicationData = new JSONObject();
            for (Map.Entry<String, Medication> entry : medicationCache.entrySet()) {
                medicationData.put(entry.getKey(), entry.getValue().toJson());
            }
            sharedPrefs.edit()
                    .putString(MEDICATION_DATA_KEY, medicationData.toString())
                    .apply();
        } catch (JSONException e) {
            Log.e("MedicationDataManager", "Error saving medications", e);
        }
    }

    public List<String> getMedicationNames() {
        return new ArrayList<>(medicationCache.keySet());
    }

    public List<Medication> getAllMedications() {
        return new ArrayList<>(medicationCache.values());
    }

    public Medication getMedication(String name) {
        return medicationCache.get(name);
    }

    /** Wholesale replace used by JSON restore. Clears the cache and rewrites storage once. */
    public void replaceAll(List<Medication> medications) {
        medicationCache.clear();
        for (Medication medication : medications) {
            medicationCache.put(medication.getName(), medication);
        }
        saveMedications();
    }

    public void addMedication(String name) {
        if (!medicationCache.containsKey(name)) {
            medicationCache.put(name, new Medication(name));
            saveMedications();
        }
    }

    public void removeMedication(String name) {
        if (medicationCache.remove(name) != null) {
            saveMedications();
        }
    }

    public void updateMedication(Medication medication) {
        medicationCache.put(medication.getName(), medication);
        saveMedications();
    }
}