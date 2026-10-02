package com.clementine.medtracker.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data model representing a medication and its usage history.
 *
 * A medication has one primary dosage plus an optional list of extra {@link Ingredient}s
 * (other drugs / vitamins bundled in the same pill). Every logged dose stores the
 * fraction taken (1/1, 1/2, ...); each ingredient's amount for a dose is its dosage
 * scaled by that fraction, so a combo or multivitamin is tracked from one tap.
 *
 * @author LuciCMD
 */
public class Medication {
    // Preset category keys. Anything else (or empty) is treated as uncategorized
    public static final String CATEGORY_UNCATEGORIZED = "Uncategorized";
    public static final String CATEGORY_PRESCRIBED = "Prescribed";
    public static final String CATEGORY_OTC = "OTC";
    public static final String CATEGORY_RECREATIONAL = "Recreational";

    /** Upper bound on extra ingredients, keeping the editor and stored JSON bounded. */
    public static final int MAX_INGREDIENTS = 10;

    /** Preset measurement units offered for the primary dose and each ingredient. */
    public static final String[] UNITS = {
            "mg", "mcg", "g", "mL", "IU", "mEq",
            "drop(s)", "spray(s)", "puff(s)", "tablet(s)", "capsule(s)", "unit(s)", "%"
    };
    public static final String DEFAULT_UNIT = "mg";

    private String name;
    private double dosage;
    private String unit;
    private final List<UsageRecord> usageHistory;
    private final List<Ingredient> ingredients;
    private String category;
    private String type;

    /** An additional active ingredient carried alongside the primary drug (name + dosage + unit). */
    public static class Ingredient {
        private final String name;
        private final double dosage;
        private final String unit;

        public Ingredient(String name, double dosage, String unit) {
            this.name = (name == null) ? "" : name.trim();
            this.dosage = dosage;
            this.unit = (unit == null || unit.trim().isEmpty()) ? DEFAULT_UNIT : unit;
        }

        public String getName() { return name; }
        public double getDosage() { return dosage; }
        public String getUnit() { return unit; }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("dosage", dosage);
            json.put("unit", unit);
            return json;
        }

