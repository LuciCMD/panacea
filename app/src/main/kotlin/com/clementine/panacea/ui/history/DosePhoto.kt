package com.clementine.panacea.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clementine.panacea.ui.components.SlateIconButton
import com.clementine.panacea.ui.edit.PillPhoto
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Overlay

// An instant print is paper whatever the theme, and a film camera's stamp is always this orange
private val Paper = Color(0xFFF4F1EA)
private val PaperInk = Color(0xFF3A3631)
private val Stamp = Color(0xFFFFA53A)
private val StampGlow = Color(0xB3FF4D00)

/**
 * A dose's photo as an instant print: the shot's time stamped in its corner like a film camera's
 * date, and [caption] written in the wide margin below. Sized by [width]; everything scales with it
 */
@Composable
fun Polaroid(photo: String, stamp: String, caption: String, width: Dp, modifier: Modifier = Modifier, tilt: Float = -1.5f, onClick: (() -> Unit)? = null) {
    val density = LocalDensity.current
    val margin = width * 0.06f
    fun size(share: Float) = with(density) { (width * share).toSp() }
    Column(
        modifier
            .width(width)
            .graphicsLayer { rotationZ = tilt }
            .shadow(6.dp, RoundedCornerShape(2.dp))
            .background(Paper, RoundedCornerShape(2.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Look closer", onClick = onClick) else Modifier)
            .semantics { contentDescription = "Photo of the dose, taken $stamp" }
            .padding(start = margin, end = margin, top = margin),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            PillPhoto(photo, null, Modifier.fillMaxSize(), px = with(density) { width.roundToPx() })
            Text(
                stamp,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = size(0.055f),
                    letterSpacing = 0.5.sp,
                    color = Stamp,
                    shadow = Shadow(StampGlow, Offset.Zero, blurRadius = 8f),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = margin * 0.8f, bottom = margin * 0.6f),
            )
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1 / 0.2f), contentAlignment = Alignment.CenterStart) {
            Text(
                caption,
                style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontWeight = FontWeight.Medium, fontSize = size(0.062f), color = PaperInk)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = margin * 0.5f),
            )
        }
    }
}

/** The print up close, as big as the screen allows */
@Composable
fun PolaroidViewer(photo: String, stamp: String, caption: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Overlay.Backdrop)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clickable(onClickLabel = "Close", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            // Width for the print, its tall margin included
            val width = minOf(maxWidth - 48.dp, (maxHeight - 96.dp) / 1.18f, 520.dp)
            Polaroid(photo, stamp, caption, width, tilt = 0f)
            SlateIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Glyphs.Close, contentDescription = "Close", tint = Overlay.Ink)
            }
        }
    }
}
