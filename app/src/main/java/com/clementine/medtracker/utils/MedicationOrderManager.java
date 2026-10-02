package com.clementine.medtracker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the persistence of medication order in the list
 * @author LuciCMD
 */
public class MedicationOrderManager {
    private static final String PREFS_NAME = "MedTrackerOrder";
    private static final String ORDER_KEY = "medication_order";
    private final SharedPreferences prefs;

    public MedicationOrderManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveMedicationOrder(List<String> medications) {
        try {
            JSONArray orderArray = new JSONArray(medications);
            prefs.edit().putString(ORDER_KEY, orderArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> loadMedicationOrder() {
        List<String> order = new ArrayList<>();
        try {
            String orderJson = prefs.getString(ORDER_KEY, "[]");
            JSONArray orderArray = new JSONArray(orderJson);
            for (int i = 0; i < orderArray.length(); i++) {
                order.add(orderArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return order;
    }

    public void addMedication(String medicationName) {
        List<String> currentOrder = loadMedicationOrder();
        if (!currentOrder.contains(medicationName)) {
            currentOrder.add(medicationName);
            saveMedicationOrder(currentOrder);
        }
    }

    public void removeMedication(String medicationName) {
        List<String> currentOrder = loadMedicationOrder();
        currentOrder.remove(medicationName);
        saveMedicationOrder(currentOrder);
    }
}