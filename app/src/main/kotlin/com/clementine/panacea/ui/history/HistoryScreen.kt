package com.clementine.panacea.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.ui.components.EmptyCard
import com.clementine.panacea.ui.components.Loader
import com.clementine.panacea.ui.components.ScreenHeader
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.counted
import com.clementine.panacea.ui.icons.Glyphs

@Composable
fun HistoryScreen(onGoToday: () -> Unit, viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val list = days ?: return Loader()
    // An id, so the sheet shows the dose as it is after a change.
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val count = list.sumOf { it.doses.size }
        item(key = "header") {
            ScreenHeader("History", if (count == 0) null else "${counted(count, "dose")} logged")
        }
        if (list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    "Nothing Logged Yet",
                    "Each dose you log on Today shows up here, by day. Tap one to change its time or amount.",
                    "Go to Today",
                    Glyphs.Today,
                    onGoToday,
                )
            }
        }
        items(list, key = { it.doses.first().id }) { day ->
            DayCard(day, showMedication = true) { opened = it.id }
        }
    }

    opened?.let { id -> list.firstNotNullOfOrNull { day -> day.doses.firstOrNull { it.id == id } } }?.let { item ->
        DoseSheet(
            item,
            onChangeTime = { viewModel.changeTime(item.id, it) },
            onChangeAmount = { viewModel.changeAmount(item.id, it) },
            onRemove = { viewModel.remove(item.id) },
            onDismiss = { opened = null },
        )
    }
}
