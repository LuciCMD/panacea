package com.clementine.panacea.ui.components

import android.view.ViewParent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.theme.Overlay

/**
 * Slate's bottom sheet: surface with 22dp top corners, a small line-coloured handle, and content that
 * scrolls when large text makes it taller than the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlateSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = Colors.Surface,
        contentColor = Colors.Ink,
        scrimColor = Overlay.SheetScrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Colors.Line, RoundedCornerShape(2.dp))
                    // Material's sheet gives the handle Collapse and Dismiss; the name is ours to give.
                    .semantics { contentDescription = "Drag handle" },
            )
        },
    ) {
        // The sheet has its own window, where Android would shade the navigation bar.
        val view = LocalView.current
        SideEffect {
            generateSequence(view as ViewParent?) { it.parent }
                .firstNotNullOfOrNull { (it as? DialogWindowProvider)?.window }
                ?.isNavigationBarContrastEnforced = false
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}
