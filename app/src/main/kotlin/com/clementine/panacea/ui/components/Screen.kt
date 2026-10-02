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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
 * A card with a heading and, if needed, a [hint] saying what it's for (faint: it can be done without).
 * A [warning] card says something that matters: its text is muted rather than faint, and its edge is warn.
 */
@Composable
fun SectionCard(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SlateCard(modifier.fillMaxWidth(), border = if (warning) Colors.Warn else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Colors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (warning) Colors.Muted else Colors.Faint) }
            }
            content()
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
