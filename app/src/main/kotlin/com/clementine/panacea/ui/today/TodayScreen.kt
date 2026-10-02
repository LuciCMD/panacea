package com.clementine.panacea.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.model.Category
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.ProgressRing
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.SlateToast
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.delay

private const val UNDO_SHOWN_MS = 10_000L
private const val TAKE_LOCK_MS = 3_000L

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val logged by viewModel.logged.collectAsStateWithLifecycle()
    val sounds = LocalSoundPlayer.current
    val haptics = LocalHapticFeedback.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirming by remember { mutableStateOf<LoggedDose?>(null) }

    val take = { medicationId: Long, multiplier: Double, takenAt: Long? ->
        viewModel.take(medicationId, multiplier, takenAt)
        sounds.play(SoundEvent.TAKE)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    Box(Modifier.fillMaxSize().background(Colors.Ground)) {
        ui?.let { state ->
            val categories = state.categories
            val shown = Category.entries.firstOrNull { it.key == filter && it in categories }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room for the undo bar over the last card.
                contentPadding = screenPadding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { ScreenHeader("Today", state.header) }
                item(key = "strip") { DayStripCard(state.strip) }
                if (categories.size > 1) {
                    item(key = "filters") { Filters(categories, shown) { filter = it?.key } }
                }
                val cards = state.cards.filter { shown == null || it.category == shown }
                if (cards.isEmpty()) {
                    item(key = "empty") {
                        Text("Add a medication to start.", style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
                    }
                }
                items(cards, key = { it.medication.id }) { card ->
                    MedicationCard(
                        card,
                        onTake = { take(card.medication.id, card.medication.lastMultiplier, null) },
                        onAmount = { sheetFor = card.medication.id },
                    )
                }
            }

            state.cards.firstOrNull { it.medication.id == sheetFor }?.let { card ->
                TakeSheet(
                    card = card,
                    presets = state.presets,
                    formats = viewModel.formats,
                    onTake = { multiplier, takenAt -> take(card.medication.id, multiplier, takenAt) },
                    onSavePreset = viewModel::addPreset,
                    onDismiss = { sheetFor = null },
                )
            }
        }

        val current = logged
        if (current != null && confirming == null) {
            LaunchedEffect(current) {
                delay(UNDO_SHOWN_MS)
                viewModel.dismiss(current)
            }
            SlateToast(
                message = current.message,
                actionLabel = "Undo",
                onAction = { confirming = current },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth(),
            )
        }
    }

    confirming?.let { dose ->
        ConfirmDialog(
            title = "Remove This Dose?",
            text = dose.removeQuestion,
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                viewModel.undo(dose)
                sounds.play(SoundEvent.UNDO)
                confirming = null
            },
            onDismiss = {
                viewModel.dismiss(dose)
                confirming = null
            },
        )
    }
}

@Composable
private fun DayStripCard(strip: DayStrip) {
    val small = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp).merge(Numbers)
    val description = if (strip.dots.isEmpty()) "No doses logged today" else "Logged today: " + strip.dots.joinToString { it.label }
    SlateCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Doses Today", style = small, color = Colors.Muted)
                Text(TodayText.dosesToday(strip.count), style = small, color = Colors.Ink)
            }
            val (field, lineColor, surface, accent, ink) = listOf(Colors.Field, Colors.Line, Colors.Surface, Colors.Accent, Colors.Ink)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                val line = 2.dp.toPx()
                val y = size.height / 2
                val nowX = size.width * strip.nowFraction
                val corner = CornerRadius(line / 2)
                drawRoundRect(field, Offset(0f, y - line / 2), Size(size.width, line), corner)
                drawRoundRect(lineColor, Offset(0f, y - line / 2), Size(nowX, line), corner)
                strip.dots.forEach {
                    val center = Offset(size.width * it.fraction, y)
                    drawCircle(surface, radius = 10.dp.toPx(), center = center)
                    drawCircle(accent, radius = 7.dp.toPx(), center = center)
                }
                drawRoundRect(ink, Offset(nowX - line / 2, 0f), Size(line, size.height), corner)
            }
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                strip.axis.forEach { Text(it, style = small.copy(fontSize = 11.sp), color = Colors.Faint) }
            }
        }
    }
}

@Composable
private fun Filters(categories: List<Category>, shown: Category?, onPick: (Category?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("All", shown == null) { onPick(null) }
        categories.forEach { c -> FilterChip(c.label, shown == c) { onPick(c) } }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SlateChip(selected = selected, onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun MedicationCard(card: CardState, onTake: () -> Unit, onAmount: () -> Unit) {
    val med = card.medication
    // A second tap right after the first is almost always a slip, so the button rests briefly.
    var justTaken by remember { mutableStateOf(false) }
    LaunchedEffect(justTaken) {
        if (justTaken) {
            delay(TAKE_LOCK_MS)
            justTaken = false
        }
    }
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    SlateCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(card.progress, if (card.overdue) Colors.Warn else Colors.Accent) {
                    Icon(TypeIcons.of(card.type), contentDescription = null, tint = Colors.Ink)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Colors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(card.doseLine, style = body, color = Colors.Muted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (card.overdue) card.dueLine?.let { Text(it, style = body, color = Colors.Warn) }
                Text(card.lastTakenLine, style = body, color = Colors.Muted)
                if (!card.overdue) card.dueLine?.let { Text(it, style = body, color = Colors.Muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val multiplier = TodayText.multiplier(med.lastMultiplier)
                SlateButton(
                    onClick = onAmount,
                    modifier = Modifier
                        .widthIn(min = 64.dp)
                        .semantics { contentDescription = "Change amount for ${med.name}, now $multiplier" },
                ) {
                    Text(multiplier, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp).merge(Numbers))
                }
                val amount = TodayText.amount(med, med.lastMultiplier)
                SlateButton(
                    onClick = {
                        justTaken = true
                        onTake()
                    },
                    enabled = !justTaken,
                    kind = ButtonKind.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = if (justTaken) "${med.name} logged" else "Take ${med.name}, $amount" },
                ) {
                    Icon(Glyphs.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        if (justTaken) "Logged" else "Take ${med.name}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
