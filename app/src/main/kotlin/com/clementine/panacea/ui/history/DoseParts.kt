package com.clementine.panacea.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clementine.panacea.data.rescaled
import com.clementine.panacea.model.Amounts
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.DoseTimeDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.RoundIconButton
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateSheet
import com.clementine.panacea.ui.components.TimeDialog
import com.clementine.panacea.ui.components.textIcon
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.today.TodayText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A day of doses: its name and total above a card of rows. [showMedication] names each dose's
 * medication, with its photo; on a medication's own page that goes without saying.
 */
@Composable
fun DayCard(day: HistoryDay, showMedication: Boolean, onOpen: (HistoryItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day.title, style = MaterialTheme.typography.labelMedium, color = Colors.Muted, modifier = Modifier.weight(1f))
            Text(day.summary, style = MaterialTheme.typography.bodySmall.merge(Numbers), color = Colors.Muted)
        }
        SlateCard(Modifier.fillMaxWidth()) {
            Column {
                day.doses.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(start = if (showMedication) 66.dp else 14.dp))
                    DoseRow(item, showMedication) { onOpen(item) }
                }
            }
        }
    }
}

/** One logged dose, the same on the History tab and a medication's page: what, then when. */
@Composable
private fun DoseRow(item: HistoryItem, showMedication: Boolean, onOpen: () -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open this dose", onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showMedication) DoseBadge(item, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showMedication) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Colors.Ink)
                Text(item.amount, style = body, color = Colors.Muted)
            } else {
                Text(item.amount, style = MaterialTheme.typography.bodyLarge.merge(Numbers), color = Colors.Ink)
            }
        }
        Text(item.time, style = body, color = Colors.Muted)
    }
}

/** The pill's own photo, or its form's icon on a raised circle. */
@Composable
private fun DoseBadge(item: HistoryItem, size: Dp) {
    if (item.photo != null) {
        PillPhoto(item.photo, null, Modifier.size(size).clip(CircleShape), px = 160)
    } else {
        Box(Modifier.size(size).background(Colors.Raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(TypeIcons.of(item.type), contentDescription = null, tint = Colors.Ink, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/**
 * A logged dose up close: what was taken and when, with Change Time and Change Amount for a dose
 * logged wrong, and Remove Dose, which asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseSheet(
    item: HistoryItem,
    onChangeTime: (Long) -> Unit,
    onChangeAmount: (Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var amounting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val sounds = LocalSoundPlayer.current

    SlateSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DoseBadge(item, 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(item.taken, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.amount, style = MaterialTheme.typography.bodyLarge.merge(Numbers), color = Colors.Ink)
            item.ingredients?.let { Text(it, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SlateButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Glyphs.Clock, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
                Text("Change Time", style = MaterialTheme.typography.labelLarge)
            }
            SlateButton(onClick = { amounting = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Glyphs.Pencil, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
                Text("Change Amount", style = MaterialTheme.typography.labelLarge)
            }
            // Plain, not red: the red is for the confirm that follows.
            SlateButton(onClick = { removing = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Remove Dose", style = MaterialTheme.typography.labelLarge, color = Colors.Bad)
            }
        }
    }

    if (picking) {
        val zone = ZoneId.systemDefault()
        val at = Instant.ofEpochMilli(item.dose.takenAt).atZone(zone)
        if (at.toLocalDate() >= LocalDate.now(zone).minusDays(1)) {
            DoseTimeDialog(
                initial = at,
                confirmLabel = "Change Time",
                onPick = {
                    onChangeTime(it.toInstant().toEpochMilli())
                    picking = false
                },
                onDismiss = { picking = false },
            )
        } else {
            // An older dose keeps its day.
            TimeDialog(
                title = "When Did You Take It?",
                initial = at.toLocalTime(),
                confirmLabel = "Change Time",
                onPick = { time ->
                    onChangeTime(at.with(time).withSecond(0).withNano(0).toInstant().toEpochMilli())
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
    if (amounting) {
        AmountDialog(item, onSave = { onChangeAmount(it); amounting = false }, onDismiss = { amounting = false })
    }
    if (removing) {
        ConfirmDialog(
            title = "Remove This Dose?",
            text = item.removeQuestion,
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                onRemove()
                sounds.play(SoundEvent.UNDO)
                removing = false
                onDismiss()
            },
            onDismiss = { removing = false },
        )
    }
}

/** How much a logged dose really was, at the dose it was logged at. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountDialog(item: HistoryItem, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var multiplier by remember { mutableDoubleStateOf(item.dose.multiplier) }
    val title = "How Much Did You Take?"
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = title }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Colors.Ink)
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(Glyphs.Minus, "Smaller Amount", enabled = multiplier > Amounts.MIN) { multiplier = Amounts.less(multiplier) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            TodayText.multiplier(multiplier),
                            style = MaterialTheme.typography.headlineMedium.merge(Numbers),
                            color = Colors.Ink,
                            modifier = Modifier.semantics { contentDescription = item.type.pieces(multiplier) },
                        )
                        Text(
                            HistoryModel.amountLine(rescaled(item.dose, multiplier)),
                            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                            color = Colors.Muted,
                        )
                    }
                    RoundIconButton(Glyphs.Plus, "Larger Amount", enabled = multiplier < Amounts.MAX) { multiplier = Amounts.more(multiplier) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    SlateButton(onClick = onDismiss) { Text("Cancel") }
                    SlateButton(onClick = { onSave(multiplier) }, kind = ButtonKind.Primary, enabled = multiplier != item.dose.multiplier) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
