package com.clementine.panacea.ui.rearrange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import com.clementine.panacea.model.Category
import com.clementine.panacea.ui.components.SlateIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clementine.panacea.ui.components.SlateCard
import com.clementine.panacea.ui.components.screenPadding
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.icons.TypeIcons
import com.clementine.panacea.ui.theme.Colors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The medications in Today's order; drag one by its handle, or long-press it, to move it. Saved on drop. */
@Composable
fun RearrangeScreen(onBack: () -> Unit, viewModel: RearrangeViewModel = viewModel(factory = RearrangeViewModel.Factory)) {
    val saved by viewModel.items.collectAsStateWithLifecycle()
    val items = remember { mutableStateListOf<RearrangeItem>() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val drag = remember { DragState(list, items, scope) }

    // The database's order, except while a drag is under way.
    LaunchedEffect(saved, drag.key) {
        val fresh = saved ?: return@LaunchedEffect
        if (drag.key == null && fresh != items.toList()) {
            items.clear()
            items.addAll(fresh)
        }
    }
    // Scrolls the list while a dragged medication is held near its top or bottom edge.
    LaunchedEffect(drag.key) {
        while (drag.key != null) {
            val step = drag.edgeScroll()
            if (step != 0f) drag.scrolled(list.scrollBy(step))
            withFrameNanos { }
        }
    }

    val save = { viewModel.save(items.map { it.id }) }
    val start = { id: Long ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        drag.start(id)
    }
    val end = {
        if (drag.key != null) {
            drag.end()
            save()
        }
    }
    fun move(from: Int, to: Int): Boolean {
        if (to !in items.indices) return false
        val next = moved(items.toList(), from, to)
        items.clear()
        items.addAll(next)
        save()
        return true
    }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().background(Colors.Ground),
        // No bottom bar here, so clear the navigation bar itself.
        contentPadding = screenPadding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") { Header(onBack) }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val dragging = item.id == drag.key
            Row(
                (if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = drag.offset } else Modifier.animateItem())
                    .fillMaxWidth()
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start(item.id) },
                            onDrag = { change, amount -> change.consume(); drag.by(amount.y) },
                            onDragEnd = end,
                            onDragCancel = end,
                        )
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${item.name}, ${index + 1} of ${items.size}"
                        customActions = listOfNotNull(
                            CustomAccessibilityAction("Move Up") { move(index, index - 1) }.takeIf { index > 0 },
                            CustomAccessibilityAction("Move Down") { move(index, index + 1) }.takeIf { index < items.lastIndex },
                        )
                    },
            ) {
                ItemCard(item, dragging, Modifier.weight(1f))
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(width = 48.dp, height = 64.dp)
                        .pointerInput(item.id) {
                            detectDragGestures(
                                onDragStart = { start(item.id) },
                                onDrag = { change, amount -> change.consume(); drag.by(amount.y) },
                                onDragEnd = end,
                                onDragCancel = end,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Glyphs.Grip, contentDescription = null, tint = if (dragging) Colors.Accent else Colors.Muted)
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        SlateIconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Glyphs.Back, contentDescription = "Back", tint = Colors.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Rearrange",
                style = MaterialTheme.typography.headlineMedium,
                color = Colors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text("Drag a medication by its handle to change its place on Today.", style = MaterialTheme.typography.bodyMedium, color = Colors.Muted)
        }
    }
}

@Composable
private fun ItemCard(item: RearrangeItem, lifted: Boolean, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    SlateCard(
        modifier
            .heightIn(min = 64.dp)
            .then(if (lifted) Modifier.border(1.5.dp, Colors.Accent, shape) else Modifier),
    ) {
        Row(
            Modifier.background(if (lifted) Colors.Raised else Colors.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Colors.Field), contentAlignment = Alignment.Center) {
                if (item.photo != null) {
                    PillPhoto(item.photo, null, Modifier.size(40.dp).clip(CircleShape), px = 160)
                } else {
                    Icon(TypeIcons.of(item.type), contentDescription = null, tint = Colors.Ink, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = Colors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val kind = listOfNotNull(item.category.label.takeIf { item.category != Category.UNCATEGORIZED }, item.type.label).joinToString(" · ")
                Text(kind, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted, maxLines = 1)
            }
        }
    }
}

/**
 * The medication being dragged: which one, and how far it has been pulled from where the list lays it
 * out. Passing the middle of a neighbour swaps the two, and the offset is corrected so it stays under
 * the finger.
 */
private class DragState(
    private val list: LazyListState,
    private val items: SnapshotStateList<RearrangeItem>,
    private val scope: CoroutineScope,
) {
    var key by mutableStateOf<Long?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(id: Long) {
        key = id
        offset = 0f
    }

    fun end() {
        key = null
        offset = 0f
    }

    fun by(dy: Float) {
        offset += dy
        val info = list.layoutInfo.visibleItemsInfo
        val current = info.firstOrNull { it.key == key } ?: return
        val middle = current.offset + offset + current.size / 2f
        val target = info.firstOrNull { it.key is Long && it.key != key && middle >= it.offset && middle <= it.offset + it.size } ?: return
        val from = items.indexOfFirst { it.id == key }
        val to = items.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        // Moving the list's first visible item makes the list follow it; hold the scroll where it was.
        val first = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        items.add(to, items.removeAt(from))
        offset += current.offset - target.offset
        if (current.index == first || target.index == first) scope.launch { list.scrollToItem(first, firstOffset) }
    }

    /** How far to scroll this frame: towards an edge the dragged medication is held near, faster the closer. */
    fun edgeScroll(): Float {
        val info = list.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val top = current.offset + offset
        val bottom = top + current.size
        val zone = current.size * 0.75f
        return when {
            top < info.viewportStartOffset + zone && list.canScrollBackward -> -MAX_STEP * (1 - (top - info.viewportStartOffset) / zone).coerceIn(0f, 1f)
            bottom > info.viewportEndOffset - zone && list.canScrollForward -> MAX_STEP * (1 - (info.viewportEndOffset - bottom) / zone).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** The list scrolled by [consumed] under a still finger: keep the medication there, and swap if it passed one. */
    fun scrolled(consumed: Float) {
        if (consumed != 0f) by(consumed)
    }

    private companion object {
        const val MAX_STEP = 18f
    }
}
