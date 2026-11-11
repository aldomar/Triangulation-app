package com.triangulation.locator.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Longest side of the saved photo, in pixels. Keeps files small (a few hundred KB). */
const val MAX_PHOTO_SIDE_PX = 1920

suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            runCatching { future.get() }
                .onSuccess { cont.resume(it) }
                .onFailure { cont.resumeWithException(it) }
        },
        ContextCompat.getMainExecutor(this),
    )
}

/**
 * Takes one photo and returns it upright and scaled down to at most [MAX_PHOTO_SIDE_PX].
 * Decoding runs off the main thread so the screen doesn't stutter.
 */
suspend fun ImageCapture.takeUprightPhoto(): Bitmap = suspendCancellableCoroutine { cont ->
    takePicture(
        Dispatchers.Default.asExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                runCatching { image.use { it.toBitmap().uprightAndScaled(it.imageInfo.rotationDegrees) } }
                    .onSuccess { cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            }

            override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
        },
    )
}

private fun Bitmap.uprightAndScaled(rotationDegrees: Int): Bitmap {
    val scale = minOf(1f, MAX_PHOTO_SIDE_PX.toFloat() / max(width, height))
    if (rotationDegrees == 0 && scale == 1f) return this
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    val out = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (out !== this) recycle()
    return out
}
