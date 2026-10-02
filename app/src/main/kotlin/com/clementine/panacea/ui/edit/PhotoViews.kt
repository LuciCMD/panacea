package com.clementine.panacea.ui.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clementine.panacea.data.PhotoStore
import com.clementine.panacea.ui.components.SlateChip
import com.clementine.panacea.ui.icons.Glyphs

val LocalPhotoStore = staticCompositionLocalOf<PhotoStore> { error("No PhotoStore provided") }

enum class PillSide(val label: String, val title: String) {
    FRONT("Front", "Front of the Pill"),
    BACK("Back", "Back of the Pill"),
}

/** A stored pill photo, decoded off the main thread at about [px] pixels. */
@Composable
fun rememberPhoto(name: String?, px: Int): ImageBitmap? {
    val store = LocalPhotoStore.current
    val bitmap by produceState<ImageBitmap?>(null, name, px) {
        value = name?.let { store.load(it, px)?.asImageBitmap() }
    }
    return bitmap
}

@Composable
fun PillPhoto(name: String?, contentDescription: String?, modifier: Modifier = Modifier, px: Int = 256, scale: ContentScale = ContentScale.Crop) {
    val bitmap = rememberPhoto(name, px)
    if (bitmap != null) Image(bitmap, contentDescription, modifier, contentScale = scale) else Box(modifier)
}

/** Both sides of the pill, full screen, to compare with the one in hand. */
@Composable
fun PhotoViewer(name: String, front: String?, back: String?, start: PillSide, onClose: () -> Unit) {
    var side by remember { mutableStateOf(if (start == PillSide.BACK && back != null) PillSide.BACK else PillSide.FRONT) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            PillPhoto(
                if (side == PillSide.FRONT) front else back,
                "$name, ${side.label.lowercase()}",
                Modifier.fillMaxWidth().aspectRatio(1f).align(Alignment.Center),
                px = 1280,
                scale = ContentScale.Fit,
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = onClose) { Icon(Glyphs.Close, contentDescription = "Close", tint = Color.White) }
            }
            if (front != null && back != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillSide.entries.forEach { s ->
                        SlateChip(selected = side == s, onClick = { side = s }) { Text(s.label, color = if (side == s) Color.Unspecified else Color.White) }
                    }
                }
            }
        }
    }
}
