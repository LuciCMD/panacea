package com.clementine.panacea.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.counted
import com.clementine.panacea.ui.theme.Colors

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    // An id, so the sheet shows the dose as it is after a change.
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }

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
                Text("Doses you log show up here.", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
            }
        }
        items(list.orEmpty(), key = { it.doses.first().id }) { day ->
            DayCard(day, showMedication = true) { opened = it.id }
        }
    }

    opened?.let { id -> days?.firstNotNullOfOrNull { day -> day.doses.firstOrNull { it.id == id } } }?.let { item ->
        DoseSheet(
            item,
            onChangeTime = { viewModel.changeTime(item.id, it) },
            onChangeAmount = { viewModel.changeAmount(item.id, it) },
            onRemove = { viewModel.remove(item.id) },
            onDismiss = { opened = null },
        )
    }
}
