package com.clementine.medtracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.clementine.medtracker.data.MedicationDataManager;
import com.clementine.medtracker.utils.ReminderFormatter;
import com.clementine.medtracker.utils.ReminderManager;
import com.clementine.medtracker.utils.ThemeManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

/**
 * Lists every reminder across all medications with its schedule and adherence status
 * (next fire, completed / missed counts, adherence %). Reminders are created here - the
 * medication is chosen inside the editor - so no medication needs to be selected first.
 *
 * @author LuciCMD
 */
public class RemindersActivity extends AppCompatActivity {

    private ReminderManager reminderManager;
    private LinearLayout container;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new ThemeManager(this).applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders);
        applyWindowInsets();

        reminderManager = new ReminderManager(this);
        container = findViewById(R.id.reminders_container);
        emptyView = findViewById(R.id.reminders_empty);

        MaterialToolbar toolbar = findViewById(R.id.reminders_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.add_reminder_button).setOnClickListener(v -> addReminder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();   // reflect edits/additions made in the editor
    }

    private void addReminder() {
        if (MedicationDataManager.getInstance(this).getMedicationNames().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("Add a medication first, then create a reminder for it.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        openEditor(null, -1);
    }

    private void refreshList() {
        container.removeAllViews();
        List<ReminderManager.Reminder> reminders = reminderManager.getReminders();
        emptyView.setVisibility(reminders.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReminderManager.Reminder reminder : reminders) {
            View card = inflater.inflate(R.layout.item_reminder, container, false);
            ((TextView) card.findViewById(R.id.reminder_med)).setText(reminder.getMedicationName());
            ((TextView) card.findViewById(R.id.reminder_schedule))
                    .setText(ReminderFormatter.scheduleSummary(reminder));
            ((TextView) card.findViewById(R.id.reminder_status)).setText(statusLine(reminder));
            card.setOnClickListener(v -> showOptions(reminder));
            container.addView(card);
        }
    }

    private String statusLine(ReminderManager.Reminder reminder) {
        String next = reminder.isEnabled()
                ? "Next " + ReminderFormatter.nextInText(reminderManager.getNextTriggerTime(reminder))
                : "Disabled";
        return String.format("%s  ·  ✓ %d taken  ·  ✗ %d missed  ·  %s",
                next, reminder.getTimesCompleted(), reminder.getTimesMissed(),
                ReminderFormatter.adherenceText(reminder));
    }

    private void showOptions(ReminderManager.Reminder reminder) {
        String toggle = reminder.isEnabled() ? "Disable" : "Enable";
        String[] options = {"Edit", toggle, "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(reminder.getMedicationName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openEditor(reminder.getMedicationName(), reminder.getId());
                            break;
                        case 1:
                            reminder.setEnabled(!reminder.isEnabled());
                            reminderManager.updateReminder(reminder);
                            refreshList();
                            break;
                        case 2:
                            confirmDelete(reminder);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(ReminderManager.Reminder reminder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reminder?")
                .setMessage("This removes the reminder for " + reminder.getMedicationName() + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    reminderManager.removeReminder(reminder);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditor(String medicationName, int reminderId) {
        Intent intent = new Intent(this, ReminderEditorActivity.class);
        if (medicationName != null) intent.putExtra(ReminderEditorActivity.EXTRA_MED, medicationName);
        intent.putExtra(ReminderEditorActivity.EXTRA_REMINDER_ID, reminderId);
        startActivity(intent);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.reminders_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
