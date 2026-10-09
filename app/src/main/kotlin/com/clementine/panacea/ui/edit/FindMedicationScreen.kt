package com.clementine.panacea.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.catalog.CatalogEntry
import com.clementine.panacea.data.catalog.CatalogForm
import com.clementine.panacea.data.catalog.CatalogHit
import com.clementine.panacea.data.catalog.CatalogKind
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.DEFAULT_DOSE_UNIT
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.model.plainAmount
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.SlateIconButton
import com.clementine.panacea.ui.components.SlateSheet
import com.clementine.panacea.ui.components.SlateTextField
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.delay

/**
 * The first step of adding a medication: find it in the catalog, which fills in its strength and
 * type, or carry on with a name of the user's own
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindMedicationScreen(
    onPick: (entry: Int, form: Int?) -> Unit,
    onCustom: (name: String) -> Unit,
    onBack: () -> Unit,
) {
    val catalog = (LocalContext.current.applicationContext as PanaceaApp).container.catalog
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf<CatalogEntry?>(null) }
    val hits by produceState(emptyList<CatalogHit>(), query) {
        // Waits out a burst of typing rather than searching on every letter
        delay(120)
        value = catalog.search(query)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        // Reads the list in while the first letters are typed
        catalog.entries()
    }
    BackHandler(onBack = onBack)

    val pick = { entry: CatalogEntry ->
        if (entry.forms.size > 1) choosing = entry else onPick(entry.index, if (entry.forms.size == 1) 0 else null)
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = screenPadding(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                SlateIconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Glyphs.Back, contentDescription = "Back", tint = Colors.Ink)
                }
                Text("Add Medication", style = MaterialTheme.typography.headlineMedium, color = Colors.Ink, modifier = Modifier.semantics { heading() })
            }
        }
        item(key = "search") {
            SlateTextField(
                query,
                { query = it },
                modifier = Modifier.focusRequester(focus),
                placeholder = "Search, such as Ibuprofen or Advil",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                accessibleLabel = "Search Medications",
            )
        }
        item(key = "own") {
            val name = query.trim()
            SlateCard(Modifier.fillMaxWidth()) {
                ResultRow(
                    title = if (name.isEmpty()) "Your Own Medication" else "Add “$name” as Your Own",
                    line = "For anything that isn't listed, or that you'd rather name yourself",
                    icon = { Icon(Glyphs.Pencil, contentDescription = null, tint = Colors.Accent) },
                ) { onCustom(name) }
            }
        }
        if (hits.isNotEmpty()) {
            item(key = "results") {
                SlateCard(Modifier.fillMaxWidth()) {
                    Column {
                        hits.forEachIndexed { i, hit ->
                            if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(horizontal = 14.dp))
                            ResultRow(hit.entry.name, FindText.hitLine(hit)) { pick(hit.entry) }
                        }
                    }
                }
            }
        } else if (query.trim().length >= 2) {
            item(key = "none") {
                Text(
                    "Nothing listed by that name. Add it as your own above, and fill in the rest yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            item(key = "about") {
                Text(
                    "Search US brand and generic names, international names, supplements and more. Picking one fills in " +
                        "its strength, type and category, which you can still change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    choosing?.let { entry ->
        SlateSheet(onDismiss = { choosing = null }) {
            Text(
                "Which ${entry.name}?",
                style = MaterialTheme.typography.titleLarge,
                color = Colors.Ink,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            Column(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                entry.forms.forEachIndexed { i, form ->
                    if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(horizontal = 20.dp))
                    ResultRow(FindText.formLine(form), FindText.formCategory(form), padding = 20.dp) {
                        choosing = null
                        onPick(entry.index, i)
                    }
                }
                HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(horizontal = 20.dp))
                ResultRow("Another Strength", "Fill in the amount yourself", padding = 20.dp) {
                    choosing = null
                    onPick(entry.index, null)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    line: String?,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = padding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.merge(Numbers), color = Colors.Ink)
            line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted) }
        }
        Icon(Glyphs.ChevronRight, contentDescription = null, tint = Colors.Faint)
    }
}

/** The search's words, kept apart from Compose so they can be tested */
object FindText {
    fun hitLine(hit: CatalogHit): String {
        val e = hit.entry
        val also = hit.alias?.let { "also called $it" }
        val what = when (e.kind) {
            CatalogKind.BRAND -> "Brand of ${e.ingredients.joinToString(" and ") { it.lowercase() }}"
            CatalogKind.GENERIC -> "Generic"
            CatalogKind.OTHER -> if (e.forms.firstOrNull()?.category == Category.RECREATIONAL) "Recreational" else "Supplement"
        }
        return listOfNotNull(what, also).joinToString(", ")
    }

    /** "200 mg tablet", "100 mg per 5 mL liquid", "5 mg tablet with acetaminophen 325 mg" */
    fun formLine(form: CatalogForm): String {
        val amount = form.dose?.let { d -> "${formatAmount(d)} ${form.unit.orEmpty()}".trim() + (form.per?.let { " per $it" } ?: "") }
        val type = form.type.takeIf { it != MedicationType.OTHER && it != MedicationType.UNSPECIFIED }?.label?.lowercase()
        val with = form.others.takeIf { it.isNotEmpty() }?.joinToString(" and ", prefix = "with ") { "${it.name.lowercase()} ${formatAmount(it.amount)} ${it.unit}" }
        return listOfNotNull(amount, type, with).joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    fun formCategory(form: CatalogForm): String = when (form.category) {
        Category.PRESCRIBED -> "Prescription"
        Category.OTC -> "Over the counter"
        Category.RECREATIONAL -> "Recreational"
        Category.UNCATEGORIZED -> "Uncategorized"
    }
}

/** A new medication's form, filled in from what was picked */
fun catalogDraft(entry: CatalogEntry, form: CatalogForm?): MedicationDraft = MedicationDraft(
    name = entry.name,
    dose = form?.dose?.let(::plainAmount).orEmpty(),
    doseUnit = form?.unit ?: DEFAULT_DOSE_UNIT,
    category = form?.category ?: entry.forms.firstOrNull()?.category ?: Category.UNCATEGORIZED,
    type = form?.type ?: entry.forms.firstOrNull()?.type ?: MedicationType.ORAL_TABLET,
    ingredients = form?.others.orEmpty().map { IngredientDraft(it.name, plainAmount(it.amount), it.unit) },
)
