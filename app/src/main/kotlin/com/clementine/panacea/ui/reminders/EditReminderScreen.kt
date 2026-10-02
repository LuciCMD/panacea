package com.clementine.panacea.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import com.clementine.panacea.ui.components.SlateIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.model.RepeatType
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.FieldLabel
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.SlateDropdown
import com.clementine.panacea.ui.components.SlateTextField
import com.clementine.panacea.ui.components.TimeDialog
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle

private val RepeatLabels = linkedMapOf(
    RepeatType.DAILY to "Daily",
    RepeatType.WEEKLY to "Weekly",
    RepeatType.MONTHLY to "Monthly",
    RepeatType.HOURLY to "Hourly",
)

/** What the open time picker is for. */
private sealed interface Picking {
    data class Time(val index: Int) : Picking
    data object NewTime : Picking
    data object Start : Picking
    data object End : Picking
}

/** Adds a reminder when [reminderId] is null (for [medicationId], if given), otherwise edits it. */
@Composable
fun EditReminderScreen(
    reminderId: Long?,
    medicationId: Long?,
    onDone: () -> Unit,
    viewModel: EditReminderViewModel = viewModel(factory = EditReminderViewModel.Factory),
) {
    val saver = EditReminderViewModel.DraftSaver
    val start = ReminderDraft(medicationId = medicationId ?: 0)
    var draft by rememberSaveable(stateSaver = saver) { mutableStateOf(if (reminderId == null) start else null) }
    var original by rememberSaveable(stateSaver = saver) { mutableStateOf(if (reminderId == null) start else null) }
    LaunchedEffect(reminderId) {
        if (reminderId != null && draft == null) {
            val loaded = viewModel.load(reminderId) ?: return@LaunchedEffect onDone()
            draft = loaded
            original = loaded
        }
    }
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val d = draft ?: return
    val meds = medications ?: return
    // With one medication there's nothing to pick.
    LaunchedEffect(meds) {
        if (d.medicationId == 0L && meds.size == 1) draft = d.copy(medicationId = meds.single().id)
    }
    val update = { change: ReminderDraft -> draft = change }
    val f = viewModel.formats

    var tried by rememberSaveable { mutableStateOf(false) }
    val problems = if (tried) ReminderDrafts.check(d) else ReminderProblems()
    var picking by remember { mutableStateOf<Picking?>(null) }
    var discarding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    val notificationsAllowed = rememberNotificationsAllowed()
    val save = { viewModel.save(d) { onDone() } }
    // Asked once, at the moment a reminder is set up; the answer doesn't stop the save.
    val askThenSave = rememberAskForNotifications(settingsIfRefused = false) { save() }

    val close = { if (d != original) discarding = true else onDone() }
    BackHandler(onBack = close)

    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = screenPadding(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                SlateIconButton(onClick = close, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Glyphs.Close, contentDescription = "Close without saving", tint = Colors.Ink)
                }
                Text(
                    if (d.isNew) "Add Reminder" else "Edit Reminder",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Colors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item(key = "medication") {
            SectionCard("Medication") {
                val names = meds.associate { it.id to it.name }
                SlateDropdown(
                    d.medicationId,
                    meds.map { it.id },
                    { names[it] ?: "Pick a Medication" },
                    { update(d.copy(medicationId = it)) },
                    "Medication, ${names[d.medicationId] ?: "none picked"}",
                    Modifier.fillMaxWidth(),
                )
                problems.medication?.let { Problem(it) }
            }
        }
        item(key = "repeat") { RepeatCard(d, problems, f, update) { picking = it } }
        item(key = "note") {
            SectionCard("Note", "Shown on the notification, such as \"With lunch\".") {
                SlateTextField(
                    d.note, { update(d.copy(note = it)) },
                    placeholder = "With lunch",
                    error = problems.note,
                    accessibleLabel = "Note",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
            }
        }
        item(key = "early") {
            SectionCard("Early Doses", "A dose logged up to this long before the reminder counts for it, so the reminder stays quiet.") {
                SlateDropdown(
                    d.early,
                    ReminderDrafts.EARLY_CHOICES,
                    { ReminderDrafts.earlyLabel(it, d) },
                    { update(d.copy(early = it)) },
                    "Early doses, ${ReminderDrafts.earlyLabel(d.early, d)}",
                    Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (tried && !problems.none) {
                    Text("Some fields above need a look.", style = MaterialTheme.typography.bodyMedium, color = Colors.Bad)
                }
                SlateButton(
                    onClick = {
                        tried = true
                        if (ReminderDrafts.check(d).none) {
                            if (notificationsAllowed) save() else askThenSave()
                        }
                    },
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (d.isNew) "Add Reminder" else "Save", style = MaterialTheme.typography.labelLarge) }
                if (!d.isNew) {
                    SlateButton(onClick = { removing = true }, modifier = Modifier.fillMaxWidth()) { Text("Remove Reminder") }
                }
            }
        }
    }

    picking?.let { p ->
        val initial = when (p) {
            is Picking.Time -> d.times.getOrNull(p.index) ?: d.times.first()
            Picking.NewTime -> ((d.times.maxOrNull() ?: (8 * 60)) + 60).coerceAtMost(23 * 60)
            Picking.Start -> d.windowStart
            Picking.End -> d.windowEnd
        }
        TimeDialog(
            title = when (p) {
                Picking.Start -> "From"
                Picking.End -> "To"
                else -> "Reminder Time"
            },
            initial = LocalTime.of(initial / 60, initial % 60),
            onPick = { time ->
                val m = time.hour * 60 + time.minute
                update(
                    when (p) {
                        is Picking.Time -> d.copy(times = d.times.toMutableList().also { it[p.index] = m }.distinct().sorted())
                        Picking.NewTime -> d.copy(times = (d.times + m).distinct().sorted())
                        Picking.Start -> d.copy(windowStart = m)
                        Picking.End -> d.copy(windowEnd = m)
                    },
                )
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (discarding) {
        ConfirmDialog(
            title = "Discard Changes?",
            text = if (d.isNew) "This reminder won't be added." else "What you changed won't be saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep Editing",
            onConfirm = {
                discarding = false
                onDone()
            },
            onDismiss = { discarding = false },
        )
    }
    if (removing) {
        ConfirmDialog(
            title = "Remove This Reminder?",
            text = "It won't remind you again. Doses you logged stay in your history.",
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                removing = false
                viewModel.delete(d.id, onDone)
            },
            onDismiss = { removing = false },
        )
    }
}

@Composable
private fun RepeatCard(d: ReminderDraft, problems: ReminderProblems, f: TimeFormats, update: (ReminderDraft) -> Unit, pick: (Picking) -> Unit) {
    val preview = ReminderText.schedule(ReminderDrafts.toEntity(d, null, ZonedDateTime.now()), f)
    SectionCard("Repeat") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatLabels.forEach { (type, label) ->
                SlateChip(selected = d.repeat == type, onClick = { update(d.copy(repeat = type)) }) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (d.repeat == RepeatType.WEEKLY || d.repeat == RepeatType.HOURLY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Days")
                DayChips(d.daysMask, f) { update(d.copy(daysMask = it)) }
                problems.days?.let { Problem(it) }
            }
        }
        if (d.repeat == RepeatType.MONTHLY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Day of the Month")
                SlateDropdown(
                    d.dayOfMonth, (1..31).toList(), { "The ${ReminderText.ordinal(it)}" },
                    { update(d.copy(dayOfMonth = it)) }, "Day of the month, the ${ReminderText.ordinal(d.dayOfMonth)}",
                )
                if (d.dayOfMonth > 28) Hint("In shorter months it comes on the last day.")
            }
        }
        if (d.repeat == RepeatType.HOURLY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("How Often")
                SlateDropdown(
                    d.intervalHours, ReminderDrafts.INTERVALS, ReminderDrafts::interval,
                    { update(d.copy(intervalHours = it)) }, "How often, ${ReminderDrafts.interval(d.intervalHours).lowercase()}",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("From")
                    TimeButton(d.windowStart, f, "From") { pick(Picking.Start) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("To")
                    TimeButton(d.windowEnd, f, "To") { pick(Picking.End) }
                }
            }
            problems.window?.let { Problem(it) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Times")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.times.forEachIndexed { i, m ->
                        TimePill(
                            m, f,
                            onChange = { pick(Picking.Time(i)) },
                            onRemove = if (d.times.size > 1) ({ update(d.copy(times = d.times - m)) }) else null,
                        )
                    }
                    if (d.times.size < ReminderDrafts.MAX_TIMES) {
                        SlateChip(selected = false, onClick = { pick(Picking.NewTime) }) {
                            Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Add Time", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text(preview, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
    }
}

@Composable
private fun DayChips(mask: Int, f: TimeFormats, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0L until 7L).map { f.firstDayOfWeek.plus(it) }.forEach { day ->
            val bit = 1 shl (day.value % 7)
            val on = mask and bit != 0
            SlateChip(selected = on, onClick = { onChange(mask xor bit) }) {
                Text(day.getDisplayName(TextStyle.SHORT, f.locale), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun format(minutes: Int, f: TimeFormats) = LocalTime.of(minutes / 60, minutes % 60).format(f.time)

@Composable
private fun TimePill(minutes: Int, f: TimeFormats, onChange: () -> Unit, onRemove: (() -> Unit)?) {
    val time = format(minutes, f)
    Surface(shape = RoundedCornerShape(24.dp), color = Colors.Field, contentColor = Colors.Ink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clickable(onClickLabel = "Change $time", onClick = onChange)
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = if (onRemove == null) 16.dp else 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(time, style = MaterialTheme.typography.bodyLarge.merge(Numbers))
            }
            if (onRemove != null) {
                SlateIconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(Glyphs.Close, contentDescription = "Remove $time", tint = Colors.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TimeButton(minutes: Int, f: TimeFormats, label: String, onClick: () -> Unit) {
    val time = format(minutes, f)
    // The button reads out as "From, 8:00".
    SlateButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Glyphs.Clock, contentDescription = label, modifier = Modifier.size(18.dp))
        Text(time, style = MaterialTheme.typography.bodyLarge.merge(Numbers))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
}

@Composable
private fun Problem(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Bad)
}
