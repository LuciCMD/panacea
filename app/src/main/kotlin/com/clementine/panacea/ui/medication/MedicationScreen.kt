package com.clementine.panacea.ui.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateIconButton
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.components.textIcon
import com.clementine.panacea.ui.edit.PhotoViewer
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.edit.PillSide
import com.clementine.panacea.ui.history.HistoryDay
import com.clementine.panacea.ui.history.HistoryItem
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.reminders.NotificationsOffCard
import com.clementine.panacea.ui.reminders.ReminderRow
import com.clementine.panacea.ui.reminders.rememberAskForNotifications
import com.clementine.panacea.ui.reminders.rememberNotificationsAllowed
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.today.CardRing
import com.clementine.panacea.ui.today.StatusLines
import com.clementine.panacea.ui.today.TakeButtons
import com.clementine.panacea.ui.today.TakeSheet
import com.clementine.panacea.ui.today.TodayViewModel
import com.clementine.panacea.ui.today.UndoBar
import com.clementine.panacea.ui.today.rememberTake

/** Days of history shown at first, and how many more each tap on Show Earlier Days adds. */
private const val FIRST_DAYS = 7
private const val MORE_DAYS = 14

/** One medication: its photos, taking it, what's in it, how much went in, its reminders and history. */
@Composable
fun MedicationScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddReminder: () -> Unit,
    onOpenReminder: (reminderId: Long) -> Unit,
    viewModel: MedicationViewModel = viewModel(factory = MedicationViewModel.Factory),
    today: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val flow = remember(id) { viewModel.observe(id) }
    val state by flow.collectAsStateWithLifecycle(MedicationState.Loading)
    val todayUi by today.ui.collectAsStateWithLifecycle()
    val take = rememberTake(today)
    val sounds = LocalSoundPlayer.current
    val notificationsAllowed = rememberNotificationsAllowed()
    // Learning is turned on whatever the answer; the card below says if notifications are off.
    val askThenLearn = rememberAskForNotifications(settingsIfRefused = false) { viewModel.setLearnRoutine(id, true) }

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<PillSide?>(null) }
    var removing by remember { mutableStateOf<HistoryItem?>(null) }
    var days by rememberSaveable { mutableIntStateOf(FIRST_DAYS) }

    val ui = when (val s = state) {
        MedicationState.Loading -> return
        MedicationState.Gone -> {
            LaunchedEffect(Unit) { onBack() }
            return
        }
        is MedicationState.Ready -> s.ui
    }
    val med = ui.medication
    val card = todayUi?.cards?.firstOrNull { it.medication.id == id }

    Box(Modifier.fillMaxSize().background(Colors.Ground)) {
        // Room for the undo bar over the end of the list.
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = screenPadding(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(med.name, ui.subtitle, onBack, onEdit) }
            item(key = "photos") { PhotosCard(med, onView = { viewing = it }, onEdit = onEdit) }
            if (card != null) {
                item(key = "take") {
                    SlateCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                CardRing(card)
                                StatusLines(card)
                            }
                            TakeButtons(
                                med,
                                dueNow = card.dueNow,
                                onTake = { take(med.id, med.lastMultiplier, null) },
                                onAmount = { sheetOpen = true },
                            )
                        }
                    }
                }
            }
            item(key = "details") { DetailsCard(ui) }
            ui.totals?.let { totals -> item(key = "totals") { TotalsCard(totals) } }

            item(key = "reminders") { SectionLabel("Reminders") }
            ui.muted?.let { text -> item(key = "muted") { MutedCard(text) { viewModel.unmute(med.id) } } }
            item(key = "routine") {
                RoutineCard(ui.routine) { on ->
                    if (on && !notificationsAllowed) askThenLearn() else viewModel.setLearnRoutine(med.id, on)
                }
            }
            if (ui.reminders.any { it.enabled } || ui.routine.on) {
                item(key = "notifications") { NotificationsOffCard() }
            }
            if (ui.reminders.isEmpty()) {
                item(key = "reminders-none") { Quiet("No reminders for this one yet.") }
            }
            items(ui.reminders, key = { "reminder-${it.id}" }) { r ->
                ReminderRow(r, onToggle = { viewModel.setReminderEnabled(r.id, it) }, onOpen = { onOpenReminder(r.id) }, showMedication = false)
            }
            item(key = "add-reminder") {
                SlateButton(onClick = onAddReminder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
                    Text("Add Reminder")
                }
            }

            item(key = "history") { SectionLabel("History") }
            if (ui.history.isEmpty()) {
                item(key = "history-none") { Quiet("Doses you log show up here.") }
            }
            items(ui.history.take(days), key = { "day-${it.doses.first().id}" }) { day ->
                DayCard(day) { removing = it }
            }
            if (ui.history.size > days) {
                item(key = "more") {
                    SlateButton(onClick = { days += MORE_DAYS }, modifier = Modifier.fillMaxWidth()) { Text("Show Earlier Days") }
                }
            }
        }

        UndoBar(
            today,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth(),
        )
    }

    if (sheetOpen && card != null) {
        TakeSheet(
            card = card,
            presets = todayUi?.presets.orEmpty(),
            formats = today.formats,
            onTake = { multiplier, takenAt -> take(med.id, multiplier, takenAt) },
            onSavePreset = today::addPreset,
            onDismiss = { sheetOpen = false },
        )
    }
    viewing?.let { PhotoViewer(med.name, med.photoFront, med.photoBack, it) { viewing = null } }
    removing?.let { item ->
        ConfirmDialog(
            title = "Remove This Dose?",
            text = item.removeQuestion,
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                viewModel.removeDose(item.id)
                sounds.play(SoundEvent.UNDO)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun Header(name: String, subtitle: String, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        SlateIconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Glyphs.Back, contentDescription = "Back", tint = Colors.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                color = Colors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
        }
        SlateButton(onClick = onEdit, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Glyphs.Pencil, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Edit")
        }
    }
}

