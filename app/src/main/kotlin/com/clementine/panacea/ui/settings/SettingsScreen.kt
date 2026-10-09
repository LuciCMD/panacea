package com.clementine.panacea.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.data.ListLayout
import com.clementine.panacea.data.Settings
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.sound.SoundLibrary
import com.clementine.panacea.sound.SoundMode
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.components.textIcon
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.theme.Palette
import com.clementine.panacea.ui.theme.Themes
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val listLayout by viewModel.listLayout.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ScreenHeader("Settings") }
        item(key = "theme") { ThemeCard(theme, viewModel::setTheme) }
        item(key = "layout") { LayoutCard(listLayout, viewModel::setListLayout) }
        item(key = "sounds") { SoundsCard(viewModel) }
        item(key = "backup") { BackupCard(viewModel) }
        if (removed.isNotEmpty()) item(key = "removed") { RecentlyRemovedCard(removed, viewModel::restore) }
        item(key = "about") { AboutCard() }
    }
}

@Composable
private fun ThemeCard(current: String, onPick: (String) -> Unit) {
    SectionCard("Theme") {
        val choices = listOf(Settings.THEME_SYSTEM) + Themes.entries.map { it.key }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { key -> ThemeTile(key, key == current, Modifier.weight(1f)) { onPick(key) } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LayoutCard(current: ListLayout, onPick: (ListLayout) -> Unit) {
    SectionCard(
        "Medication List",
        info = "How Today shows your medications. Grouped and Compact put each category in one card, and Compact " +
            "fits more on screen by moving the amount to a long press on Take. When Due sorts them by what needs you first.",
    ) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListLayout.entries.forEach { layout ->
                SlateChip(layout == current, { onPick(layout) }, role = Role.RadioButton) { ChipText(layout.label) }
            }
        }
    }
}

/** A tile drawn in the theme it stands for, so it previews itself. */
@Composable
private fun ThemeTile(key: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val system = key == Settings.THEME_SYSTEM
    val label = if (system) "Match System" else Themes.fromKey(key)?.label ?: key
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    if (selected) BorderStroke(2.dp, Colors.Accent) else BorderStroke(1.dp, Colors.LineSoft),
                    RoundedCornerShape(12.dp),
                ),
        ) {
            if (system) {
                Swatch(Themes.SLATE.palette, Modifier.weight(1f))
                Swatch(Themes.DAYLIGHT.palette, Modifier.weight(1f))
            } else {
                Swatch(Themes.fromKey(key)?.palette ?: Themes.DEFAULT.palette, Modifier.weight(1f))
            }
        }
        // Inset from the tile's rounded corners, which would otherwise clip the name's first and last letters.
        Row(
            Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(Glyphs.Check, contentDescription = null, tint = Colors.Accent, modifier = Modifier.size(textIcon(16.dp)))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Colors.Ink else Colors.Muted,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/** A miniature card in [p]'s colours: a title line, a text line and the accent. */
@Composable
private fun Swatch(p: Palette, modifier: Modifier) {
    Box(modifier.fillMaxHeight().background(p.ground).padding(8.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(p.surface, RoundedCornerShape(6.dp))
                .border(1.dp, p.lineSoft, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(width = 34.dp, height = 5.dp).background(p.ink, CircleShape))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(width = 22.dp, height = 4.dp).background(p.muted, CircleShape))
                Box(Modifier.size(width = 18.dp, height = 8.dp).background(p.accent, CircleShape))
            }
        }
    }
}

@Composable
private fun SoundsCard(viewModel: SettingsViewModel) {
    val player = LocalSoundPlayer.current
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    var pickingFor by rememberSaveable { mutableStateOf<SoundEvent?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val event = pickingFor
        pickingFor = null
        if (uri != null && event != null) viewModel.addSound(event, uri) { player.play(event, it) }
    }
    val pick = { event: SoundEvent ->
        pickingFor = event
        picker.launch(SoundLibrary.PICKER_TYPES)
    }

    SectionCard(
        "Sounds",
        info = "Your own sound can be any file this phone plays, such as MP3, AAC, FLAC, Ogg or WAV, up to 20 MB. " +
            "Short clips work best.",
    ) {
        SoundEvent.entries.forEachIndexed { i, event ->
            if (i > 0) HorizontalDivider(color = Colors.LineSoft)
            val setting by viewModel.sound(event).collectAsStateWithLifecycle()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.label, style = MaterialTheme.typography.bodyLarge, color = Colors.Ink, modifier = Modifier.weight(1f))
                    SlateButton(
                        onClick = { player.play(event, setting) },
                        enabled = setting.effective != SoundMode.NONE,
                        modifier = Modifier.semantics { contentDescription = "Play, ${event.label.lowercase()}" },
                    ) {
                        Icon(Glyphs.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Play", style = MaterialTheme.typography.labelLarge)
                    }
                }
                // Both events offer Built-In and None, so each chip says which sound it's for.
                val forEvent = { choice: String -> Modifier.semantics { contentDescription = "$choice, ${event.label.lowercase()}" } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val mode = setting.effective
                    SlateChip(mode == SoundMode.BUILT_IN, { viewModel.setMode(event, SoundMode.BUILT_IN) }, forEvent("Built-In")) { ChipText("Built-In") }
                    SlateChip(mode == SoundMode.NONE, { viewModel.setMode(event, SoundMode.NONE) }, forEvent("None")) { ChipText("None") }
                    val custom = setting.custom
                    if (custom != null) {
                        SlateChip(mode == SoundMode.CUSTOM, { viewModel.setMode(event, SoundMode.CUSTOM) }) {
                            Icon(Glyphs.File, contentDescription = null, modifier = Modifier.size(textIcon(16.dp)))
                            ChipText(custom.name, Modifier.widthIn(max = 180.dp))
                        }
                    }
                    SlateChip(false, { pick(event) }, role = Role.Button) {
                        Icon(Glyphs.Plus, contentDescription = null, modifier = Modifier.size(textIcon(16.dp)))
                        ChipText(if (adding == event) "Adding…" else if (custom == null) "Choose a File" else "Another File")
                    }
                }
                if (setting.effective != SoundMode.NONE) {
                    VolumeSlider(event, setting.volume) { viewModel.setVolume(event, it); player.play(event, setting.copy(volume = it)) }
                }
                problems[event]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Colors.Bad) }
            }
        }
    }
}

