package com.clementine.medtracker.utils;

import android.content.Context;
import android.util.Log;
import java.util.List;
import java.util.ArrayList;
import com.clementine.medtracker.data.MedicationDataManager;
import com.clementine.medtracker.models.Medication;

/**
 * Handles medication-related operations and business logic
 * @author LuciCMD
 */
public class MedicationManager {
    private final MedicationDataManager dataManager;
    private final TimeManager timeManager;
    private final Context context;

    public interface MedicationUpdateListener {
        void onMedicationUpdated(String medicationName);
        void onMedicationError(String message);
    }

    private MedicationUpdateListener updateListener;

    public MedicationManager(Context context) {
        this.context = context;
        this.dataManager = MedicationDataManager.getInstance(context);
        this.timeManager = new TimeManager();
    }

    public void setUpdateListener(MedicationUpdateListener listener) {
        this.updateListener = listener;
    }

    public void addMedication(String name) {
        if (name == null || name.trim().isEmpty()) {
            notifyError("Medication name cannot be empty");
            return;
        }

        if (dataManager.getMedication(name) != null) {
            notifyError("Medication already exists");
            return;
        }

        dataManager.addMedication(name);
        notifyUpdate(name);
    }

    public void removeMedication(String name) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) {
            notifyError("Medication not found");
            return;
        }

        // Remove the medication itself
        dataManager.removeMedication(name);
        notifyUpdate(name);
    }

    public void takeMedication(String name, double fractionTaken) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) {
            notifyError("Medication not found");
            return;
        }

        double adjustedDosage = medication.getDosage() * fractionTaken;
        // Store the fraction so each ingredient's amount can be derived (ingredient.dosage * fraction)
        medication.addUsageRecord(adjustedDosage, fractionTaken);

        dataManager.updateMedication(medication);
        notifyUpdate(name);
    }

    public void undoLastTake(String name) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) {
            notifyError("Medication not found");
            return;
        }

        medication.removeLastUsageRecord();
        dataManager.updateMedication(medication);

        notifyUpdate(name);
    }

    public void updateDosage(String name, double newDosage) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) {
            notifyError("Medication not found");
            return;
        }

        if (newDosage < 0) {
            notifyError("Dosage cannot be negative");
            return;
        }

        medication.setDosage(newDosage);
        dataManager.updateMedication(medication);
        notifyUpdate(name);
    }
    
    public void updateMedication(Medication medication) {
        dataManager.updateMedication(medication);
        notifyUpdate(medication.getName());
    }

    public double getTotalDosageLast24Hours(String name) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) return 0.0;

        double total = 0.0;
        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

        for (Medication.UsageRecord record : medication.getUsageHistory()) {
            if (record.getTimestamp().getTime() > twentyFourHoursAgo) {
                total += record.getDosageTaken();
            }
        }

        return total;
    }
    
    /**
     * Sum of the dose fractions logged in the last 24h. Multiply by an ingredient's dosage
     * to get how much of that ingredient was taken over the window.
     */
    public double getTotalFractionLast24Hours(String name) {
        Medication medication = dataManager.getMedication(name);
        if (medication == null) return 0.0;

        double total = 0.0;
        long twentyFourHoursAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000);

        for (Medication.UsageRecord record : medication.getUsageHistory()) {
            if (record.getTimestamp().getTime() > twentyFourHoursAgo) {
                double fraction = record.getFraction();
                if (!Double.isNaN(fraction)) total += fraction;
            }
        }

        return total;
    }

    public List<String> getMedicationNames() {
        return dataManager.getMedicationNames();
    }

    public Medication getMedication(String name) {
        return dataManager.getMedication(name);
    }

    private void notifyUpdate(String medicationName) {
        if (updateListener != null) {
            updateListener.onMedicationUpdated(medicationName);
        }
    }

    private void notifyError(String message) {
        Log.e("MedicationManager", message);
        if (updateListener != null) {
            updateListener.onMedicationError(message);
        }
    }
}