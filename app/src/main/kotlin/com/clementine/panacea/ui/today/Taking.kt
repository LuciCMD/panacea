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
 * What's been had, then what's due, always in that order. The answers lead in ink; the next time is
 * muted until it's missed, when it turns warn at the same size. [withTotal] adds the last 24 hours'
 * total, which the medication's page shows and Today leaves out.
 */
@Composable
fun StatusLines(card: CardState, withTotal: Boolean = false) {
    val lead = MaterialTheme.typography.bodyLarge.merge(Numbers)
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(keepPhrases(card.lastTakenLine), style = lead, color = Colors.Ink)
        if (withTotal) Text(keepPhrases(card.last24hLine), style = lead, color = Colors.Ink)
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

/**
 * The bar after a dose is logged: Undo removes it in one tap, and the bar then offers Put Back, so a
 * slip either way is one tap to mend. Removing older doses, from History, still asks first.
 */
@Composable
fun UndoBar(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val sounds = LocalSoundPlayer.current
    val haptics = LocalHapticFeedback.current
    val current = notice ?: return
    // Undo and Put Back share a place, so a quick second tap meant for the first does nothing.
    var ready by remember(current) { mutableStateOf(false) }
    LaunchedEffect(current) {
        delay(ACTION_REST_MS)
        ready = true
    }
    when (current) {
        is LoggedDose -> SlateToast(
            message = current.message,
            actionLabel = "Undo",
            onAction = {
                if (ready) {
                    viewModel.undo(current)
                    sounds.play(SoundEvent.UNDO)
                }
            },
            modifier = modifier,
        )
        is RemovedDose -> SlateToast(
            message = current.message,
            actionLabel = "Put Back",
            onAction = {
                if (ready) {
                    viewModel.putBack(current)
                    sounds.play(SoundEvent.TAKE)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            },
            modifier = modifier,
            stripe = Colors.Muted,
        )
    }
}

private const val ACTION_REST_MS = 600L
