package com.clementine.panacea.ui.reminders

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateSwitch
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
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
    SectionCard("Notifications Are Off", "Reminders can't show until Panacea may send notifications.") {
        SlateButton(onClick = ask, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth()) {
            Icon(Glyphs.Bell, contentDescription = null)
            Text("Allow Notifications")
        }
    }
}

/** One reminder: tap to edit it, the switch turns it on or off. */
@Composable
fun ReminderRow(card: ReminderCard, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, showMedication: Boolean = true) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SlateCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 0.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Edit this reminder", onClick = onOpen)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val scheduleColor = if (card.enabled) Colors.Ink else Colors.Muted
                if (showMedication) {
                    Text(card.medication, style = MaterialTheme.typography.titleMedium, color = Colors.Ink)
                    Text(card.schedule, style = body, color = scheduleColor)
                } else {
                    // On a medication's own page the schedule is what tells reminders apart.
                    Text(card.schedule, style = MaterialTheme.typography.titleMedium.merge(Numbers), color = scheduleColor)
                }
                card.note?.let { Text(it, style = body, color = Colors.Muted) }
                Text(card.next, style = body, color = if (card.muted) Colors.Warn else Colors.Muted)
            }
            val label = if (showMedication) "${card.medication} reminder" else "This reminder"
            SlateSwitch(
                checked = card.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

/** The Mute… button on a notification: asks how long, then quiets that medication's reminders. */
@Composable
fun MuteMedicationDialog(medicationId: Long, onClose: () -> Unit) {
    val viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)
    val names by viewModel.names.collectAsStateWithLifecycle()
    val all = names ?: return
    val name = all[medicationId] ?: return LaunchedEffect(Unit) { onClose() }
    MuteDialog(
        title = "Mute $name",
        onMute = { until ->
            viewModel.mute(medicationId, until)
            onClose()
        },
        onDismiss = onClose,
    )
}
