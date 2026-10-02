package com.clementine.medtracker.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import com.clementine.medtracker.models.Medication;
import com.clementine.medtracker.models.MedicationType;

/**
 * Dialog for adding a medication: name, dosage + unit, category, type, and an optional
 * "set up a reminder" toggle. When that toggle is on, the caller opens the full-screen
 * reminder editor after the medication is created (rather than a cramped inline picker).
 *
 * @author LuciCMD
 */
public class AddMedicationDialog {

    public interface OnMedicationAddedListener {
        void onMedicationAdded(String name, double dosage, String unit, String category, String type,
                               boolean setReminder);
    }

    // Category options offered when adding (Uncategorized is the default first choice)
    private static final String[] CATEGORIES = {
            Medication.CATEGORY_UNCATEGORIZED,
            Medication.CATEGORY_PRESCRIBED,
            Medication.CATEGORY_OTC,
            Medication.CATEGORY_RECREATIONAL
    };

    private final Context context;
    private final OnMedicationAddedListener listener;

    public AddMedicationDialog(Context context, OnMedicationAddedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show() {
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 20, 50, 20);

        // Medication name
        EditText nameInput = new EditText(context);
        nameInput.setHint("Medication Name");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mainLayout.addView(nameInput);

        // Dosage + unit (side by side)
        LinearLayout dosageRow = new LinearLayout(context);
        dosageRow.setOrientation(LinearLayout.HORIZONTAL);

        EditText dosageInput = new EditText(context);
        dosageInput.setHint("Dosage");
        dosageInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        dosageInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dosageRow.addView(dosageInput);

        Spinner unitSpinner = new Spinner(context);
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, Medication.UNITS);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
        dosageRow.addView(unitSpinner);

        mainLayout.addView(dosageRow);

        // Category
        TextView categoryLabel = new TextView(context);
        categoryLabel.setText("Category");
        mainLayout.addView(categoryLabel);

        Spinner categorySpinner = new Spinner(context);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        mainLayout.addView(categorySpinner);

        // Type
        TextView typeLabel = new TextView(context);
        typeLabel.setText("Type");
        mainLayout.addView(typeLabel);

        final MedicationType[] types = MedicationType.selectable();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].getDisplayName();
        }
        Spinner typeSpinner = new Spinner(context);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, typeNames);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        mainLayout.addView(typeSpinner);

        // Optional: open the reminder editor after adding
        CheckBox setReminderCheckBox = new CheckBox(context);
        setReminderCheckBox.setText("Set up a reminder after adding");
        mainLayout.addView(setReminderCheckBox);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(mainLayout);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Add New Medication")
                .setView(scrollView)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        addButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String dosageText = dosageInput.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter a medication name", Toast.LENGTH_SHORT).show();
                return;
            }

            double dosage = 0.0;
            if (!dosageText.isEmpty()) {
                try {
                    dosage = Double.parseDouble(dosageText);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid dosage value", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String unit = (String) unitSpinner.getSelectedItem();
            String category = CATEGORIES[categorySpinner.getSelectedItemPosition()];
            String type = types[typeSpinner.getSelectedItemPosition()].getKey();

            listener.onMedicationAdded(name, dosage, unit, category, type,
                    setReminderCheckBox.isChecked());
            dialog.dismiss();
        });
    }
}
