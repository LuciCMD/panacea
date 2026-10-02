package com.clementine.medtracker;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import java.util.Arrays;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.clementine.medtracker.adapters.MedicationAdapter;
import com.clementine.medtracker.utils.MedicationItemTouchHelper;
import com.clementine.medtracker.utils.MedicationOrderManager;
import com.clementine.medtracker.models.Medication;
import com.clementine.medtracker.models.MedicationType;
import com.clementine.medtracker.utils.MedicationManager;
import com.clementine.medtracker.utils.TimeManager;
import com.clementine.medtracker.utils.MedicationExporter;
import com.clementine.medtracker.utils.BackupManager;
import com.clementine.medtracker.utils.ReminderManager;
import com.clementine.medtracker.utils.ThemeManager;
import com.clementine.medtracker.utils.AmountManager;
import com.clementine.medtracker.dialogs.AddMedicationDialog;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main Activity for the Panacea application
 * A medication tracking application designed to help users manage their medication intake
 * @author LuciCMD
 */
public class MainActivity extends AppCompatActivity implements
        MedicationManager.MedicationUpdateListener,
        TimeManager.TimeUpdateListener,
        MedicationAdapter.OnStartDragListener,
        MedicationAdapter.OnMedicationSelectedListener,
        MedicationItemTouchHelper.OnMedicationOrderChangeListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Category tabs. "All" shows everything; the rest filter by Medication.getCategory()
    private static final String CATEGORY_ALL = "All";
    private static final String[] CATEGORY_TABS = {
            CATEGORY_ALL,
            Medication.CATEGORY_PRESCRIBED,
            Medication.CATEGORY_OTC,
            Medication.CATEGORY_RECREATIONAL,
            Medication.CATEGORY_UNCATEGORIZED
    };

    private boolean isRearrangeMode = false;
    private String currentCategory = CATEGORY_ALL;

    // UI Components
    private RecyclerView medicationList;
    private Button gobbleButton;
    private TextView lastTimeTaken;
    private TextView timeSinceLastTaken;
    private TextView lastTakenMg;
    private EditText amountInput;
    private Button ingredientsButton;
    private TabLayout categoryTabs;
    private MedicationAdapter medicationAdapter;
    private ItemTouchHelper itemTouchHelper;

    // Media Players for sound effects
    private MediaPlayer mediaPlayer;
    private MediaPlayer mediaPlayerUndo;

    // Managers
    private MedicationManager medicationManager;
    private TimeManager timeManager;
    private MedicationExporter medicationExporter;
    private BackupManager backupManager;
    private MedicationOrderManager orderManager;
    private ReminderManager reminderManager;
    private ThemeManager themeManager;
    private AmountManager amountManager;

    // Medication whose amount multiplier the field currently reflects (for per-med memory)
    private String amountMemoryMed;

    // Storage Access Framework launchers (modern replacement for startActivityForResult)
    private ActivityResultLauncher<String> csvExportLauncher;
    private ActivityResultLauncher<String> backupExportLauncher;
    private ActivityResultLauncher<String[]> backupImportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before calling super.onCreate or setContentView
        themeManager = new ThemeManager(this);
        themeManager.applyTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));
        setToolbarTitle("Select a medication");  // no app name; the bar shows the selected dose
        applyWindowInsets();

        registerDocumentLaunchers();

        // Check and request necessary permissions
        checkAndRequestPermissions();

        // Initialize managers
        initializeManagers();

        // Set up UI components
        initializeViews();
        setupCategoryTabs();
        setupListeners();
        initializeMediaPlayers();

        // Set up adapters
        setupAdapters();

        // Set up back press handling
        setupBackHandler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_medication_options) {
            showMedicationOptions();
            return true;
        } else if (id == R.id.action_add_medication) {
            showAddMedicationDialog();
            return true;
        } else if (id == R.id.action_rearrange) {
            isRearrangeMode = !isRearrangeMode;
            toggleDragAndDrop(isRearrangeMode);
            return true;
        } else if (id == R.id.action_export) {
            exportMedicationData();
            return true;
        } else if (id == R.id.action_backup) {
            backupData();
            return true;
        } else if (id == R.id.action_import) {
            confirmImportData();
            return true;
        } else if (id == R.id.action_reminders) {
            startActivity(new Intent(this, RemindersActivity.class));
            return true;
        } else if (id == R.id.action_themes) {
            showThemeSelector();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMedicationOptions() {
        String selectedMedication = medicationAdapter.getSelectedMedication();
        if (selectedMedication == null) {
            Toast.makeText(this, "Please select a medication first", Toast.LENGTH_SHORT).show();
            return;
        }

        View menuItemView = findViewById(R.id.action_medication_options);
        if (menuItemView == null) {
            menuItemView = findViewById(android.R.id.content);
        }

        PopupMenu popup = new PopupMenu(this, menuItemView);
        popup.inflate(R.menu.context_menu);
        popup.setOnMenuItemClickListener(item -> handleMedicationMenuAction(item, selectedMedication));
        popup.setGravity(Gravity.END);
        popup.show();
    }

    /**
     * Apps targeting Android 15 (SDK 35) are forced edge-to-edge, so content would draw
     * under the status bar and the gesture/navigation bar. Pad the root by the system-bar
     * (and display-cutout) insets to keep everything within the visible bounds.
     */
    private void applyWindowInsets() {
        View root = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // In rearrange mode, Back simply leaves rearrange mode (no need to revisit the menu)
                if (isRearrangeMode) {
                    isRearrangeMode = false;
                    toggleDragAndDrop(false);
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Exit Application")
                        .setMessage("Are you sure you want to exit?")
                        .setPositiveButton("Yes", (dialog, which) -> finishAffinity())
                        .setNegativeButton("No", null)
                        .show();
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }

            if (!hasExactAlarmPermission()) {
                requestExactAlarmPermission();
            }
        }
    }

    private boolean hasExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getSystemService(AlarmManager.class).canScheduleExactAlarms();
        }
        return true;
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void initializeManagers() {
        medicationManager = new MedicationManager(this);
        timeManager = new TimeManager();
        medicationExporter = new MedicationExporter(this);
        backupManager = new BackupManager(this);
        orderManager = new MedicationOrderManager(this);
        reminderManager = new ReminderManager(this);
        amountManager = new AmountManager(this);

        medicationManager.setUpdateListener(this);
        timeManager.setUpdateListener(this);
    }

    private void initializeViews() {
        medicationList = findViewById(R.id.medication_list);
        gobbleButton = findViewById(R.id.gobble_button);
        lastTimeTaken = findViewById(R.id.last_time_taken);
        timeSinceLastTaken = findViewById(R.id.time_since_last_taken);
        lastTakenMg = findViewById(R.id.last_taken_mg);
        amountInput = findViewById(R.id.amount_input);
        ingredientsButton = findViewById(R.id.ingredients_button);
        ingredientsButton.setOnClickListener(this::showIngredientsPopup);
        findViewById(R.id.amount_presets_button).setOnClickListener(this::showAmountPresetsMenu);
        categoryTabs = findViewById(R.id.category_tabs);

        // Initialize RecyclerView
        medicationList.setLayoutManager(new LinearLayoutManager(this));
        medicationAdapter = new MedicationAdapter(this, this);
        // Resolve each row's type icon from the data layer
        medicationAdapter.setTypeIconResolver(name -> {
            Medication medication = medicationManager.getMedication(name);
            String typeKey = medication != null ? medication.getType() : null;
            return MedicationType.fromKey(typeKey).getIconRes();
        });
        medicationList.setAdapter(medicationAdapter);

        // Set up drag and drop
        MedicationItemTouchHelper callback = new MedicationItemTouchHelper(medicationAdapter, this);
        itemTouchHelper = new ItemTouchHelper(callback);

        registerForContextMenu(medicationList);
    }

    private void setupCategoryTabs() {
        for (String category : CATEGORY_TABS) {
            categoryTabs.addTab(categoryTabs.newTab().setText(category));
        }
        categoryTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentCategory = CATEGORY_TABS[tab.getPosition()];
                // Reordering only makes sense against the full list
                if (isRearrangeMode && !CATEGORY_ALL.equals(currentCategory)) {
                    isRearrangeMode = false;
                    toggleDragAndDrop(false);
                }
                refreshMedicationList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupListeners() {
        gobbleButton.setOnClickListener(v -> {
            gobbleButton.setEnabled(false);
            takeMedication();
            new Handler(Looper.getMainLooper()).postDelayed(() -> gobbleButton.setEnabled(true), 5000);
        });
    }

    private void initializeMediaPlayers() {
        mediaPlayer = MediaPlayer.create(this, R.raw.gobble);
        mediaPlayerUndo = MediaPlayer.create(this, R.raw.undo);
    }

    private void setupAdapters() {
        // Seed the saved order if it's empty, then show the current category
        List<String> medicationOrder = orderManager.loadMedicationOrder();
        if (medicationOrder.isEmpty()) {
            orderManager.saveMedicationOrder(medicationManager.getMedicationNames());
        }
        refreshMedicationList();
    }

    private void registerDocumentLaunchers() {
        csvExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri == null) return;
                    try {
                        medicationExporter.exportToCSV(uri);
                        Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "CSV export failed", e);
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        backupExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null) return;
                    try {
                        backupManager.exportBackup(uri);
                        Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Backup failed", e);
                        Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        backupImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    try {
                        int count = backupManager.importBackup(uri);
                        refreshMedicationList();
                        Toast.makeText(this, "Restored " + count + " medications", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Import failed", e);
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeNotifications();
            } else {
                Toast.makeText(this, "Some features may be limited without notifications",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(this);
        }
    }

    private void showAddMedicationDialog() {
        AddMedicationDialog dialog = new AddMedicationDialog(this,
            (name, dosage, unit, category, type, setReminder) -> {
                if (medicationManager.getMedication(name) != null) {
                    Toast.makeText(this, "\"" + name + "\" already exists", Toast.LENGTH_SHORT).show();
                    return;
                }

                medicationManager.addMedication(name);

                Medication medication = medicationManager.getMedication(name);
                if (medication != null) {
                    medication.setDosage(dosage);
                    medication.setUnit(unit);
                    medication.setCategory(category);
                    medication.setType(type);
                    medicationManager.updateMedication(medication);
                }

                orderManager.addMedication(name);
                refreshMedicationList();

                Toast.makeText(this, "Medication \"" + name + "\" added successfully!",
                    Toast.LENGTH_SHORT).show();

                // Continue into the full-screen reminder editor if requested
                if (setReminder) {
                    openReminderEditor(name, -1);
                }
            });
        dialog.show();
    }

    private void showThemeSelector() {
        String[] themeNames = ThemeManager.getThemeNames();
        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Theme")
               .setSingleChoiceItems(themeNames, currentTheme.getId(),
                   (dialog, which) -> {
                       ThemeManager.Theme selectedTheme = ThemeManager.Theme.fromId(which);
                       themeManager.setTheme(selectedTheme);
                       recreate();
                       dialog.dismiss();
                   })
               .setNegativeButton("Cancel", null)
               .show();
    }

    /** Opens the full-screen reminder editor (reminderId -1 = add a new one). */
    private void openReminderEditor(String medicationName, int reminderId) {
        Intent intent = new Intent(this, ReminderEditorActivity.class);
        intent.putExtra(ReminderEditorActivity.EXTRA_MED, medicationName);
        intent.putExtra(ReminderEditorActivity.EXTRA_REMINDER_ID, reminderId);
        startActivity(intent);
    }


    private void takeMedication() {
        String selectedMedication = medicationAdapter.getSelectedMedication();
        if (selectedMedication != null) {
            double amount = parseAmount();
            medicationManager.takeMedication(selectedMedication, amount);
            amountManager.setLastAmount(selectedMedication, amount);

            // Taking it in-app satisfies any pending reminder: clear its ongoing notification
            reminderManager.cancelNotificationsForMedication(selectedMedication);

            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        } else {
            Toast.makeText(this, "No medication selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoMedication(String medicationName) {
        if (medicationName != null) {
            medicationManager.undoLastTake(medicationName);

            if (mediaPlayerUndo != null) {
                mediaPlayerUndo.start();
            }
        } else {
            Toast.makeText(this, "No medication selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMedicationDisplay(String medicationName) {
        Medication medication = medicationManager.getMedication(medicationName);
        if (medication != null) {
            Medication.UsageRecord lastRecord = medication.getLastUsageRecord();

            // Dosage is shown in the header bar itself (medication name lives in the selected list row)
            setToolbarTitle(String.format("Dosage: %s %s",
                    formatDosage(medication.getDosage()), medication.getUnit()));
            ingredientsButton.setVisibility(medication.hasIngredients() ? View.VISIBLE : View.GONE);

            double primaryAmount = medicationManager.getTotalDosageLast24Hours(medicationName);
            lastTakenMg.setText(String.format("Amount Taken (24h): %s %s",
                    formatDosage(primaryAmount), medication.getUnit()));

            if (lastRecord != null) {
                String timestamp = timeManager.getDateFormat().format(lastRecord.getTimestamp());
                lastTimeTaken.setText("Last Gobbled: " + timestamp);
                timeManager.startTimeUpdates(timestamp);
            } else {
                lastTimeTaken.setText("Last Gobbled: N/A");
                timeSinceLastTaken.setText("Time Since Gobbled: N/A");
                timeManager.stopTimeUpdates();
            }
        }
    }

    private void setToolbarTitle(String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title == null ? "" : title);
        }
    }

    /** Drops down the full ingredient list (dose + 24h amount each) for the selected medication. */
    private void showIngredientsPopup(View anchor) {
        String selected = medicationAdapter.getSelectedMedication();
        Medication medication = (selected == null) ? null : medicationManager.getMedication(selected);
        if (medication == null || !medication.hasIngredients()) return;

        double fractionSum = medicationManager.getTotalFractionLast24Hours(selected);
        PopupMenu popup = new PopupMenu(this, anchor);
        // Header row (disabled) then one row per ingredient: "Name — dose unit  (24h: amt unit)"
        popup.getMenu().add(medication.getName() + " ingredients").setEnabled(false);
        for (Medication.Ingredient ingredient : medication.getIngredients()) {
            String line = String.format("%s — %s %s  (24h: %s %s)",
                    ingredient.getName(),
                    formatDosage(ingredient.getDosage()), ingredient.getUnit(),
                    formatDosage(ingredient.getDosage() * fractionSum), ingredient.getUnit());
            popup.getMenu().add(line).setEnabled(false);
        }
        popup.show();
    }

    private void exportMedicationData() {
        csvExportLauncher.launch("medication_data.csv");
    }

    private void backupData() {
        backupExportLauncher.launch("panacea_backup.json");
    }

    private void confirmImportData() {
        new AlertDialog.Builder(this)
                .setTitle("Restore from backup?")
                .setMessage("This replaces your current medications, order and reminders with the contents of the backup file. Your current data will be overwritten.\n\nTip: make a fresh backup first if you're unsure.")
                .setPositiveButton("Choose File", (dialog, which) ->
                        backupImportLauncher.launch(new String[]{"application/json", "application/octet-stream", "text/plain", "*/*"}))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reads the custom amount multiplier; defaults to a full dose (1.0) if blank/invalid/&le;0. */
    private double parseAmount() {
        String text = amountInput.getText().toString().trim();
        if (text.isEmpty()) return 1.0;
        try {
            double value = Double.parseDouble(text);
            return (value > 0) ? value : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /** Dropdown next to the amount field: apply a saved preset, save the current value, or remove one. */
    private void showAmountPresetsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        List<Double> presets = amountManager.getPresets();
        for (int i = 0; i < presets.size(); i++) {
            popup.getMenu().add(1, i, i, "× " + formatDosage(presets.get(i)));
        }
        popup.getMenu().add(2, 1000, 1000, "Save current value");
        if (!presets.isEmpty()) popup.getMenu().add(2, 1001, 1001, "Remove a preset…");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == 1) {
                amountInput.setText(formatDosage(presets.get(item.getItemId())));
                return true;
            } else if (item.getItemId() == 1000) {
                double value = parseAmount();
                amountManager.addPreset(value);
                Toast.makeText(this, "Saved preset × " + formatDosage(value), Toast.LENGTH_SHORT).show();
                return true;
            } else if (item.getItemId() == 1001) {
                showRemovePresetDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRemovePresetDialog() {
        List<Double> presets = amountManager.getPresets();
        if (presets.isEmpty()) return;
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) labels[i] = "× " + formatDosage(presets.get(i));
        new AlertDialog.Builder(this)
                .setTitle("Remove a preset")
                .setItems(labels, (dialog, which) -> {
                    amountManager.removePreset(presets.get(which));
                    Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatDosage(double dosage) {
        if (dosage == 0.0) {
            return "0";
        }
        return String.format(Locale.US, "%.6f", dosage).replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    /** Loads the saved order, reconciles it against current meds, then shows only the active category. */
    private void refreshMedicationList() {
        List<String> orderedMedications = orderManager.loadMedicationOrder();
        List<String> allMedications = medicationManager.getMedicationNames();
        for (String med : allMedications) {
            if (!orderedMedications.contains(med)) {
                orderedMedications.add(med);
            }
        }
        orderedMedications.retainAll(allMedications);
        orderManager.saveMedicationOrder(orderedMedications);

        medicationAdapter.setMedications(filterByCategory(orderedMedications));
        syncDetailPanel();
    }

    /** Keeps the bottom detail panel in step with the current selection after any data change. */
    private void syncDetailPanel() {
        String selected = medicationAdapter.getSelectedMedication();
        if (selected != null && medicationManager.getMedication(selected) != null) {
            updateMedicationDisplay(selected);
        } else {
            clearDetailPanel();
        }
    }

    private void clearDetailPanel() {
        timeManager.stopTimeUpdates();
        setToolbarTitle("Select a medication");
        ingredientsButton.setVisibility(View.GONE);
        lastTimeTaken.setText("Last Gobbled: N/A");
        timeSinceLastTaken.setText("Time Since Gobbled: N/A");
        lastTakenMg.setText("Amount Taken (24h): N/A");
    }

    private List<String> filterByCategory(List<String> names) {
        if (CATEGORY_ALL.equals(currentCategory)) {
            return names;
        }
        List<String> filtered = new ArrayList<>();
        for (String name : names) {
            Medication medication = medicationManager.getMedication(name);
            if (medication != null && currentCategory.equals(medication.getCategory())) {
                filtered.add(name);
            }
        }
        return filtered;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (!isRearrangeMode) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        String medicationName = medicationAdapter.getSelectedMedication();
        if (medicationName == null) {
            Toast.makeText(this, "No medication selected", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (handleMedicationMenuAction(item, medicationName)) {
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /** Single handler shared by the long-press context menu and the options popup. */
    public boolean handleMedicationMenuAction(MenuItem item, String medicationName) {
        int id = item.getItemId();
        if (id == R.id.undo) {
            undoMedication(medicationName);
            return true;
        } else if (id == R.id.dosage) {
            showDosageDialog(medicationName);
            return true;
        } else if (id == R.id.set_category) {
            showCategoryDialog(medicationName);
            return true;
        } else if (id == R.id.set_type) {
            showTypeDialog(medicationName);
            return true;
        } else if (id == R.id.manage_ingredients) {
            showIngredientsDialog(medicationName);
            return true;
        } else if (id == R.id.remove) {
            medicationManager.removeMedication(medicationName);
            orderManager.removeMedication(medicationName);
            reminderManager.removeMedicationReminders(medicationName);
            refreshMedicationList();
            return true;
        }
        return false;
    }

    private void showDosageDialog(String medicationName) {
        Medication medication = medicationManager.getMedication(medicationName);
        if (medication == null) {
            Toast.makeText(this, "Medication not found", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Dosage");
        if (medication.getDosage() > 0) input.setText(formatDosage(medication.getDosage()));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        layout.addView(input);

        final Spinner unitSpinner = buildUnitSpinner(medication.getUnit());
        layout.addView(unitSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Set Dosage for " + medicationName)
                .setView(layout)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        double newDosage = Double.parseDouble(input.getText().toString());
                        medication.setDosage(newDosage);
                        medication.setUnit((String) unitSpinner.getSelectedItem());
                        medicationManager.updateMedication(medication);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid dosage value", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    /** A unit dropdown seeded from {@link Medication#UNITS}, with {@code selected} pre-chosen. */
    private Spinner buildUnitSpinner(String selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Medication.UNITS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < Medication.UNITS.length; i++) {
            if (Medication.UNITS[i].equals(selected)) {
                spinner.setSelection(i);
                break;
            }
        }
        return spinner;
    }

    private void showCategoryDialog(String medicationName) {
        Medication medication = medicationManager.getMedication(medicationName);
        if (medication == null) {
            Toast.makeText(this, "Medication not found", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] categories = {
                Medication.CATEGORY_PRESCRIBED,
                Medication.CATEGORY_OTC,
                Medication.CATEGORY_RECREATIONAL,
                Medication.CATEGORY_UNCATEGORIZED
        };
        int checked = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(medication.getCategory())) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Category for " + medicationName)
                .setSingleChoiceItems(categories, checked, (dialog, which) -> {
                    medication.setCategory(categories[which]);
                    medicationManager.updateMedication(medication);
                    refreshMedicationList();
                    Toast.makeText(this, "Category set to " + categories[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTypeDialog(String medicationName) {
        Medication medication = medicationManager.getMedication(medicationName);
        if (medication == null) {
            Toast.makeText(this, "Medication not found", Toast.LENGTH_SHORT).show();
            return;
        }

        final MedicationType[] types = MedicationType.selectable();
        ListAdapter iconAdapter = new ArrayAdapter<MedicationType>(
                this, android.R.layout.simple_list_item_1, android.R.id.text1, types) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                text.setText(types[position].getDisplayName());
                text.setCompoundDrawablePadding(24);
                text.setCompoundDrawablesRelativeWithIntrinsicBounds(types[position].getIconRes(), 0, 0, 0);
                return view;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle("Type for " + medicationName)
                .setAdapter(iconAdapter, (dialog, which) -> {
                    medication.setType(types[which].getKey());
                    medicationManager.updateMedication(medication);
                    refreshMedicationList();
                    Toast.makeText(this, "Type set to " + types[which].getDisplayName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Editor for a medication's extra ingredients (other drugs / vitamins bundled in). Shows a
     * row per ingredient (name + mg + remove) plus an "add" button; Save validates and stores all.
     */
    private void showIngredientsDialog(String medicationName) {
        Medication medication = medicationManager.getMedication(medicationName);
        if (medication == null) {
            Toast.makeText(this, "Medication not found", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        TextView infoText = new TextView(this);
        infoText.setText("Ingredients (other drugs or vitamins in this medication). Each scales by the same amount multiplier when you log a dose. Clear a name to drop it.");
        layout.addView(infoText);

        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(rowsContainer);

        List<View[]> rows = new ArrayList<>();
        for (Medication.Ingredient ingredient : medication.getIngredients()) {
            addIngredientRow(rowsContainer, rows, ingredient.getName(), ingredient.getDosage(), ingredient.getUnit());
        }
        if (rows.isEmpty()) {
            addIngredientRow(rowsContainer, rows, "", 0, Medication.DEFAULT_UNIT);
        }

        Button addRowButton = new Button(this);
        addRowButton.setText("Add another ingredient");
        addRowButton.setOnClickListener(v -> {
            if (rows.size() < Medication.MAX_INGREDIENTS) {
                addIngredientRow(rowsContainer, rows, "", 0, Medication.DEFAULT_UNIT);
            } else {
                Toast.makeText(this, "Up to " + Medication.MAX_INGREDIENTS + " ingredients",
                        Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(addRowButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ingredients for " + medicationName)
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        // Custom Save handler so an invalid dosage keeps the dialog open instead of losing edits
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<Medication.Ingredient> result = new ArrayList<>();
            for (View[] row : rows) {
                EditText nameInput = (EditText) row[0];
                EditText dosageInput = (EditText) row[1];
                Spinner unitSpinner = (Spinner) row[2];
                String ingredientName = nameInput.getText().toString().trim();
                if (ingredientName.isEmpty()) continue;
                String dosageText = dosageInput.getText().toString().trim();
                double dosage = 0;
                if (!dosageText.isEmpty()) {
                    try {
                        dosage = Double.parseDouble(dosageText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid dosage for " + ingredientName,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                result.add(new Medication.Ingredient(ingredientName, dosage,
                        (String) unitSpinner.getSelectedItem()));
            }
            medication.setIngredients(result);
            medicationManager.updateMedication(medication);
            Toast.makeText(this, "Ingredients updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    /** Adds one name + dosage + unit input row (with a remove button) to the ingredients editor. */
    private void addIngredientRow(LinearLayout container, List<View[]> rows,
                                  String name, double dosage, String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Ingredient name");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(name);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
        row.addView(nameInput);

        EditText dosageInput = new EditText(this);
        dosageInput.setHint("amt");
        dosageInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (dosage > 0) dosageInput.setText(formatDosage(dosage));
        dosageInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(dosageInput);

        Spinner unitSpinner = buildUnitSpinner(unit);
        unitSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(unitSpinner);

        View[] widgets = new View[]{nameInput, dosageInput, unitSpinner};

        ImageButton removeButton = new ImageButton(this);
        removeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        removeButton.setBackground(null);
        removeButton.setContentDescription("Remove ingredient");
        removeButton.setOnClickListener(v -> {
            container.removeView(row);
            rows.remove(widgets);
        });
        row.addView(removeButton);

        rows.add(widgets);
        container.addView(row);
    }

    @Override
    public void onMedicationUpdated(String medicationName) {
        // refreshMedicationList() now also syncs the detail panel to the current selection
        runOnUiThread(this::refreshMedicationList);
    }

    @Override
    public void onMedicationError(String message) {
        runOnUiThread(() ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onTimeUpdate(String timeSinceLastTaken) {
        runOnUiThread(() ->
                this.timeSinceLastTaken.setText(timeSinceLastTaken)
        );
    }

    @Override
    public void onStartDrag(MedicationAdapter.MedicationViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onMedicationSelected(String medication) {
        // Remember the amount for the medication we're leaving, then load the new one's
        saveAmountToMemory();
        amountMemoryMed = medication;
        amountInput.setText(formatDosage(amountManager.getLastAmount(medication)));
        updateMedicationDisplay(medication);
    }

    /** Persists the amount field to the currently tracked medication's memory. */
    private void saveAmountToMemory() {
        if (amountMemoryMed != null) {
            amountManager.setLastAmount(amountMemoryMed, parseAmount());
        }
    }

    @Override
    public void onOrderChanged(String[] medications) {
        // Reordering is only enabled on the "All" tab, so this is the full order
        orderManager.saveMedicationOrder(Arrays.asList(medications));
    }

    private void toggleDragAndDrop(boolean enable) {
        if (enable) {
            // Reordering operates on the complete list, so jump to the "All" tab first
            if (!CATEGORY_ALL.equals(currentCategory) && categoryTabs.getTabCount() > 0) {
                TabLayout.Tab allTab = categoryTabs.getTabAt(0);
                if (allTab != null) allTab.select();
            }
            itemTouchHelper.attachToRecyclerView(medicationList);
            Toast.makeText(this, "Rearrange mode on. Drag to reorder; press Back to finish.",
                    Toast.LENGTH_SHORT).show();
        } else {
            itemTouchHelper.attachToRecyclerView(null);
            Toast.makeText(this, "Rearrange mode disabled.", Toast.LENGTH_SHORT).show();
        }
        medicationAdapter.setDragEnabled(enable);
        medicationList.setLongClickable(!enable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist the current amount so it survives leaving the screen / switching apps
        saveAmountToMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaPlayerUndo != null) {
            mediaPlayerUndo.release();
            mediaPlayerUndo = null;
        }
        timeManager.stopTimeUpdates();
    }
}
