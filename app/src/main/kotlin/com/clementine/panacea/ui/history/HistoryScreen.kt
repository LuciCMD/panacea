package com.clementine.panacea.ui.history

import com.clementine.panacea.ui.counted
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.sound.LocalSoundPlayer
import com.clementine.panacea.sound.SoundEvent
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.ConfirmDialog
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Colors

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val sounds = LocalSoundPlayer.current
    var removing by remember { mutableStateOf<HistoryItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val list = days
        val count = list?.sumOf { it.doses.size } ?: 0
        item(key = "header") {
            ScreenHeader("History", if (count == 0) null else "${counted(count, "dose")} logged")
        }
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                Text("Doses you log show up here.", style = MaterialTheme.typography.bodyMedium, color = Colors.Faint)
            }
        }
        items(list.orEmpty(), key = { it.doses.first().id }) { day ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    day.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Colors.Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                SlateCard(Modifier.fillMaxWidth()) {
                    Column {
                        day.doses.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = Colors.LineSoft, modifier = Modifier.padding(start = 66.dp))
                            DoseRow(item) { removing = item }
                        }
                    }
                }
            }
        }
    }

    removing?.let { item ->
        ConfirmDialog(
            title = "Remove This Dose?",
            text = item.removeQuestion,
            confirmLabel = "Remove",
            dismissLabel = "Keep It",
            confirmKind = ButtonKind.Delete,
            onConfirm = {
                viewModel.remove(item)
                sounds.play(SoundEvent.UNDO)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun DoseRow(item: HistoryItem, onRemove: () -> Unit) {
    val body = MaterialTheme.typography.bodyMedium.merge(Numbers)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Remove this dose", onClick = onRemove)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(Colors.Raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(TypeIcons.of(item.type), contentDescription = null, tint = Colors.Ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Colors.Ink)
            Text(item.amount, style = body, color = Colors.Muted)
        }
        Text(item.time, style = body, color = Colors.Muted)
    }
}
