package com.cruisepass.verify.ui

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cruisepass.verify.R
import com.cruisepass.verify.scan.BarcodeDecoder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class FramedObject(
    val rect: Rect,
    val width: Int,
    val height: Int,
)

@Composable
fun CameraScanner(
    onCode: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val delivered = remember { AtomicBoolean(false) }
    val closed = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var framed by remember { mutableStateOf<FramedObject?>(null) }
    val detector = remember {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            boundProvider?.unbindAll()
            executor.shutdown()
            detector.close()
        }
    }

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        val provider = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            future.addListener(
                { continuation.resumeWith(runCatching { future.get() }) },
                mainExecutor,
            )
        }
        boundProvider = provider
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(selector)
            .build()
            .also { it.surfaceProvider = view.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        val decoder = BarcodeDecoder()
        analysis.setAnalyzer(executor) { image ->
            if (closed.get()) {
                image.close()
                return@setAnalyzer
            }
            val code = decoder.read(image)
            image.planes.forEach { it.buffer.rewind() }
            if (code != null && delivered.compareAndSet(false, true)) {
                mainExecutor.execute { onCode(code) }
            }
            detectLargest(detector, image) { rect, width, height ->
                if (closed.get()) return@detectLargest
                mainExecutor.execute {
                    framed = rect?.let { FramedObject(it, width, height) }
                }
            }
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
        )
        framed?.let { target ->
            ObjectCorners(target)
        }
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun detectLargest(
    detector: com.google.mlkit.vision.objects.ObjectDetector,
    image: androidx.camera.core.ImageProxy,
    onResult: (Rect?, Int, Int) -> Unit,
) {
    val mediaImage = image.image
    if (mediaImage == null) {
        image.close()
        return
    }
    val rotation = image.imageInfo.rotationDegrees
    val upright = rotation == 90 || rotation == 270
    val width = if (upright) image.height else image.width
    val height = if (upright) image.width else image.height
    try {
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { objects ->
                val largest = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                onResult(largest?.boundingBox, width, height)
                image.close()
            }
            .addOnFailureListener {
                onResult(null, width, height)
                image.close()
            }
    } catch (_: Exception) {
        image.close()
    }
}

@Composable
private fun ObjectCorners(target: FramedObject) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (target.width <= 0 || target.height <= 0) return@Canvas
        val scale = maxOf(size.width / target.width, size.height / target.height)
        val offsetX = (size.width - target.width * scale) / 2f
        val offsetY = (size.height - target.height * scale) / 2f
        val margin = 20.dp.toPx()
        val left = (target.rect.left * scale + offsetX).coerceIn(margin, size.width - margin)
        val top = (target.rect.top * scale + offsetY).coerceIn(margin, size.height - margin)
        val right = (target.rect.right * scale + offsetX).coerceIn(margin, size.width - margin)
        val bottom = (target.rect.bottom * scale + offsetY).coerceIn(margin, size.height - margin)
        if (right - left < 8.dp.toPx() || bottom - top < 8.dp.toPx()) return@Canvas
        val corner = 40.dp.toPx().coerceAtMost(minOf(right - left, bottom - top) / 2f)
        val stroke = 6.dp.toPx()
        val color = Color.White
        drawLine(color, Offset(left, top), Offset(left + corner, top), stroke)
        drawLine(color, Offset(left, top), Offset(left, top + corner), stroke)
        drawLine(color, Offset(right, top), Offset(right - corner, top), stroke)
        drawLine(color, Offset(right, top), Offset(right, top + corner), stroke)
        drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), stroke)
        drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), stroke)
        drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), stroke)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), stroke)
    }
}
