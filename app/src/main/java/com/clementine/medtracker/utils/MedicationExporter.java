package com.clementine.medtracker.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import com.clementine.medtracker.data.MedicationDataManager;
import com.clementine.medtracker.models.Medication;

/**
 * Handles exporting medication data to various formats
 * @author LuciCMD
 */
public class MedicationExporter {
    private static final String TAG = "MedicationExporter";
    private final Context context;
    private final MedicationDataManager dataManager;
    private final SimpleDateFormat dateFormat;

    public MedicationExporter(Context context) {
        this.context = context;
        this.dataManager = MedicationDataManager.getInstance(context);
        this.dateFormat = TimeManager.getDateFormat();
    }

    public void exportToCSV(Uri uri) throws IOException {
        Log.d(TAG, "Starting CSV export to: " + uri);

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {

            // Write CSV header
            writer.write("Medication,Time Taken,Dosage Taken,Ingredients Taken,Daily Total\n");

            // Export each medication's history
            List<String> medicationNames = dataManager.getMedicationNames();
            for (String name : medicationNames) {
                Medication medication = dataManager.getMedication(name);
                if (medication != null) {
                    exportMedicationHistory(writer, medication);
                }
            }

            writer.flush();
            Log.d(TAG, "CSV export completed successfully");

        } catch (IOException e) {
            Log.e(TAG, "Error during CSV export", e);
            throw e;
        }
    }

    private void exportMedicationHistory(BufferedWriter writer, Medication medication) throws IOException {
        double dailyTotal = 0;
        List<Medication.UsageRecord> history = medication.getUsageHistory();
        List<Medication.Ingredient> ingredients = medication.getIngredients();

        for (int i = 0; i < history.size(); i++) {
            Medication.UsageRecord record = history.get(i);
            dailyTotal += record.getDosageTaken();

            String unit = medication.getUnit();
            String line = String.format(Locale.US, "%s,%s,%s,%s,%s\n",
                    csvCell(medication.getName()),
                    dateFormat.format(record.getTimestamp()),
                    csvCell(formatDosage(record.getDosageTaken()) + " " + unit),
                    formatIngredientsTaken(ingredients, record.getFraction()),
                    csvCell(formatDosage(dailyTotal) + " " + unit)
            );

            writer.write(line);

            // Reset the running daily total if the next record is from a different day
            if (i < history.size() - 1) {
                Medication.UsageRecord nextRecord = history.get(i + 1);
                if (!isSameDay(record.getTimestamp(), nextRecord.getTimestamp())) {
                    dailyTotal = 0;
                }
            }
        }
    }

    /** Builds one CSV cell like "Caffeine=25 mg; B12=5 mcg" from the ingredients scaled by the dose fraction. */
    private String formatIngredientsTaken(List<Medication.Ingredient> ingredients, double fraction) {
        if (ingredients.isEmpty()) return "";
        double f = Double.isNaN(fraction) ? 0 : fraction;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ingredients.size(); i++) {
            if (i > 0) builder.append("; ");
            Medication.Ingredient ingredient = ingredients.get(i);
            builder.append(ingredient.getName())
                   .append("=")
                   .append(formatDosage(ingredient.getDosage() * f))
                   .append(" ")
                   .append(ingredient.getUnit());
        }
        return csvCell(builder.toString());
    }

    /** Quotes a value if it contains a comma, quote or newline so the CSV stays well-formed. */
    private String csvCell(String value) {
        if (value == null) value = "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean isSameDay(java.util.Date date1, java.util.Date date2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        return sdf.format(date1).equals(sdf.format(date2));
    }

    private String formatDosage(double dosage) {
        if (dosage == 0.0) {
            return "0";
        }
        // Remove trailing zeros and decimal point if not needed
        return String.format(Locale.US, "%.6f", dosage).replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    /**
     * Export data to JSON format (for future implementation)
     */
    public void exportToJSON(Uri uri) throws IOException {
        // Implementation for JSON export format
        throw new UnsupportedOperationException("JSON export not yet implemented");
    }

    /**
     * Export data to PDF format (for future implementation)
     */
    public void exportToPDF(Uri uri) throws IOException {
        // Implementation for PDF export format
        throw new UnsupportedOperationException("PDF export not yet implemented");
    }
}