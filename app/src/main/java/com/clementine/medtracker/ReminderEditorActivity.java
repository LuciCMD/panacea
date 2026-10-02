package com.clementine.medtracker;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.clementine.medtracker.data.MedicationDataManager;
import com.clementine.medtracker.utils.ReminderManager;
import com.clementine.medtracker.utils.ThemeManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen editor for a single reminder. Launched by {@link MainActivity} for adding or
 * editing; hosts the per-type schedule UI (hourly / daily / weekly / monthly) with a
 * clock-dial time picker ({@link MaterialTimePicker}) and a Save/back flow.
 *
 * @author LuciCMD
 */
public class ReminderEditorActivity extends AppCompatActivity {
    public static final String EXTRA_MED = "medication_name";
    public static final String EXTRA_REMINDER_ID = "reminder_id";

    private static final String[] DAY_LABELS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private ReminderManager reminderManager;
    private String medicationName;
    private ReminderManager.Reminder existing;   // null for a new reminder

    private MaterialButtonToggleGroup repeatGroup;
    private LinearLayout hourlySection;
    private LinearLayout monthlySection;
    private LinearLayout daysSection;
    private LinearLayout timesSection;
    private final Chip[] dayChips = new Chip[7];
    private EditText intervalInput;
    private final int[] start = {8, 0};
    private final int[] end = {22, 0};
    private MaterialButton startButton;
    private MaterialButton endButton;
    private NumberPicker dayOfMonthPicker;
    private LinearLayout timesContainer;
    private final List<int[]> times = new ArrayList<>();
    private TextInputEditText noteInput;
    private MaterialSwitch enabledSwitch;
    private LinearLayout medSection;
    private Spinner medSpinner;

    private interface OnTimePicked { void onPicked(int hour, int minute); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new ThemeManager(this).applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_editor);
        applyWindowInsets();

        reminderManager = new ReminderManager(this);
        medicationName = getIntent().getStringExtra(EXTRA_MED);
        int reminderId = getIntent().getIntExtra(EXTRA_REMINDER_ID, -1);
        if (reminderId != -1) existing = reminderManager.getReminderById(reminderId);
        if (medicationName == null && existing != null) medicationName = existing.getMedicationName();

