package com.clementine.panacea.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors

/** Whether an entry's ⓘ box is open; kept across rotation, and each entry has its own */
@Composable
fun rememberInfoOpen(): MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }

/** A small ⓘ after a label; a tap opens or closes its [InfoBox] under the label's row */
@Composable
fun InfoTip(about: String, open: MutableState<Boolean>, modifier: Modifier = Modifier) {
    Box(
        modifier
            // A full 48dp target; the icon inside stays small and grows with the text
            .minimumInteractiveComponentSize()
            .size(textIcon(28.dp))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (open.value) "Close" else "Explain") { open.value = !open.value }
            .semantics {
                contentDescription = "About $about"
                stateDescription = if (open.value) "Open" else "Closed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.Info, contentDescription = null, tint = if (open.value) Colors.Accent else Colors.Faint, modifier = Modifier.size(textIcon(18.dp)))
    }
}

/**
 * What an ⓘ explains, in a quiet raised panel with an accent bar on its left, pushing what's below
 * it down; capped at 600dp so a line stays readable on a tablet
 */
@Composable
fun InfoBox(text: String, open: MutableState<Boolean>, modifier: Modifier = Modifier) {
    AnimatedVisibility(open.value, enter = expandVertically(tween(160)), exit = shrinkVertically(tween(120))) {
        Row(
            modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(8.dp))
                .background(Colors.Raised),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Colors.Accent))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.Muted,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
