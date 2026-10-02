package com.clementine.panacea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors

/** A field label: Title Case, above the field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Colors.Ink, modifier = modifier)
}

/**
 * Slate's filled field: field fill, radius 8, no outline, and a 2 px accent underline while focused
 * (bad colour when [error] is set). The error is written under it and read out with the field.
 */
@Composable
fun SlateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    accessibleLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val underline = when {
        error != null -> Colors.Bad
        focused -> Colors.Accent
        else -> null
    }
    val selection = TextSelectionColors(handleColor = Colors.Accent, backgroundColor = Colors.AccentDim.copy(alpha = 0.5f))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompositionLocalProvider(LocalTextSelectionColors provides selection) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                cursorBrush = SolidColor(Colors.Accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Colors.Ink).merge(Numbers),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        accessibleLabel?.let { contentDescription = it }
                        if (error != null) error(error)
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(Colors.Field, MaterialTheme.shapes.small)
                            .drawBehind {
                                underline?.let {
                                    val y = size.height - 1.dp.toPx()
                                    val inset = 6.dp.toPx()
                                    drawLine(it, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 2.dp.toPx())
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Colors.Faint)
                        }
                        inner()
                    }
                },
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Colors.Bad) }
    }
}

/** A field-styled button that opens a list to pick from, restyled to the theme. */
@Composable
fun <T> SlateDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val accent = Colors.Accent
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.small,
            color = Colors.Field,
            contentColor = Colors.Ink,
            modifier = Modifier.heightIn(min = 48.dp).semantics { this.contentDescription = "$contentDescription, ${label(selected)}" },
        ) {
            Row(
                Modifier
                    .height(48.dp)
                    .drawBehind {
                        if (open) {
                            val y = size.height - 1.dp.toPx()
                            drawLine(accent, Offset(6.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 2.dp.toPx())
                        }
                    }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label(selected), style = MaterialTheme.typography.bodyLarge)
                Icon(Glyphs.ChevronDown, contentDescription = null, tint = if (open) Colors.Accent else Colors.Muted, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Colors.Raised,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, Colors.LineSoft),
            shadowElevation = 8.dp,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontSize = 15.sp,
                            color = if (isSelected) Colors.Accent else Colors.Ink,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    modifier = if (isSelected) Modifier.background(Colors.AccentWash) else Modifier,
                )
            }
        }
    }
}