/** Plays the sound at the new level once the thumb is let go, rather than on every step of a drag */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider(event: SoundEvent, volume: Float, onSet: (Float) -> Unit) {
    var dragging by remember(volume) { mutableFloatStateOf(volume) }
    val percent = "${(dragging * 100).roundToInt()}%"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Volume", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onSet(dragging) },
            // Material's thick track and upright thumb are heavier than anything else on Slate
            thumb = { Box(Modifier.size(20.dp).background(Colors.Accent, CircleShape)) },
            track = { state ->
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Colors.Field)) {
                    Box(Modifier.fillMaxWidth(state.value).fillMaxHeight().background(Colors.Accent))
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = "Volume, ${event.label.lowercase()}" },
        )
        // Wide enough for "100%", so the slider doesn't shift as the number grows
        Text(percent, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Ink, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun BackupCard(viewModel: SettingsViewModel) {
    val status by viewModel.backupStatus.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val age by viewModel.backupAge.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::backUp)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::open)
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val idle = working == null

    SectionCard(
        "Backup",
        hint = "Keep a backup somewhere off this phone.",
        info = "A backup is one file with everything, photos and sounds included. Restore replaces what's on this phone " +
            "with it. Export CSV saves your doses for a spreadsheet.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlateButton(
                onClick = { save.launch(BackupText.backupName(LocalDate.now())) },
                kind = ButtonKind.Primary,
                enabled = idle,
                modifier = Modifier.weight(1f),
            ) { Text("Back Up", style = MaterialTheme.typography.labelLarge) }
            SlateButton(
                onClick = { open.launch(RESTORE_TYPES) },
                enabled = idle,
                modifier = Modifier.weight(1f),
            ) { Text("Restore", style = MaterialTheme.typography.labelLarge) }
        }
        SlateButton(
            onClick = { csv.launch(BackupText.csvName(LocalDate.now())) },
            enabled = idle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Glyphs.File, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Export CSV", style = MaterialTheme.typography.labelLarge)
        }
        // What's happening, or what just happened; otherwise how old the last backup is.
        val line = working ?: status?.text ?: age?.text
        if (line != null) {
            val color = when {
                working != null -> Colors.Muted
                status != null -> if (status?.failed == true) Colors.Bad else Colors.Muted
                age?.warn == true -> Colors.Warn
                else -> Colors.Muted
            }
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                color = color,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    pending?.let { p ->
        val q = viewModel.question(p)
        ConfirmDialog(
            title = q.title,
            text = q.text,
            confirmLabel = q.confirm,
            dismissLabel = "Cancel",
            onConfirm = viewModel::restore,
            onDismiss = viewModel::cancelRestore,
            confirmKind = if (q.destructive) ButtonKind.Delete else ButtonKind.Primary,
        )
    }
}

/** What the restore picker offers; a backup saved by another app may not say it's a zip. */
private val RESTORE_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream", "text/plain")

@Composable
private fun ChipText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Removed medications, until they're deleted for good; each can come back as it was. */
@Composable
private fun RecentlyRemovedCard(removed: List<RemovedItem>, onRestore: (Long) -> Unit) {
    SectionCard(
        "Recently Removed",
        info = "A removed medication waits here for 30 days with its doses and reminders, then is deleted for good. " +
            "Restore brings it back as it was.",
    ) {
        Column {
            removed.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = Colors.LineSoft)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Colors.Ink)
                        Text(item.status, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted)
                    }
                    SlateButton(onClick = { onRestore(item.id) }, modifier = Modifier.semantics { contentDescription = "Restore ${item.name}" }) {
                        Text("Restore")
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    SectionCard("About") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Panacea ${version.orEmpty()}".trim(), style = MaterialTheme.typography.bodyLarge, color = Colors.Ink)
            Text(
                "Everything stays on this phone: no account, no internet, no tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.Muted,
            )
        }
    }
}
