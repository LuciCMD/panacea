package com.clementine.panacea.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.clementine.panacea.ui.theme.SlateIndication
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clementine.panacea.ui.theme.Colors

/** Tonal is the accent, quietly: a lavender tint with lavender text, for an action that isn't due. */
enum class ButtonKind { Plain, Primary, Tonal, Delete, Text }

/**
 * Slate button: flat, radius 8, no border. Pressed and hovered it takes the white wash, focused from
 * a keyboard an accent ring, and disabled it fades to 40%. Delete is only for the confirm step of a removal.
 */
@Composable
fun SlateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Plain,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val (container, text) = when (kind) {
        ButtonKind.Plain -> Colors.Field to Colors.Ink
        ButtonKind.Primary -> Colors.Accent to Colors.OnAccent
        ButtonKind.Tonal -> Colors.AccentWash to Colors.Accent
        ButtonKind.Delete -> Colors.Bad to Colors.OnAccent
        ButtonKind.Text -> Color.Transparent to Colors.Accent
    }
    val s = shape ?: MaterialTheme.shapes.small
    CompositionLocalProvider(LocalContentColor provides text, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
        Row(
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(s)
                .background(container)
                .clickable(
                    interactionSource = null,
                    indication = SlateIndication(s),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A round 48 dp icon button with Slate's washes and focus ring; give its icon a content description. */
@Composable
fun SlateIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Colors.Ink) {
        Box(
            modifier
                .size(48.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(CircleShape)
                .clickable(interactionSource = null, indication = SlateIndication(CircleShape), enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** How much of a disabled control shows, in place of a separate grey look. */
const val DISABLED_ALPHA = 0.4f

/** A pill that can be picked; the picked one is washed in the accent. */
@Composable
fun SlateChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    CompositionLocalProvider(LocalContentColor provides if (selected) Colors.Accent else Colors.Muted) {
        Row(
            modifier
                .minimumInteractiveComponentSize()
                .clip(shape)
                .background(if (selected) Colors.AccentWash else Color.Transparent)
                .then(if (selected) Modifier else Modifier.border(1.dp, Colors.LineSoft, shape))
                .selectable(selected = selected, interactionSource = null, indication = SlateIndication(shape), onClick = onClick)
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A flat card: surface fill, 1 px soft line (or [border], such as warn), radius 12. */
@Composable
fun SlateCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Colors.Surface,
        contentColor = Colors.Ink,
        border = BorderStroke(1.dp, border ?: Colors.LineSoft),
        content = content,
    )
}

/** A banner with an urgency stripe, a message and one action, announced to screen readers. */
@Composable
fun SlateToast(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    stripe: Color = Colors.Good,
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(10.dp),
        color = Colors.Surface,
        contentColor = Colors.Ink,
        border = BorderStroke(1.dp, Colors.Line),
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
            SlateButton(onClick = onAction, kind = ButtonKind.Text, modifier = Modifier.padding(end = 6.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A question that needs an answer before anything happens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmKind: ButtonKind = ButtonKind.Primary,
    /** Back or a tap outside, when that isn't the same as the dismiss button. */
    onCancel: () -> Unit = onDismiss,
) {
    BasicAlertDialog(onDismissRequest = onCancel, modifier = Modifier.semantics { paneTitle = title }) {
        SlateCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Colors.Ink)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = Colors.Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SlateButton(onClick = onDismiss) { Text(dismissLabel) }
                    SlateButton(onClick = onConfirm, kind = confirmKind) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** A thin ring filling clockwise from the top, around [content]. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val track = Colors.Field
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2 + 1.5.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}
