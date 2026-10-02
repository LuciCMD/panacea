package com.clementine.panacea.ui.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.data.db.MedicationCounts
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.DoseUnits
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.WeightUnit
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.FieldLabel
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.SlateDropdown
import com.clementine.panacea.ui.components.SlateTextField
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.launch

/** The forms people use most; the rest show under More. */
private val CommonTypes = listOf(
    MedicationType.ORAL_TABLET, MedicationType.ORAL_CAPSULE, MedicationType.LIQUID_SYRUP, MedicationType.EDIBLE,
    MedicationType.DROPS, MedicationType.INHALER, MedicationType.IV_INJECTION,
)

/** Adds a medication when [id] is null, otherwise edits that one. */
@Composable
fun EditMedicationScreen(
    id: Long?,
    onDone: () -> Unit,
    viewModel: EditMedicationViewModel = viewModel(factory = EditMedicationViewModel.Factory),
) {
    val saver = EditMedicationViewModel.DraftSaver
    var draft by rememberSaveable(stateSaver = saver) { mutableStateOf(if (id == null) MedicationDraft() else null) }
    var original by rememberSaveable(stateSaver = saver) { mutableStateOf(if (id == null) MedicationDraft() else null) }
    LaunchedEffect(id) {
        if (id != null && draft == null) {
            val loaded = viewModel.load(id) ?: return@LaunchedEffect onDone()
            draft = loaded
            original = loaded
        }
    }
    val d = draft ?: return
    val update = { change: MedicationDraft -> draft = change }

    var tried by rememberSaveable { mutableStateOf(false) }
    var problems by remember { mutableStateOf(DraftProblems()) }
    LaunchedEffect(d, tried) { if (tried) problems = viewModel.check(d) }

    var camera by rememberSaveable { mutableStateOf<String?>(null) }   // sides, comma separated
    var discarding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<MedicationCounts?>(null) }
    val scope = rememberCoroutineScope()

    val setPhoto = { side: PillSide, name: String? ->
        val old = if (side == PillSide.FRONT) d.photoFront else d.photoBack
        // A photo taken in this visit and replaced again is no use to anyone.
        if (old != null && old != original?.photoFront && old != original?.photoBack) viewModel.photos.delete(old)
        draft = (draft ?: d).let { if (side == PillSide.FRONT) it.copy(photoFront = name) else it.copy(photoBack = name) }
    }

    camera?.let { sides ->
        PillCamera(
            sides = sides.split(',').map(PillSide::valueOf),
            onPhoto = { side, name -> setPhoto(side, name) },
            onClose = { camera = null },
        )
        return
    }

    val close = {
        if (d != original) {
            discarding = true
        } else {
            onDone()
        }
    }
    BackHandler(onBack = close)

    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = screenPadding(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                IconButton(onClick = close, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Glyphs.Close, contentDescription = "Close without saving", tint = Colors.Ink)
                }
                Text(
                    if (d.isNew) "Add Medication" else "Edit Medication",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Colors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item(key = "basics") { BasicsCard(d, problems, update) }
        item(key = "category") { CategoryCard(d, update) }
        item(key = "type") { TypeCard(d, update) }
        item(key = "photos") {
            PhotosCard(
                d,
                onCamera = { sides -> camera = sides.joinToString(",") { it.name } },
                onSet = setPhoto,
            )
        }
        item(key = "ingredients") { IngredientsCard(d, problems, update) }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (tried && !problems.none) {
                    Text("Some fields above need a look.", style = MaterialTheme.typography.bodyMedium, color = Colors.Bad)
                }
                SlateButton(
                    onClick = {
                        scope.launch {
                            tried = true
                            problems = viewModel.check(d)
                            if (problems.none) viewModel.save(d) { onDone() }
                        }
                    },
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (d.isNew) "Add Medication" else "Save", style = MaterialTheme.typography.labelLarge) }
                if (!d.isNew) {
                    SlateButton(
                        onClick = { scope.launch { removing = viewModel.counts(d.id) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove Medication") }
                }
            }
        }
    }

    if (discarding) {
        ConfirmDialog(
            title = "Discard Changes?",
            text = if (d.isNew) "This medication won't be added." else "What you changed won't be saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep Editing",
            onConfirm = {
                discarding = false
                viewModel.discardPhotos(d, original)
                onDone()
            },
            onDismiss = { discarding = false },
        )
    }
    removing?.let { counts ->
        val name = original?.name ?: d.name
        ConfirmDialog(
            title = "Remove $name?",
            text = Drafts.removeQuestion(counts),
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                removing = null
                viewModel.discardPhotos(d, original)
                viewModel.delete(d.id, onDone)
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun BasicsCard(d: MedicationDraft, problems: DraftProblems, update: (MedicationDraft) -> Unit) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val noun = d.type.noun
    SectionCard("Medication") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Name")
            SlateTextField(
                d.name, { update(d.copy(name = it)) },
                error = problems.name,
                accessibleLabel = "Name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Dose")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlateTextField(d.dose, { update(d.copy(dose = it)) }, Modifier.weight(1f), placeholder = "50", error = problems.dose, accessibleLabel = "Dose", keyboardOptions = decimal)
                SlateDropdown(d.doseUnit, (DoseUnits + d.doseUnit).distinct(), { it }, { update(d.copy(doseUnit = it)) }, "Dose unit")
            }
            Hint("The active amount in one $noun, as on the label.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Pill Weight")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlateTextField(d.weight, { update(d.copy(weight = it)) }, Modifier.weight(1f), placeholder = "0.25", error = problems.weight, accessibleLabel = "Pill weight", keyboardOptions = decimal)
                SlateDropdown(
                    WeightUnit.fromKey(d.weightUnit), WeightUnit.entries, { it.key },
                    { update(d.copy(weightUnit = it.key)) }, "Weight unit",
                )
            }
            Hint("What one $noun weighs on a scale. Leave it empty if you don't know.")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
}

