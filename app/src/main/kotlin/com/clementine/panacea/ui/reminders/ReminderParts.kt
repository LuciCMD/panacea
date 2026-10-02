package com.clementine.panacea.ui.reminders

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.ui.Prompt
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.DoseTimeDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateSwitch
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.today.TodayText
import com.clementine.panacea.ui.today.TodayViewModel
import com.clementine.panacea.ui.today.rememberTake
import java.time.ZonedDateTime

/** How long a mute lasts; Until Tomorrow stands in for "not today". */
enum class MuteChoice(val label: String) {
    HOUR("For 1 Hour"),
    TWO_HOURS("For 2 Hours"),
    FOUR_HOURS("For 4 Hours"),
    TOMORROW("Until Tomorrow");

    fun until(now: ZonedDateTime): Long = when (this) {
        HOUR -> now.plusHours(1)
        TWO_HOURS -> now.plusHours(2)
        FOUR_HOURS -> now.plusHours(4)
        TOMORROW -> now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    }.toInstant().toEpochMilli()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuteDialog(title: String, onMute: (until: Long) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableIntStateOf(MuteChoice.TWO_HOURS.ordinal) }
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = title }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 10.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Colors.Ink, modifier = Modifier.padding(start = 12.dp))
                Text(
                    "Reminders that come due while muted show once the mute ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.Muted,
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 10.dp),
                )
                Column(Modifier.selectableGroup()) {
                    MuteChoice.entries.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = choice == c.ordinal, role = Role.RadioButton) { choice = c.ordinal }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice == c.ordinal,
                                onClick = null,
                                modifier = Modifier.padding(12.dp),
                                colors = RadioButtonDefaults.colors(selectedColor = Colors.Accent, unselectedColor = Colors.Muted),
                            )
                            Text(c.label, style = MaterialTheme.typography.bodyLarge, color = Colors.Ink)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SlateButton(onClick = onDismiss) { Text("Cancel") }
                    SlateButton(
                        onClick = { onMute(MuteChoice.entries[choice].until(ZonedDateTime.now())) },
                        kind = ButtonKind.Primary,
                    ) { Text("Mute") }
                }
            }
        }
    }
}

/** True while the app may post notifications; checked again whenever the screen comes back. */
@Composable
fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    return allowed
}

/**
 * Asks for the notification permission. If it's refused and [settingsIfRefused], opens the app's
 * notification settings, where a permission refused twice can still be given.
 */
@Composable
fun rememberAskForNotifications(settingsIfRefused: Boolean = true, onAnswer: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && settingsIfRefused) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        onAnswer(granted)
    }
    return { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** Says reminders can't show, with a way to fix it; nothing when notifications are allowed. */
@Composable
fun NotificationsOffCard() {
    if (rememberNotificationsAllowed()) return
    val ask = rememberAskForNotifications()
    SectionCard("Notifications Are Off", "Reminders can't show until Panacea may send notifications.", warning = true) {
        SlateButton(onClick = ask, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth()) {
            Icon(Glyphs.Bell, contentDescription = null)
            Text("Allow Notifications")
        }
    }
}

/** One reminder: tap to edit it, the switch turns it on or off. */
/** A reminder on its medication's page, where when it goes off is what tells reminders apart. */
@Composable
fun ReminderRow(card: ReminderCard, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    SlateCard(Modifier.fillMaxWidth()) {
        ReminderLine(card, onToggle, onOpen, MaterialTheme.typography.titleMedium, PaddingValues(16.dp))
    }
}

/** One medication's reminders on the Reminders tab: its name once, then each reminder by when it goes off. */
@Composable
fun MedicationReminders(cards: List<ReminderCard>, onToggle: (id: Long, on: Boolean) -> Unit, onOpen: (id: Long) -> Unit) {
    SlateCard(Modifier.fillMaxWidth()) {
        Column {
            Text(
                cards.first().medication,
                style = MaterialTheme.typography.titleMedium,
                color = Colors.Ink,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() },
            )
            cards.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(start = 16.dp))
                ReminderLine(
                    card,
                    onToggle = { onToggle(card.id, it) },
                    onOpen = { onOpen(card.id) },
                    scheduleStyle = MaterialTheme.typography.bodyLarge,
                    padding = PaddingValues(start = 16.dp, end = 16.dp, top = if (i == 0) 4.dp else 12.dp, bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderLine(card: ReminderCard, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, scheduleStyle: TextStyle, padding: PaddingValues) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Row(
        Modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Edit this reminder", onClick = onOpen)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(card.schedule, style = scheduleStyle.merge(Numbers), color = if (card.enabled) Colors.Ink else Colors.Muted)
            card.note?.let { Text(it, style = body, color = Colors.Muted) }
            Text(card.next, style = body, color = if (card.muted) Colors.Warn else Colors.Muted)
        }
        // Two reminders for one medication differ only by when, so the switch says both.
        SlateSwitch(
            checked = card.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = "${card.medication} Reminder, ${card.schedule}" },
        )
    }
}

/** A learned reminder: tap for the medication's page, where it's explained; the switch stops learning. */
@Composable
fun LearnedRow(card: LearnedCard, onStop: () -> Unit, onOpen: () -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SlateCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClickLabel = "Open ${card.medication}", onClick = onOpen)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.medication, style = MaterialTheme.typography.titleMedium, color = Colors.Ink)
                    Text(
                        "Learned",
                        style = MaterialTheme.typography.labelMedium,
                        color = Colors.Accent,
                        modifier = Modifier
                            .background(Colors.AccentWash, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(card.usually, style = body, color = Colors.Ink)
                Text(card.next, style = body, color = if (card.muted) Colors.Warn else Colors.Muted)
            }
            SlateSwitch(
                checked = true,
                onCheckedChange = { if (!it) onStop() },
                modifier = Modifier.semantics { contentDescription = "Learn ${card.medication}'s Routine" },
            )
        }
    }
}

