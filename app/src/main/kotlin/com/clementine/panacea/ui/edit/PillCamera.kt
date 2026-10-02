package com.clementine.panacea.ui.edit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.clementine.panacea.ui.components.SlateIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clementine.panacea.ui.components.ButtonKind
import com.clementine.panacea.ui.components.SlateButton
import com.clementine.panacea.ui.icons.Glyphs
import com.clementine.panacea.ui.theme.Colors
import com.clementine.panacea.ui.theme.Overlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**
 * Photographs the pill one side at a time, like an ID check: a square guide to fill, a look at the
 * result, then the next side. The back can be skipped, since many pills are blank there.
 */
@Composable
fun PillCamera(sides: List<PillSide>, onPhoto: (PillSide, String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = LocalPhotoStore.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var review by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted && !asked) askCamera.launch(Manifest.permission.CAMERA) }

    val side = sides[step.coerceAtMost(sides.lastIndex)]
    val next = {
        if (step < sides.lastIndex) {
            step++
            review = null
        } else {
            onClose()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            review = store.import(uri)
            problem = if (review == null) "That picture couldn't be opened." else null
            busy = false
        }
    }
    val choose = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler {
        review?.let(store::delete)
        onClose()
    }

    Box(Modifier.fillMaxSize().background(Overlay.Backdrop)) {
        val shown = review
        when {
            shown != null -> ReviewPhoto(shown)
            granted -> Viewfinder(
                onCapture = { capture, viewSize ->
                    scope.launch {
                        busy = true
                        problem = null
                        try {
                            val bitmap = withContext(Dispatchers.Default) { capture.shoot(context).upright() }
                            val crop = PhotoCrop.guide(bitmap.width, bitmap.height, viewSize.width, viewSize.height)
                            review = store.save(Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.size, crop.size))
                        } catch (e: ImageCaptureException) {
                            problem = "The photo didn't come out. Try again."
                        }
                        busy = false
                    }
                },
                onChoose = choose,
                busy = busy,
            )
            else -> NoCamera(asked, onAsk = { askCamera.launch(Manifest.permission.CAMERA) }, onChoose = choose)
        }

        // What to photograph, over the top of everything.
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlateIconButton(onClick = {
                    review?.let(store::delete)
                    onClose()
                }) { Icon(Glyphs.Close, contentDescription = "Close the camera", tint = Overlay.Ink) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(side.title, style = MaterialTheme.typography.titleLarge, color = Overlay.Ink)
                    if (sides.size > 1) {
                        Text("Step ${step + 1} of ${sides.size}", style = MaterialTheme.typography.bodyMedium, color = Overlay.Soft)
                    }
                }
                if (side == PillSide.BACK && sides.size > 1 && review == null) {
                    SlateButton(onClick = onClose, kind = ButtonKind.Text) { Text("Skip", color = Overlay.Ink) }
                }
            }
            if (review == null && granted) {
                Text(
                    "Fill the square with the pill. A plain, light surface works best.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Overlay.Soft,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Colors.Warn, modifier = Modifier.padding(16.dp)) }
        }

        if (shown != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SlateButton(onClick = {
                    store.delete(shown)
                    review = null
                }) { Text("Retake") }
                SlateButton(onClick = {
                    onPhoto(side, shown)
                    next()
                }, kind = ButtonKind.Primary) { Text(if (step < sides.lastIndex) "Use Photo, Next Side" else "Use Photo") }
            }
        }
    }
}

@Composable
private fun Viewfinder(onCapture: (ImageCapture, IntSize) -> Unit, onChoose: () -> Unit, busy: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val preview = remember { Preview.Builder().build().apply { setSurfaceProvider { request = it } } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA).firstOrNull { provider.hasCamera(it) }
        if (selector == null) {
            failed = true
            return@LaunchedEffect
        }
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, preview, capture)
            awaitCancellation()
        } catch (e: IllegalStateException) {
            failed = true
        } catch (e: IllegalArgumentException) {
            failed = true
        } finally {
            provider.unbindAll()
        }
    }
    DisposableEffect(Unit) { onDispose { camera?.cameraControl?.enableTorch(false) } }

    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        request?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        if (failed) {
            Text(
                "The camera isn't available right now.",
                style = MaterialTheme.typography.bodyLarge,
                color = Overlay.Ink,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        GuideOverlay()
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlateIconButton(onClick = onChoose, modifier = Modifier.size(56.dp)) {
                Icon(Glyphs.Image, contentDescription = "Choose from your photos", tint = Overlay.Ink)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { if (!busy && !failed) onCapture(capture, viewSize) },
                enabled = !busy && !failed,
                shape = CircleShape,
                color = Overlay.Ink,
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Overlay.Scrim, CircleShape)
                    .semantics { contentDescription = "Take the photo" },
            ) {}
            Spacer(Modifier.weight(1f))
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            SlateIconButton(
                onClick = {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                },
                enabled = hasFlash,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Glyphs.Flash,
                    contentDescription = if (torch) "Turn the light off" else "Turn the light on",
                    tint = when {
                        !hasFlash -> Color.Transparent
                        torch -> Colors.Accent
                        else -> Overlay.Ink
                    },
                )
            }
        }
    }
}

/** Darkens everything but the guide square in the middle. */
@Composable
private fun GuideOverlay() {
    val accent = Colors.Accent
    Canvas(Modifier.fillMaxSize()) {
        val side = PhotoCrop.GUIDE * minOf(size.width, size.height)
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        val corner = CornerRadius(20.dp.toPx())
        val hole = RoundRect(topLeft.x, topLeft.y, topLeft.x + side, topLeft.y + side, corner)
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRoundRect(hole)
        }
        drawPath(path, Overlay.Scrim)
        drawRoundRect(
            accent, topLeft, Size(side, side), corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun ReviewPhoto(name: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * PhotoCrop.GUIDE
        PillPhoto(name, "The photo just taken", Modifier.size(side).clip(RoundedCornerShape(20.dp)), px = 1024)
    }
}

@Composable
private fun NoCamera(asked: Boolean, onAsk: () -> Unit, onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (asked) {
            Text(
                "Panacea needs the camera to photograph your pill. You can pick a photo you already have instead.",
                style = MaterialTheme.typography.bodyLarge,
                color = Overlay.Ink,
            )
            SlateButton(onClick = onAsk) { Text("Allow the Camera") }
        }
        SlateButton(onClick = onChoose, kind = ButtonKind.Primary) {
            Icon(Glyphs.Image, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Choose From Photos")
        }
    }
}

private suspend fun ImageCapture.shoot(context: android.content.Context): ImageProxyBitmap =
    suspendCancellableCoroutine { cont ->
        takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    ImageProxyBitmap(image.toBitmap(), image.imageInfo.rotationDegrees)
                } finally {
                    image.close()
                }
                cont.resume(result)
            }

            override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
        })
    }

private class ImageProxyBitmap(val bitmap: Bitmap, val rotation: Int) {
    fun upright(): Bitmap {
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