        MaterialToolbar toolbar = findViewById(R.id.reminder_toolbar);
        toolbar.setTitle((existing != null ? "Edit reminder" : "Add reminder")
                + (medicationName != null ? " · " + medicationName : ""));
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        setupMedicationPicker();
        seedFromExisting();
        findViewById(R.id.save_reminder_button).setOnClickListener(v -> onSave());
    }

    private void bindViews() {
        repeatGroup = findViewById(R.id.repeat_group);
        hourlySection = findViewById(R.id.hourly_section);
        monthlySection = findViewById(R.id.monthly_section);
        daysSection = findViewById(R.id.days_section);
        timesSection = findViewById(R.id.times_section);
        intervalInput = findViewById(R.id.interval_input);
        startButton = findViewById(R.id.start_time_button);
        endButton = findViewById(R.id.end_time_button);
        dayOfMonthPicker = findViewById(R.id.day_of_month_picker);
        timesContainer = findViewById(R.id.times_container);
        noteInput = findViewById(R.id.note_input);
        enabledSwitch = findViewById(R.id.enabled_switch);
        medSection = findViewById(R.id.med_section);
        medSpinner = findViewById(R.id.med_spinner);

        dayOfMonthPicker.setMinValue(1);
        dayOfMonthPicker.setMaxValue(31);

        ChipGroup daysChipGroup = findViewById(R.id.days_chip_group);
        for (int i = 0; i < 7; i++) {
            Chip chip = new Chip(this);
            chip.setText(DAY_LABELS[i]);
            chip.setCheckable(true);
            dayChips[i] = chip;
            daysChipGroup.addView(chip);
        }

        startButton.setOnClickListener(v -> pickTime(start[0], start[1], (h, m) -> {
            start[0] = h; start[1] = m; startButton.setText(fmtTime(h, m));
        }));
        endButton.setOnClickListener(v -> pickTime(end[0], end[1], (h, m) -> {
            end[0] = h; end[1] = m; endButton.setText(fmtTime(h, m));
        }));
        findViewById(R.id.add_time_button).setOnClickListener(v -> addTimeRow(8, 0));

        repeatGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateVisibility(typeFor(checkedId));
        });
    }

    /** When no medication was passed in (adding from the reminders list), offer a picker. */
    private void setupMedicationPicker() {
        if (medicationName != null) {
            medSection.setVisibility(View.GONE);
            return;
        }
        medSection.setVisibility(View.VISIBLE);
        List<String> names = MedicationDataManager.getInstance(this).getMedicationNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        medSpinner.setAdapter(adapter);
    }

    private void seedFromExisting() {
        ReminderManager.Reminder.RepeatType type = (existing != null)
                ? existing.getRepeatType() : ReminderManager.Reminder.RepeatType.DAILY;
        int seedMask = (existing != null) ? existing.getDaysMask() : ReminderManager.Reminder.EVERY_DAY;

        for (int i = 0; i < 7; i++) dayChips[i].setChecked((seedMask & (1 << i)) != 0);

        if (existing != null) {
            intervalInput.setText(String.valueOf(existing.getIntervalHours()));
            start[0] = existing.getStartHour(); start[1] = existing.getStartMinute();
            end[0] = existing.getEndHour(); end[1] = existing.getEndMinute();
            dayOfMonthPicker.setValue(existing.getDayOfMonth());
            noteInput.setText(existing.getLabel());
            enabledSwitch.setChecked(existing.isEnabled());
        }
        startButton.setText(fmtTime(start[0], start[1]));
        endButton.setText(fmtTime(end[0], end[1]));

        if (existing != null && !existing.getTimes().isEmpty()) {
            for (int[] time : existing.getTimes()) addTimeRow(time[0], time[1]);
        } else {
            addTimeRow(8, 0);
        }

        repeatGroup.check(buttonFor(type));
        updateVisibility(type);
    }

    private void onSave() {
        if (medicationName == null) {   // adding from the reminders list: take the picked med
            Object picked = medSpinner.getSelectedItem();
            if (picked == null) {
                Toast.makeText(this, "Select a medication", Toast.LENGTH_SHORT).show();
                return;
            }
            medicationName = picked.toString();
        }
        ReminderManager.Reminder reminder = (existing != null) ? existing
                : new ReminderManager.Reminder(medicationName, ReminderManager.Reminder.RepeatType.DAILY);

        reminder.setRepeatType(typeFor(repeatGroup.getCheckedButtonId()));
        reminder.setDaysMask(daysMask());
        reminder.setTimes(times);
        int interval = 4;
        try {
            interval = Integer.parseInt(intervalInput.getText().toString().trim());
        } catch (NumberFormatException ignored) { }
        reminder.setIntervalHours(interval);
        reminder.setStart(start[0], start[1]);
        reminder.setEnd(end[0], end[1]);
        reminder.setDayOfMonth(dayOfMonthPicker.getValue());
        reminder.setLabel(noteInput.getText() == null ? "" : noteInput.getText().toString().trim());
        reminder.setEnabled(enabledSwitch.isChecked());

        if (existing != null) reminderManager.updateReminder(reminder);
        else reminderManager.addReminder(reminder);
        finish();
    }

    private void updateVisibility(ReminderManager.Reminder.RepeatType type) {
        boolean hourly = type == ReminderManager.Reminder.RepeatType.HOURLY;
        boolean monthly = type == ReminderManager.Reminder.RepeatType.MONTHLY;
        hourlySection.setVisibility(hourly ? View.VISIBLE : View.GONE);
        monthlySection.setVisibility(monthly ? View.VISIBLE : View.GONE);
        timesSection.setVisibility(hourly ? View.GONE : View.VISIBLE);
        daysSection.setVisibility(monthly ? View.GONE : View.VISIBLE);
    }

    private ReminderManager.Reminder.RepeatType typeFor(int checkedButtonId) {
        if (checkedButtonId == R.id.repeat_hourly) return ReminderManager.Reminder.RepeatType.HOURLY;
        if (checkedButtonId == R.id.repeat_weekly) return ReminderManager.Reminder.RepeatType.WEEKLY;
        if (checkedButtonId == R.id.repeat_monthly) return ReminderManager.Reminder.RepeatType.MONTHLY;
        return ReminderManager.Reminder.RepeatType.DAILY;
    }

    private int buttonFor(ReminderManager.Reminder.RepeatType type) {
        switch (type) {
            case HOURLY: return R.id.repeat_hourly;
            case WEEKLY: return R.id.repeat_weekly;
            case MONTHLY: return R.id.repeat_monthly;
            default: return R.id.repeat_daily;
        }
    }

    private int daysMask() {
        int mask = 0;
        for (int i = 0; i < 7; i++) {
            if (dayChips[i].isChecked()) mask |= (1 << i);
        }
        return (mask == 0) ? ReminderManager.Reminder.EVERY_DAY : mask;
    }

    private void addTimeRow(int hour, int minute) {
        int[] entry = {hour, minute};
        times.add(entry);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Button timeButton = new Button(this);
        timeButton.setText(fmtTime(entry[0], entry[1]));
        timeButton.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        timeButton.setOnClickListener(v -> pickTime(entry[0], entry[1], (h, m) -> {
            entry[0] = h; entry[1] = m; timeButton.setText(fmtTime(h, m));
        }));
        row.addView(timeButton);

        ImageButton remove = new ImageButton(this);
        remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove.setBackground(null);
        remove.setContentDescription("Remove time");
        remove.setOnClickListener(v -> {
            if (times.size() > 1) {
                times.remove(entry);
                timesContainer.removeView(row);
            }
        });
        row.addView(remove);

        timesContainer.addView(row);
    }

    private void pickTime(int hour, int minute, OnTimePicked callback) {
        View content = getLayoutInflater().inflate(R.layout.dialog_time_spinner, null);
        TimePicker timePicker = content.findViewById(R.id.time_picker);
        timePicker.setIs24HourView(false);
        timePicker.setHour(hour);
        timePicker.setMinute(minute);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("OK", (d, w) ->
                        callback.onPicked(timePicker.getHour(), timePicker.getMinute()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String fmtTime(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.reminder_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
