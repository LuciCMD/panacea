package com.clementine.panacea.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clementine.panacea.ui.theme.Colors

/** Tabular figures, so times and amounts don't shift as they change. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * Padding for a tab's scrolling list: clear of the status bar on top. The bottom bar below the
 * content already keeps clear of the navigation bar.
 */
@Composable
fun screenPadding(bottom: Dp = 24.dp): PaddingValues {
    val top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    return PaddingValues(start = 20.dp, end = 20.dp, top = top + 22.dp, bottom = bottom)
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    /** How to read the page, behind an ⓘ after the title. */
    info: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // No gap: the ⓘ's 48dp target already leaves room around its icon.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Colors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                info?.let { InfoTip(about = title, text = it) }
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Muted) }
        }
        action?.invoke()
    }
}

/**
 * A card with a heading. What it's for goes behind an [info] ⓘ after the title; a visible [hint] is
 * for the few things that must be seen, such as a [warning], whose card also has a warn edge.
 */
@Composable
fun SectionCard(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SlateCard(modifier.fillMaxWidth(), border = if (warning) Colors.Warn else null) {
        // The ⓘ's 48dp row is taller than the title, so the card's top padding gives way to keep the title in place.
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = if (info != null) 4.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Colors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    info?.let { InfoTip(about = title, text = it) }
                }
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted) }
            }
            content()
        }
    }
}

/** An empty list: what will show up here, and the one thing to do about it. */
@Composable
fun EmptyCard(title: String, text: String, action: String, icon: ImageVector, onAction: () -> Unit) {
    SlateCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Colors.Ink, modifier = Modifier.semantics { heading() })
                Text(text, style = MaterialTheme.typography.bodyLarge, color = Colors.Muted)
            }
            SlateButton(onClick = onAction, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(textIcon(20.dp)))
                Text(action)
            }
        }
    }
}

@Composable
fun SlateSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Colors.Accent,
            checkedTrackColor = Colors.AccentDim,
            checkedBorderColor = Colors.AccentDim,
            uncheckedThumbColor = Colors.Muted,
            uncheckedTrackColor = Colors.Field,
            uncheckedBorderColor = Colors.Line,
        ),
    )
}