@Composable
private fun CategoryCard(d: MedicationDraft, update: (MedicationDraft) -> Unit) {
    SectionCard("Category") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Category.PRESCRIBED, Category.OTC, Category.RECREATIONAL, Category.UNCATEGORIZED).forEach { c ->
                SlateChip(selected = d.category == c, onClick = { update(d.copy(category = c)) }) {
                    Text(c.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypeCard(d: MedicationDraft, update: (MedicationDraft) -> Unit) {
    var all by rememberSaveable { mutableStateOf(d.type !in CommonTypes) }
    SectionCard("Type") {
        val types = if (all) MedicationType.entries else CommonTypes
        val tiles: List<MedicationType?> = if (all) types else types + null   // null is "More"
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    TypeTile(
                        label = t?.label ?: "More",
                        icon = t?.let(TypeIcons::of) ?: Glyphs.More,
                        selected = t != null && t == d.type,
                        modifier = Modifier.weight(1f),
                        onClick = { if (t == null) all = true else update(d.copy(type = t)) },
                    )
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TypeTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        shape = MaterialTheme.shapes.small,
        color = if (selected) Colors.AccentWash else Colors.Field,
        contentColor = if (selected) Colors.Accent else Colors.Ink,
        border = if (selected) BorderStroke(1.dp, Colors.Accent) else null,
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PhotosCard(d: MedicationDraft, onCamera: (List<PillSide>) -> Unit, onSet: (PillSide, String?) -> Unit) {
    val store = LocalPhotoStore.current
    val scope = rememberCoroutineScope()
    var pickingFor by rememberSaveable { mutableStateOf<PillSide?>(null) }
    var viewing by remember { mutableStateOf<PillSide?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val side = pickingFor
        pickingFor = null
        if (uri != null && side != null) scope.launch { store.import(uri)?.let { onSet(side, it) } }
    }
    SectionCard("Pill Photos", hint = "Both sides of the pill, so you can check one at a glance instead of looking it up. They stay on this phone.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillSide.entries.forEach { side ->
                val photo = if (side == PillSide.FRONT) d.photoFront else d.photoBack
                PhotoSlot(
                    side = side,
                    photo = photo,
                    modifier = Modifier.weight(1f),
                    onTake = {
                        // With no photos yet, walk through both sides like an ID check.
                        onCamera(if (d.photoFront == null && d.photoBack == null) PillSide.entries else listOf(side))
                    },
                    onChoose = {
                        pickingFor = side
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onView = { viewing = side },
                    onRemove = { onSet(side, null) },
                )
            }
        }
    }
    viewing?.let { PhotoViewer(d.name.ifBlank { "Your Pill" }, d.photoFront, d.photoBack, it) { viewing = null } }
}

@Composable
private fun PhotoSlot(
    side: PillSide,
    photo: String?,
    modifier: Modifier,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onView: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Surface(
                onClick = { if (photo == null) onTake() else menu = true },
                shape = RoundedCornerShape(12.dp),
                color = Colors.Field,
                contentColor = Colors.Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = if (photo == null) "Take a photo of the ${side.label.lowercase()}" else "${side.label} photo, options" },
            ) {
                if (photo != null) {
                    PillPhoto(photo, null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), px = 512)
                } else {
                    Column(
                        Modifier.fillMaxSize().border(1.dp, Colors.Line, RoundedCornerShape(12.dp)),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Glyphs.Camera, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text("Add ${side.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = Colors.Raised,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, Colors.LineSoft),
            ) {
                listOf(
                    "View" to onView,
                    "Retake Photo" to onTake,
                    "Choose From Photos" to onChoose,
                    "Remove Photo" to onRemove,
                ).forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label, color = Colors.Ink) }, onClick = {
                        menu = false
                        action()
                    })
                }
            }
        }
        Text(side.label, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
        if (photo == null) {
            SlateButton(onClick = onChoose, kind = ButtonKind.Text, modifier = Modifier.semantics { contentDescription = "Choose a photo of the ${side.label.lowercase()}" }) {
                Text("Choose From Photos", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IngredientsCard(d: MedicationDraft, problems: DraftProblems, update: (MedicationDraft) -> Unit) {
    SectionCard("Ingredients", hint = "Optional. Other actives in the same ${d.type.noun}; each one scales with the amount you take.") {
        d.ingredients.forEachIndexed { i, ing ->
            val set = { changed: IngredientDraft -> update(d.copy(ingredients = d.ingredients.toMutableList().also { it[i] = changed })) }
            Column(
                Modifier.fillMaxWidth().background(Colors.Raised, MaterialTheme.shapes.small).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SlateTextField(
                        ing.name, { set(ing.copy(name = it)) }, Modifier.weight(1f),
                        placeholder = "Name",
                        error = problems.ingredients[i]?.takeIf { ing.name.isBlank() },
                        accessibleLabel = "Ingredient ${i + 1} name",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                    IconButton(onClick = { update(d.copy(ingredients = d.ingredients.filterIndexed { j, _ -> j != i })) }) {
                        Icon(Glyphs.Close, contentDescription = "Remove ingredient ${i + 1}", tint = Colors.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 52.dp)) {
                    SlateTextField(
                        ing.amount, { set(ing.copy(amount = it)) }, Modifier.weight(1f),
                        placeholder = "Amount",
                        error = problems.ingredients[i]?.takeIf { ing.name.isNotBlank() },
                        accessibleLabel = "Ingredient ${i + 1} amount",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    )
                    SlateDropdown(ing.unit, (DoseUnits + ing.unit).distinct(), { it }, { set(ing.copy(unit = it)) }, "Ingredient ${i + 1} unit")
                }
            }
        }
        if (d.ingredients.size < Drafts.MAX_INGREDIENTS) {
            SlateButton(onClick = { update(d.copy(ingredients = d.ingredients + IngredientDraft())) }) {
                Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Add Ingredient", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
