package com.clementine.panacea.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors

/**
 * A small ⓘ after a label. A tap shows [text] at once beside the icon, never over it, until a tap
 * anywhere else or Back. The icon is faint, and accent while its text is open.
 */
@Composable
fun InfoTip(about: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            // Small on screen, growing with the text; Compose widens the touch area to 48dp around it.
            .size(textIcon(28.dp))
            .clip(CircleShape)
            .clickable(onClickLabel = "Explain") { open = !open }
            .semantics { contentDescription = "About $about" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.Info, contentDescription = null, tint = if (open) Colors.Accent else Colors.Faint, modifier = Modifier.size(textIcon(18.dp)))
        if (open) {
            val density = LocalDensity.current
            val beside = remember(density) { with(density) { Beside(gap = 12.dp.roundToPx(), margin = 16.dp.roundToPx()) } }
            Popup(popupPositionProvider = beside, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Colors.Surface,
                    contentColor = Colors.Ink,
                    border = BorderStroke(1.dp, Colors.Line),
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** Beside the anchor on whichever side has room; on a narrow screen, below it (or above, near the bottom). */
private class Beside(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val right = anchorBounds.right + gap
        val left = anchorBounds.left - gap - w
        val middle = (anchorBounds.top + anchorBounds.height / 2 - h / 2).coerceIn(margin, maxOf(margin, windowSize.height - h - margin))
        return when {
            right + w <= windowSize.width - margin -> IntOffset(right, middle)
            left >= margin -> IntOffset(left, middle)
            else -> {
                val x = (anchorBounds.center.x - w / 2).coerceIn(margin, maxOf(margin, windowSize.width - w - margin))
                val below = anchorBounds.bottom + gap
                IntOffset(x, if (below + h <= windowSize.height - margin) below else anchorBounds.top - gap - h)
            }
        }
    }
}
