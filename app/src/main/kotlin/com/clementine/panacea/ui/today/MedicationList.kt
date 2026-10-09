package com.clementine.panacea.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clementine.panacea.data.ListLayout
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.delay

/** A run of medications under one label; [label] is null when there's only the one run and nothing to tell apart */
data class ListSection(val label: String?, val cards: List<CardState>)

/** Grouped and Compact split by category, as the filters do; When Due by what needs doing first */
fun listSections(cards: List<CardState>, layout: ListLayout): List<ListSection> = when (layout) {
    ListLayout.WHEN_DUE -> listOf(
        "Missed" to cards.filter { it.overdue },
        "Due Now" to cards.filter { it.dueNow && !it.overdue },
        "Later" to cards.filter { !it.dueNow && !it.overdue && it.dueLine != null },
        "As Needed" to cards.filter { !it.dueNow && !it.overdue && it.dueLine == null },
    ).filter { it.second.isNotEmpty() }.map { ListSection(it.first, it.second) }
    else -> {
        val byCategory = Category.entries.map { c -> c to cards.filter { it.category == c } }.filter { it.second.isNotEmpty() }
        if (byCategory.size == 1) listOf(ListSection(null, cards))
        else byCategory.map { (c, list) -> ListSection(c.groupLabel, list) }
    }
}

@Composable
fun ListLabel(text: String, warn: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warn) Colors.Warn else Colors.Muted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() },
    )
}

/** Several medications in one card, split by hairlines; [compact] rows trade the amount button for a long press */
@Composable
fun MedicationGroup(
    cards: List<CardState>,
    compact: Boolean,
    onTake: (CardState) -> Unit,
    onAmount: (CardState) -> Unit,
    onOpen: (CardState) -> Unit,
) {
    SlateCard(Modifier.fillMaxWidth()) {
        Column {
            cards.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(horizontal = 14.dp))
                if (compact) {
                    CompactRow(card, { onTake(card) }, { onAmount(card) }, { onOpen(card) })
                } else {
                    MedicationBody(card, { onTake(card) }, { onAmount(card) }, { onOpen(card) })
                }
            }
        }
    }
}

/** Everything a medication's card holds, without the card: its title row, status lines, × and Take */
@Composable
fun MedicationBody(card: CardState, onTake: () -> Unit, onAmount: () -> Unit, onOpen: () -> Unit) {
    val med = card.medication
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClickLabel = "Open ${med.name}", onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardRing(card)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(med.name, style = MaterialTheme.typography.titleMedium, color = Colors.Ink, modifier = Modifier.semantics { heading() })
                Text(card.doseLine, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
            }
            Icon(Glyphs.ChevronRight, contentDescription = null, tint = Colors.Faint)
        }
        StatusLines(card)
        TakeButtons(med, card.dueNow, onTake, onAmount)
    }
}

/** One row: the ring, the name, when it was last taken and is next due, and a round Take */
@Composable
private fun CompactRow(card: CardState, onTake: () -> Unit, onAmount: () -> Unit, onOpen: () -> Unit) {
    val med = card.medication
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open ${med.name}", onClick = onOpen)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CardRing(card)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(med.name, style = MaterialTheme.typography.titleMedium, color = Colors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            Text(card.lastTakenLine, style = body, color = Colors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.dueLine?.let {
                Text(it, style = body, color = if (card.overdue) Colors.Warn else Colors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        RoundTake(card, onTake, onAmount)
    }
}

/** Take as a 48dp circle; a long press opens the take sheet, and an amount other than one shows on it */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundTake(card: CardState, onTake: () -> Unit, onAmount: () -> Unit) {
    val med = card.medication
    // A second tap right after the first is almost always a slip, as on the full Take button
    var justTaken by remember { mutableStateOf(false) }
    LaunchedEffect(justTaken) {
        if (justTaken) {
            delay(TAKE_LOCK_MS)
            justTaken = false
        }
    }
    val amount = TodayText.amount(med, med.lastMultiplier)
    val pieces = MedicationType.fromKey(med.type).pieces(med.lastMultiplier)
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (card.dueNow) Colors.Accent else Colors.Field)
                .combinedClickable(
                    enabled = !justTaken,
                    role = Role.Button,
                    onClickLabel = "Take",
                    onLongClickLabel = "Change amount, now $pieces",
                    onLongClick = onAmount,
                    onClick = {
                        justTaken = true
                        onTake()
                    },
                )
                .semantics { contentDescription = if (justTaken) "${med.name} logged" else "Take $amount of ${med.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Glyphs.Check,
                contentDescription = null,
                tint = when {
                    justTaken -> Colors.Faint
                    card.dueNow -> Colors.OnAccent
                    else -> Colors.Accent
                },
                modifier = Modifier.size(22.dp),
            )
        }
        if (med.lastMultiplier != 1.0) {
            Text(
                TodayText.multiplier(med.lastMultiplier),
                style = MaterialTheme.typography.labelSmall.merge(Numbers),
                color = Colors.Ink,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .background(Colors.Raised, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