        public static Ingredient fromJson(JSONObject json) throws JSONException {
            return new Ingredient(
                    json.optString("name", ""),
                    json.optDouble("dosage", 0),
                    json.optString("unit", DEFAULT_UNIT));
        }
    }

    public static class UsageRecord {
        private final Date timestamp;
        private final double dosageTaken;   // primary absolute amount = dosage * fraction
        private double fraction;            // multiplier used; ingredient amounts scale by this

        public UsageRecord(Date timestamp, double dosageTaken, double fraction) {
            this.timestamp = timestamp;
            this.dosageTaken = dosageTaken;
            this.fraction = fraction;
        }

        public Date getTimestamp() { return timestamp; }
        public double getDosageTaken() { return dosageTaken; }
        public double getFraction() { return fraction; }

        /** Migration hook: pre-3.1 records had no fraction; backfill it once from the primary. */
        void backfillFraction(double derivedFraction) {
            if (Double.isNaN(fraction)) {
                this.fraction = derivedFraction;
            }
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("timeTaken", timestamp.getTime());
            json.put("dosageTaken", dosageTaken);
            json.put("fraction", fraction);
            return json;
        }

        public static UsageRecord fromJson(JSONObject json) throws JSONException {
            // fraction is absent in pre-3.1 data -> NaN sentinel, backfilled by Medication.fromJson
            double fraction = json.has("fraction") ? json.getDouble("fraction") : Double.NaN;
            return new UsageRecord(
                    new Date(json.getLong("timeTaken")),
                    json.getDouble("dosageTaken"),
                    fraction
            );
        }
    }

    public Medication(String name) {
        this.name = name;
        this.dosage = 0;
        this.unit = DEFAULT_UNIT;
        this.usageHistory = new ArrayList<>();
        this.ingredients = new ArrayList<>();
        this.category = CATEGORY_UNCATEGORIZED;
        this.type = MedicationType.UNSPECIFIED.getKey();
    }

    // Getters and setters
    public String getName() { return name; }
    public double getDosage() { return dosage; }
    public void setDosage(double dosage) { this.dosage = dosage; }
    public String getUnit() {
        return (unit == null || unit.trim().isEmpty()) ? DEFAULT_UNIT : unit;
    }
    public void setUnit(String unit) { this.unit = unit; }
    public List<UsageRecord> getUsageHistory() { return usageHistory; }

    public String getCategory() {
        return (category == null || category.trim().isEmpty()) ? CATEGORY_UNCATEGORIZED : category;
    }
    public void setCategory(String category) { this.category = category; }

    public String getType() {
        return (type == null || type.trim().isEmpty()) ? MedicationType.UNSPECIFIED.getKey() : type;
    }
    public void setType(String type) { this.type = type; }

    public List<Ingredient> getIngredients() { return ingredients; }

    public boolean hasIngredients() { return !ingredients.isEmpty(); }

    /** Replaces the ingredient list, dropping blanks and enforcing {@link #MAX_INGREDIENTS}. */
    public void setIngredients(List<Ingredient> newIngredients) {
        ingredients.clear();
        if (newIngredients != null) {
            for (Ingredient ingredient : newIngredients) {
                if (ingredients.size() >= MAX_INGREDIENTS) break;
                if (ingredient != null && !ingredient.getName().isEmpty()) {
                    ingredients.add(ingredient);
                }
            }
        }
    }

    public void addUsageRecord(double dosageTaken, double fraction) {
        usageHistory.add(new UsageRecord(new Date(), dosageTaken, fraction));
    }

    public void removeLastUsageRecord() {
        if (!usageHistory.isEmpty()) {
            usageHistory.remove(usageHistory.size() - 1);
        }
    }

    public UsageRecord getLastUsageRecord() {
        if (!usageHistory.isEmpty()) {
            return usageHistory.get(usageHistory.size() - 1);
        }
        return null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("dosage", dosage);
        json.put("unit", getUnit());
        json.put("category", getCategory());
        json.put("type", getType());

        JSONArray ingredientsArray = new JSONArray();
        for (Ingredient ingredient : ingredients) {
            ingredientsArray.put(ingredient.toJson());
        }
        json.put("ingredients", ingredientsArray);

        JSONArray historyArray = new JSONArray();
        for (UsageRecord record : usageHistory) {
            historyArray.put(record.toJson());
        }
        json.put("usageHistory", historyArray);

        return json;
    }

    public static Medication fromJson(JSONObject json) throws JSONException {
        Medication medication = new Medication(json.getString("name"));
        medication.setDosage(json.optDouble("dosage", 0));
        medication.setUnit(json.optString("unit", DEFAULT_UNIT));
        // optString defaults keep older saved data (no category/type) loading cleanly
        medication.setCategory(json.optString("category", CATEGORY_UNCATEGORIZED));
        medication.setType(json.optString("type", MedicationType.UNSPECIFIED.getKey()));

        // Ingredients: read the new "ingredients" array, else migrate the legacy single secondary drug
        JSONArray ingredientsArray = json.optJSONArray("ingredients");
        if (ingredientsArray != null) {
            for (int i = 0; i < ingredientsArray.length()
                    && medication.ingredients.size() < MAX_INGREDIENTS; i++) {
                Ingredient ingredient = Ingredient.fromJson(ingredientsArray.getJSONObject(i));
                if (!ingredient.getName().isEmpty()) medication.ingredients.add(ingredient);
            }
        } else {
            String legacyName = json.optString("secondaryDrugName", "");
            if (!legacyName.trim().isEmpty()) {
                medication.ingredients.add(new Ingredient(
                        legacyName, json.optDouble("secondaryDrugDosage", 0), DEFAULT_UNIT));
            }
        }

        JSONArray historyArray = json.optJSONArray("usageHistory");
        if (historyArray != null) {
            for (int i = 0; i < historyArray.length(); i++) {
                medication.usageHistory.add(
                        UsageRecord.fromJson(historyArray.getJSONObject(i)));
            }
        }

        // Backfill missing fractions (pre-3.1 records) from the primary so ingredient totals
        // reproduce cleanly. Derive from the primary dose; fall back to a full dose if unknown.
        double dosage = medication.getDosage();
        for (UsageRecord record : medication.usageHistory) {
            double derived = (dosage > 0) ? (record.getDosageTaken() / dosage) : 1.0;
            record.backfillFraction(derived);
        }

        return medication;
    }
}
