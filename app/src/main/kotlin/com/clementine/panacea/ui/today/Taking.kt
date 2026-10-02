package com.clementine.panacea.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clementine.panacea.data.db.MedicationEntity
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

private const val UNDO_SHOWN_MS = 10_000L
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

/** When it was last taken and when it's due; the overdue warning comes first. */
@Composable
fun StatusLines(card: CardState) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (card.overdue) card.dueLine?.let { Text(it, style = body, color = Colors.Warn) }
        Text(card.lastTakenLine, style = body, color = Colors.Muted)
        if (!card.overdue) card.dueLine?.let { Text(it, style = body, color = Colors.Muted) }
    }
}

/** The amount button and Take, as on every medication card. */
@Composable
fun TakeButtons(med: MedicationEntity, onTake: () -> Unit, onAmount: () -> Unit) {
    // A second tap right after the first is almost always a slip, so the button rests briefly.
    var justTaken by remember { mutableStateOf(false) }
    LaunchedEffect(justTaken) {
        if (justTaken) {
            delay(TAKE_LOCK_MS)
            justTaken = false
        }
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

/** The bar offering Undo for the dose just logged, and the question before it goes. */
@Composable
fun UndoBar(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val logged by viewModel.logged.collectAsStateWithLifecycle()
    val sounds = LocalSoundPlayer.current
    var confirming by remember { mutableStateOf<LoggedDose?>(null) }

    val current = logged
    if (current != null && confirming == null) {
        LaunchedEffect(current) {
            delay(UNDO_SHOWN_MS)
            viewModel.dismiss(current)
        }
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
