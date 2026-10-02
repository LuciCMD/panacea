package com.clementine.panacea.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.clementine.panacea.ui.edit.EditMedicationScreen
import com.clementine.panacea.ui.history.HistoryScreen
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.reminders.RemindersScreen
import com.clementine.panacea.ui.settings.SettingsScreen
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.today.TodayScreen

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Glyphs.Today),
    REMINDERS("Reminders", Glyphs.Bell),
    HISTORY("History", Glyphs.History),
    SETTINGS("Settings", Glyphs.Sliders),
}

/** A place in the app; the back stack is a list of these, saved as their [id]s. */
sealed interface Route {
    val id: String

    data class Top(val tab: Tab) : Route {
        override val id get() = "tab:${tab.name}"
    }

    /** Add a medication (null) or edit one. */
    data class EditMedication(val medicationId: Long?) : Route {
        override val id get() = "edit:${medicationId ?: "new"}"
    }

    companion object {
        fun fromId(id: String): Route? = when {
            id.startsWith("tab:") -> Tab.entries.firstOrNull { it.name == id.removePrefix("tab:") }?.let(::Top)
            id.startsWith("edit:") -> EditMedication(id.removePrefix("edit:").toLongOrNull())
            else -> null
        }
    }
}

private val start: Route = Route.Top(Tab.TODAY)

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.id } },
    restore = { ids -> ids.mapNotNull(Route::fromId).ifEmpty { listOf(start) }.toMutableStateList() },
)

// Motion stays a short fade, as everywhere else.
private fun fade(): ContentTransform = fadeIn(tween(120)) togetherWith fadeOut(tween(120))

@Composable
fun PanaceaShell() {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableListOf(start).toMutableStateList() }
    val tab = (backStack.last() as? Route.Top)?.tab
    val pop = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f),
            onBack = { pop() },
            transitionSpec = { fade() },
            popTransitionSpec = { fade() },
            predictivePopTransitionSpec = { fade() },
            entryProvider = { route ->
                NavEntry(route, contentKey = route.id) {
                    when (route) {
                        is Route.Top -> when (route.tab) {
                            Tab.TODAY -> TodayScreen(
                                onAdd = { backStack.add(Route.EditMedication(null)) },
                                onOpen = { backStack.add(Route.EditMedication(it)) },
                            )
                            Tab.REMINDERS -> RemindersScreen()
                            Tab.HISTORY -> HistoryScreen()
                            Tab.SETTINGS -> SettingsScreen()
                        }
                        is Route.EditMedication -> EditMedicationScreen(route.medicationId, onDone = { pop() })
                    }
                }
            },
        )
        if (tab != null) {
            BottomBar(tab) { picked ->
                // Every tab sits on top of Today, so Back from any of them comes home first.
                backStack.clear()
                backStack.add(start)
                if (picked != Tab.TODAY) backStack.add(Route.Top(picked))
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onPick: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Colors.Ground)) {
        HorizontalDivider(color = Colors.LineSoft)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, onClick = { onPick(t) }, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier
                            .size(width = 60.dp, height = 32.dp)
                            .background(if (selected) Colors.Field else Color.Transparent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (selected) Colors.Accent else Colors.Muted)
                    }
                    Text(
                        t.label,
                        fontSize = 12.sp,
                        color = if (selected) Colors.Ink else Colors.Muted,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