@Composable
private fun PhotosCard(med: MedicationEntity, onView: (PillSide) -> Unit, onEdit: () -> Unit) {
    if (med.photoFront == null && med.photoBack == null) {
        SectionCard("Pill Photos", "A photo of each side lets you check a pill at a glance.") {
            SlateButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Glyphs.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Add Pill Photos")
            }
        }
        return
    }
    SlateCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillSide.entries.forEach { side ->
                val photo = if (side == PillSide.FRONT) med.photoFront else med.photoBack
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val tile = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                    if (photo != null) {
                        PillPhoto(
                            photo,
                            null,
                            tile
                                .background(Colors.Field)
                                .clickable(onClickLabel = "View the ${side.label.lowercase()} up close") { onView(side) }
                                .semantics { contentDescription = "${side.label} of ${med.name}" },
                            px = 640,
                        )
                    } else {
                        Box(
                            tile
                                .background(Colors.Field)
                                .clickable(onClickLabel = "Add a photo of the ${side.label.lowercase()}", onClick = onEdit),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No Photo", style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
                        }
                    }
                    Text(side.label, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(ui: MedicationUi) {
    SectionCard("Details") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.facts.forEach { FactRow(it) }
        }
        if (ui.ingredients.isNotEmpty()) {
            HorizontalDivider(color = Colors.LineSoft)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ingredients", style = MaterialTheme.typography.labelLarge, color = Colors.Ink)
                ui.ingredients.forEach { FactRow(it) }
            }
        }
    }
}

@Composable
private fun FactRow(fact: Fact) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(fact.label, style = body, color = Colors.Muted, modifier = Modifier.weight(1f))
        Text(fact.value, style = body, color = if (fact.value == "Not set") Colors.Faint else Colors.Ink, textAlign = TextAlign.End)
    }
}

@Composable
private fun TotalsCard(totals: Totals) {
    val head = MaterialTheme.typography.labelMedium
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SectionCard("Totals") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                Spacer(Modifier.weight(1.3f))
                Cell("Doses", head, Colors.Muted, 0.7f)
                if (totals.showAmount) Cell("Amount", head, Colors.Muted, 1f)
                if (totals.showWeight) Cell("Weight", head, Colors.Muted, 1f)
            }
            totals.rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = row.spoken(totals.showAmount, totals.showWeight)
                    },
                ) {
                    Text(row.label, style = body, color = Colors.Muted, modifier = Modifier.weight(1.3f))
                    Cell(row.doses, body, Colors.Ink, 0.7f)
                    if (totals.showAmount) Cell(row.amount, body, Colors.Ink, 1f)
                    if (totals.showWeight) Cell(row.weight, body, Colors.Ink, 1f)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, style: TextStyle, color: Color, weight: Float) {
    Text(text, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(weight))
}

@Composable
private fun MutedCard(text: String, onUnmute: () -> Unit) {
    SlateCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Glyphs.BellOff, contentDescription = null, tint = Colors.Warn, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Ink, modifier = Modifier.weight(1f))
            SlateButton(onClick = onUnmute, kind = ButtonKind.Text) { Text("Unmute") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Colors.Ink,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Faint, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun DayCard(day: HistoryDay, onRemove: (HistoryItem) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            day.title,
            style = MaterialTheme.typography.labelMedium,
            color = Colors.Muted,
            modifier = Modifier.padding(start = 4.dp),
        )
        SlateCard(Modifier.fillMaxWidth()) {
            Column {
                day.doses.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(start = 14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Remove this dose") { onRemove(item) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.time, style = body, color = Colors.Ink, modifier = Modifier.widthIn(min = 64.dp))
                        Text(item.amount, style = body, color = Colors.Muted, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
