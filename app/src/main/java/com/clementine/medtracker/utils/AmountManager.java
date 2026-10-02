package com.clementine.medtracker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists dose-amount state: a small set of reusable multiplier <b>presets</b> (shown in the
 * dropdown next to the Amount field) and the <b>last amount used per medication</b>, so each
 * medication remembers its own multiplier when you switch between them.
 *
 * Stored in its own SharedPreferences file, separate from medication data, so updating an
 * amount never churns the medication list or triggers a refresh.
 *
 * @author LuciCMD
 */
public class AmountManager {
    private static final String TAG = "AmountManager";
    private static final String PREFS = "AmountPrefs";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_LAST = "last_amounts";
    private static final int MAX_PRESETS = 12;
    private static final double EPS = 1e-9;

    private final SharedPreferences prefs;

    public AmountManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Saved multiplier presets, sorted ascending. Seeds sensible defaults on first use. */
    public List<Double> getPresets() {
        String json = prefs.getString(KEY_PRESETS, null);
        if (json == null) {
            List<Double> defaults = new ArrayList<>();
            Collections.addAll(defaults, 0.25, 0.5, 1.0, 2.0);
            savePresets(defaults);
            return defaults;
        }
        List<Double> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) list.add(array.getDouble(i));
        } catch (JSONException e) {
            Log.e(TAG, "Error reading presets", e);
        }
        return list;
    }

    private void savePresets(List<Double> presets) {
        try {
            JSONArray array = new JSONArray();
            for (Double value : presets) array.put((double) value);
            prefs.edit().putString(KEY_PRESETS, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving presets", e);
        }
    }

    public void addPreset(double value) {
        if (value <= 0) return;
        List<Double> presets = getPresets();
        for (Double existing : presets) {
            if (Math.abs(existing - value) < EPS) return;   // already saved
        }
        if (presets.size() >= MAX_PRESETS) return;
        presets.add(value);
        Collections.sort(presets);
        savePresets(presets);
    }

    public void removePreset(double value) {
        List<Double> presets = getPresets();
        presets.removeIf(existing -> Math.abs(existing - value) < EPS);
        savePresets(presets);
    }

    /** The multiplier last used for {@code medicationName} (defaults to a full dose, 1.0). */
    public double getLastAmount(String medicationName) {
        if (medicationName == null) return 1.0;
        try {
            JSONObject map = new JSONObject(prefs.getString(KEY_LAST, "{}"));
            return map.optDouble(medicationName, 1.0);
        } catch (JSONException e) {
            return 1.0;
        }
    }

    public void setLastAmount(String medicationName, double value) {
        if (medicationName == null || value <= 0) return;
        try {
            JSONObject map = new JSONObject(prefs.getString(KEY_LAST, "{}"));
            map.put(medicationName, value);
            prefs.edit().putString(KEY_LAST, map.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving last amount", e);
        }
    }
}
