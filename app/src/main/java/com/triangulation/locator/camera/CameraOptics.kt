package com.triangulation.locator.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.max
import kotlin.math.min

/**
 * The lens and sensor numbers needed to turn a pixel position in a photo into an angle.
 * They come from the camera's own specification sheet (Camera2 characteristics).
 */
data class CameraOptics(
    val focalLengthMm: Float,
    val pixelSizeMm: Double,
    val activeArrayLongPx: Int,
    val activeArrayShortPx: Int,
) {
    /**
     * Focal length in pixels, divided by the photo's width, for a photo of the given size.
     *
     * The photo is the largest centred crop of the sensor's active area with the photo's
     * aspect ratio, scaled to the photo size, at 1× zoom.
     *
     * Zoom (CameraX `setZoomRatio`) crops the picture around its centre, so a photo taken at
     * [zoomRatio]× covers 1/[zoomRatio] of the width and the focal length in photo widths grows
     * by the same factor. On phones that switch to a telephoto lens, CameraX keeps the zoom
     * ratio matched to the main lens's view, so this still holds closely.
     */
    fun focalFraction(photoWidth: Int, photoHeight: Int, zoomRatio: Double = 1.0): Double {
        val focalPxOnSensor = focalLengthMm / pixelSizeMm
        val scale = max(
            max(photoWidth, photoHeight).toDouble() / activeArrayLongPx,
            min(photoWidth, photoHeight).toDouble() / activeArrayShortPx,
        )
        return focalPxOnSensor * scale / photoWidth * zoomRatio
    }

    companion object {
        @OptIn(ExperimentalCamera2Interop::class)
        fun from(cameraInfo: CameraInfo): CameraOptics? = runCatching {
            val info = Camera2CameraInfo.from(cameraInfo)
            val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: return null
            val physical = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val pixelArray = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
            val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
            if (focal <= 0f || physical.width <= 0f || pixelArray.width <= 0) return null
            CameraOptics(
                focalLengthMm = focal,
                pixelSizeMm = physical.width.toDouble() / pixelArray.width,
                activeArrayLongPx = max(active.width(), active.height()),
                activeArrayShortPx = min(active.width(), active.height()),
            )
        }.getOrNull()
    }
}
