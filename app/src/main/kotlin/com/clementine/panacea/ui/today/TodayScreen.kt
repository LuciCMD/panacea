package com.clementine.panacea.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.model.MedicationType
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Slate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val now = remember { ZonedDateTime.now() }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Slate.Ground),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = insets.calculateTopPadding() + 22.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Today", style = MaterialTheme.typography.headlineMedium, color = Slate.Ink)
                Text(
                    now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate.Muted,
                )
            }
        }
        val list = medications
        if (list != null && list.isEmpty()) {
            item {
                Text("Add a medication to start.", style = MaterialTheme.typography.bodyMedium, color = Slate.Faint)
            }
        }
        items(list.orEmpty(), key = { it.medication.id }) { summary ->
            MedicationCard(summary, now)
        }
    }
}

@Composable
private fun MedicationCard(summary: MedicationSummary, now: ZonedDateTime) {
    val med = summary.medication
    val time = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val date = remember { DateTimeFormatter.ofPattern("d MMM") }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Slate.Surface,
        border = BorderStroke(1.dp, Slate.LineSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(Slate.Raised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TypeIcons.of(MedicationType.fromKey(med.type)), contentDescription = null, tint = Slate.Ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(med.name, style = MaterialTheme.typography.titleMedium, color = Slate.Ink)
                Text(TodayText.doseLine(med), style = MaterialTheme.typography.bodyMedium, color = Slate.Muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    TodayText.lastTaken(summary.lastTakenAt, now, time, date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate.Muted,
                )
            }
        }
    }
}
