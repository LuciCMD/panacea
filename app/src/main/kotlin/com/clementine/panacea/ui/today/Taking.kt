package com.clementine.panacea.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateToast
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.delay

private const val TAKE_LOCK_MS = 3_000L

/** Logs a dose with its sound and a tap of feedback; takenAt null means now. */
@Composable
fun rememberTake(viewModel: TodayViewModel): (medicationId: Long, multiplier: Double, takenAt: Long?) -> Unit {
    val sounds = LocalSoundPlayer.current
    val haptics = LocalHapticFeedback.current
    return remember(viewModel, sounds, haptics) {
        { medicationId, multiplier, takenAt ->
            viewModel.take(medicationId, multiplier, takenAt)
            sounds.play(SoundEvent.TAKE)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
}

/**
 * What's been had, then what's due, always in that order. The two answers lead in ink; the next time
 * is muted until it's missed, when it turns warn at the same size.
 */
@Composable
fun StatusLines(card: CardState) {
    val lead = MaterialTheme.typography.bodyLarge.merge(Numbers)
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(keepPhrases(card.lastTakenLine), style = lead, color = Colors.Ink)
        Text(keepPhrases(card.last24hLine), style = lead, color = Colors.Ink)
        card.dueLine?.let {
            Text(
                keepPhrases(it),
                style = if (card.overdue) lead else body,
                color = if (card.overdue) Colors.Warn else Colors.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Lets a status line wrap only at its "·", so "3 h 52 min ago" never leaves "ago" on a line alone. */
private fun keepPhrases(line: String): String =
    line.split(" · ").joinToString(" · ") { it.replace(' ', NO_BREAK) }

private val NO_BREAK = Char(0xA0)

/**
 * The amount button and Take, as on every medication card. Take is full accent when a dose is
 * [dueNow], and a quieter tint otherwise: still easy to tap, but not the loudest thing on the card.
 */
@Composable
fun TakeButtons(med: MedicationEntity, dueNow: Boolean, onTake: () -> Unit, onAmount: () -> Unit) {
    // A second tap right after the first is almost always a slip, so the button rests briefly.
    var justTaken by remember { mutableStateOf(false) }
    LaunchedEffect(justTaken) {
        if (justTaken) {
            delay(TAKE_LOCK_MS)
            justTaken = false
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val pieces = MedicationType.fromKey(med.type).pieces(med.lastMultiplier)
        // Anything but one of it stands out, so a large amount left from last time is noticed.
        val usual = med.lastMultiplier == 1.0
        SlateButton(
            onClick = onAmount,
            modifier = Modifier
                .widthIn(min = 64.dp)
                .semantics { contentDescription = "Change amount for ${med.name}, now $pieces" },
        ) {
            Text(
                TodayText.multiplier(med.lastMultiplier),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp).merge(Numbers),
                color = if (usual) Colors.Muted else Colors.Ink,
                fontWeight = if (usual) FontWeight.Normal else FontWeight.Medium,
            )
        }
        val amount = TodayText.amount(med, med.lastMultiplier)
        SlateButton(
            onClick = {
                justTaken = true
                onTake()
            },
            enabled = !justTaken,
            kind = if (dueNow) ButtonKind.Primary else ButtonKind.Tonal,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = if (justTaken) "${med.name} logged" else "Take $amount of ${med.name}" },
        ) {
            Icon(Glyphs.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                // The name is the card's title; the button says what a tap will log.
                if (justTaken) "Logged" else "Take $amount",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The bar offering Undo for the dose just logged, and the question before it goes. */
@Composable
fun UndoBar(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val logged by viewModel.logged.collectAsStateWithLifecycle()
    val sounds = LocalSoundPlayer.current
    var confirming by remember { mutableStateOf<LoggedDose?>(null) }

    val current = logged
    if (current != null && confirming == null) {
        SlateToast(message = current.message, actionLabel = "Undo", onAction = { confirming = current }, modifier = modifier)
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
