package com.clementine.panacea.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.clementine.panacea.model.Amounts
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.TimeFormats
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.DoseTimeDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.RoundIconButton
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.SlateSheet
import com.clementine.panacea.ui.components.textIcon
import com.clementine.panacea.ui.edit.PhotoViewer
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.edit.PillSide
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Take a medication with a different amount or at an earlier time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeSheet(
    card: CardState,
    presets: List<Double>,
    formats: TimeFormats,
    onTake: (multiplier: Double, takenAt: Long?) -> Unit,
    onSavePreset: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val med = card.medication
    var multiplier by rememberSaveable(med.id) { mutableDoubleStateOf(med.lastMultiplier) }
    var takenAt by rememberSaveable(med.id) { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PillSide?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now = remember { ZonedDateTime.now() }

    SlateSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Take ${med.name}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            perItem(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted) }
        }

        // What's been had, beside the choice of how much more: the moment it matters most.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val line = MaterialTheme.typography.bodyMedium.merge(Numbers)
            Text(keepPhrases(card.lastTakenLine), style = line, color = Colors.Ink)
            card.had.forEach { h ->
                Text(
                    buildAnnotatedString {
                        append(keepPhrases(TodayText.had(h)))
                        withStyle(SpanStyle(color = Colors.Muted)) { append(" · " + keepPhrases(TodayText.withThisOne(h, multiplier))) }
                    },
                    style = line,
                    color = Colors.Ink,
                )
            }
        }

        if (med.photoFront != null || med.photoBack != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillSide.entries.forEach { side ->
                    val photo = if (side == PillSide.FRONT) med.photoFront else med.photoBack
                    if (photo != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PillPhoto(
                                photo, null,
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button, onClickLabel = "Look closer") { viewing = side }
                                    .semantics { contentDescription = "${side.label} of ${med.name}" },
                                px = 216,
                            )
                            Text(side.label, style = MaterialTheme.typography.bodySmall, color = Colors.Muted)
                        }
                    }
                }
                Text("Tap a photo to check it up close.", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted, modifier = Modifier.weight(1f))
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Colors.Raised, MaterialTheme.shapes.medium).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Glyphs.Minus, "Smaller Amount", enabled = multiplier > Amounts.MIN) { multiplier = Amounts.less(multiplier) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    TodayText.multiplier(multiplier),
                    style = MaterialTheme.typography.displaySmall.merge(Numbers),
                    // Read as "1.5 tablets", not "multiplication sign 1.5".
                    modifier = Modifier.semantics { contentDescription = MedicationType.fromKey(med.type).pieces(multiplier) },
                )
                Text(
                    listOfNotNull(
                        TodayText.amount(med, multiplier).takeIf { med.dose > 0 },
                        TodayText.weightOf(med, multiplier),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge.merge(Numbers),
                    color = Colors.Muted,
                )
            }
            RoundIconButton(Glyphs.Plus, "Larger Amount", enabled = multiplier < Amounts.MAX) { multiplier = Amounts.more(multiplier) }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                SlateChip(
                    selected = p == multiplier,
                    onClick = { multiplier = p },
                    modifier = Modifier.semantics { contentDescription = MedicationType.fromKey(med.type).pieces(p) },
                ) {
                    Text(TodayText.multiplier(p), style = MaterialTheme.typography.bodyMedium.merge(Numbers))
                }
            }
            if (multiplier !in presets && presets.size < Amounts.MAX_PRESETS) {
                SlateChip(
                    selected = false,
                    onClick = { onSavePreset(multiplier) },
                    role = Role.Button,
                    modifier = Modifier.semantics { contentDescription = "Save ${MedicationType.fromKey(med.type).pieces(multiplier)} as a Preset" },
                ) {
                    Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(textIcon(18.dp)))
                    Text("Save as Preset", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        HorizontalDivider(color = Colors.LineSoft)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Taken At", style = MaterialTheme.typography.bodyLarge)
                Text(takenAtText(takenAt, now, formats), style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
            }
            SlateButton(onClick = { picking = true }) {
                Icon(Glyphs.Clock, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
                Text(if (takenAt == null) "I Took It Earlier" else "Change Time", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (multiplier != med.lastMultiplier) {
            Text("This amount is kept for next time.", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
        }

        SlateButton(
            onClick = {
                onTake(multiplier, takenAt)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Glyphs.Check, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
            Text("Take ${TodayText.amount(med, multiplier)}", style = MaterialTheme.typography.labelLarge.merge(Numbers))
        }
    }

    viewing?.let { PhotoViewer(med.name, med.photoFront, med.photoBack, it) { viewing = null } }

    if (picking) {
        DoseTimeDialog(
            initial = takenAt?.let { Instant.ofEpochMilli(it).atZone(now.zone) } ?: ZonedDateTime.now(),
            onPick = { at ->
                takenAt = at.toInstant().toEpochMilli()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** "50 mg per tablet · each weighs 0.31 g", or null when neither is known. */
private fun perItem(card: CardState): String? {
    val med = card.medication
    return listOfNotNull(
        "${formatAmount(med.dose)} ${med.doseUnit} per ${card.type.noun}".takeIf { med.dose > 0 },
        TodayText.weightOf(med, 1.0)?.let { "each weighs $it" },
    ).joinToString(" · ").ifEmpty { null }
}

private fun takenAtText(takenAt: Long?, now: ZonedDateTime, f: TimeFormats): String {
    if (takenAt == null) return "Now, ${ZonedDateTime.now().format(f.time)}"
    val at = Instant.ofEpochMilli(takenAt).atZone(now.zone)
    val day = if (at.toLocalDate() == ZonedDateTime.now().toLocalDate()) "Today" else "Yesterday"
    return "$day at ${at.format(f.time)}"
}