/** When to remind again, from Later… on a notification; Not Today mutes the medication until midnight. */
enum class LaterChoice(val label: String) {
    TEN_MINUTES("In 10 Minutes"),
    HOUR("In 1 Hour"),
    TWO_HOURS("In 2 Hours"),
    FOUR_HOURS("In 4 Hours"),
    NOT_TODAY("Not Today");

    fun until(now: ZonedDateTime): Long = when (this) {
        TEN_MINUTES -> now.plusMinutes(10)
        HOUR -> now.plusHours(1)
        TWO_HOURS -> now.plusHours(2)
        FOUR_HOURS -> now.plusHours(4)
        NOT_TODAY -> now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    }.toInstant().toEpochMilli()
}

/** The medication a notification's button is about, with its name, once known; [onGone] if it was removed. */
@Composable
private fun rememberPromptMedication(prompt: Prompt, viewModel: RemindersViewModel, onGone: () -> Unit): Pair<Long, String>? {
    val names by viewModel.names.collectAsStateWithLifecycle()
    val id by produceState<Long?>(null, prompt) { value = viewModel.medicationOf(prompt) ?: -1L }
    val all = names ?: return null
    val medicationId = id ?: return null
    val name = all[medicationId]
    if (name == null) {
        LaunchedEffect(Unit) { onGone() }
        return null
    }
    return medicationId to name
}

/** Already Taken… on a notification: log when it was taken, or put the reminder away if it's logged. */
@Composable
fun AlreadyTakenDialog(prompt: Prompt, onTookEarlier: (medicationId: Long) -> Unit, onClose: () -> Unit) {
    val viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)
    val (medicationId, name) = rememberPromptMedication(prompt, viewModel, onClose) ?: return
    ChoiceDialog(
        title = "Already Taken",
        text = "Log when you took $name, or put this reminder away if the dose is already in History.",
        onDismiss = onClose,
    ) {
        // Which applies depends on the person, so neither is the primary.
        SlateButton(onClick = { onTookEarlier(medicationId) }, modifier = Modifier.fillMaxWidth()) {
            Text("Took It Earlier")
        }
        SlateButton(
            onClick = {
                viewModel.dealtWith(prompt)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Already Logged") }
    }
}

/** Took It Earlier: asks when, logs the usual amount at that time, and puts the notification away. */
@Composable
fun TookEarlierDialog(prompt: Prompt, onClose: () -> Unit) {
    val reminders: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)
    val today: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val ui by today.ui.collectAsStateWithLifecycle()
    val take = rememberTake(today)
    val (medicationId, _) = rememberPromptMedication(prompt, reminders, onClose) ?: return
    val med = ui?.cards?.firstOrNull { it.medication.id == medicationId }?.medication ?: return
    DoseTimeDialog(
        initial = ZonedDateTime.now(),
        // It logs the usual amount, so it says which.
        confirmLabel = "Log ${TodayText.amount(med, med.lastMultiplier)}",
        onPick = { at ->
            take(med.id, med.lastMultiplier, at.toInstant().toEpochMilli())
            // Even a time too early to count for the reminder answers it.
            reminders.dealtWith(prompt)
            onClose()
        },
        onDismiss = onClose,
    )
}

/** Later… on a notification: shows it again after a while, or not today. */
@Composable
fun LaterDialog(prompt: Prompt, onClose: () -> Unit) {
    val viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)
    val (medicationId, name) = rememberPromptMedication(prompt, viewModel, onClose) ?: return
    ChoiceDialog(
        title = "Remind Me Later",
        text = "Not Today also holds $name's other reminders until midnight.",
        onDismiss = onClose,
    ) {
        LaterChoice.entries.forEach { choice ->
            if (choice == LaterChoice.NOT_TODAY) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(vertical = 4.dp))
            SlateButton(
                onClick = {
                    viewModel.later(prompt, medicationId, choice)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(choice.label) }
        }
    }
}

/** A short question answered by one of a column of buttons, with Cancel below them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceDialog(title: String, text: String, onDismiss: () -> Unit, choices: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = title }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Colors.Ink)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.Muted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices()
                    SlateButton(onClick = onDismiss, kind = ButtonKind.Text, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}
