package com.clementine.panacea.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.model.Category
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.EmptyCard
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.ProgressRing
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Colors

@Composable
fun TodayScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onRearrange: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val importProblems by viewModel.importProblems.collectAsStateWithLifecycle()
    val take = rememberTake(viewModel)
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }

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
                item(key = "header") {
                    ScreenHeader("Today", keepPhrases(state.header), info = TodayText.HOW_TO_READ.takeIf { state.cards.isNotEmpty() }) {
                        if (state.cards.size >= 2) {
                            SlateButton(
                                onClick = onRearrange,
                                kind = ButtonKind.Text,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.padding(end = 4.dp).size(48.dp).semantics { contentDescription = "Rearrange Medications" },
                            ) { Icon(Glyphs.Rearrange, contentDescription = null, tint = Colors.Muted) }
                        }
                        SlateButton(
                            onClick = onAdd,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Add Medication" },
                        ) { Icon(Glyphs.Plus, contentDescription = null) }
                    }
                }
                if (importProblems.isNotEmpty()) {
                    item(key = "import") { ImportProblemsCard(importProblems, viewModel::dismissImportProblems) }
                }
                if (state.cards.isEmpty()) {
                    item(key = "empty") {
                        EmptyCard(
                            "No Medications Yet",
                            "Add what you take, and Today shows how much you last took and when, and when the next dose is due.",
                            "Add Medication",
                            Glyphs.Plus,
                            onAdd,
                        )
                    }
                }
                if (categories.size > 1) {
                    item(key = "filters") { Filters(categories, shown) { filter = it?.key } }
                }
                val cards = state.cards.filter { shown == null || it.category == shown }
                items(cards, key = { it.medication.id }) { card ->
                    MedicationCard(
                        card,
                        onTake = { take(card.medication.id, card.medication.lastMultiplier, null) },
                        onAmount = { sheetFor = card.medication.id },
                        onOpen = { onOpen(card.medication.id) },
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

        UndoBar(
            viewModel,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun Filters(categories: List<Category>, shown: Category?, onPick: (Category?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("All", shown == null) { onPick(null) }
        categories.forEach { c -> FilterChip(c.groupLabel, shown == c) { onPick(c) } }
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


/** The wait for the next reminder around the pill's photo, or its form's icon. */
@Composable
fun CardRing(card: CardState) {
    val med = card.medication
    ProgressRing(card.progress, if (card.overdue) Colors.Warn else Colors.Accent) {
        // The pill's own photo says more than the form's icon.
        if (med.photoFront != null) {
            PillPhoto(med.photoFront, null, Modifier.size(40.dp).clip(CircleShape), px = 160)
        } else {
            Icon(TypeIcons.of(card.type), contentDescription = null, tint = Colors.Ink)
        }
    }
}

@Composable
private fun MedicationCard(card: CardState, onTake: () -> Unit, onAmount: () -> Unit, onOpen: () -> Unit) {
    val med = card.medication
    SlateCard(Modifier.fillMaxWidth()) {
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
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Colors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(card.doseLine, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
                }
                Icon(Glyphs.ChevronRight, contentDescription = null, tint = Colors.Faint)
            }
            StatusLines(card)
            TakeButtons(med, card.dueNow, onTake, onAmount)
        }
    }
}

/** Shown once after updating from 3.4 if something couldn't be brought over, until put away. */
@Composable
private fun ImportProblemsCard(problems: List<String>, onDismiss: () -> Unit) {
    SectionCard(
        "Some 3.4 Data Wasn't Brought Over",
        hint = "Everything else came across. Panacea 3.4's own files are still on this phone, unchanged.",
        warning = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            problems.forEach { problem ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = Colors.Warn, modifier = Modifier.clearAndSetSemantics { })
                    Text(problem, style = MaterialTheme.typography.bodyMedium, color = Colors.Ink)
                }
            }
        }
        SlateButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Got It") }
    }
}
