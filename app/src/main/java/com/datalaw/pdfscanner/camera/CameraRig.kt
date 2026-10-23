package com.datalaw.pdfscanner.camera

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.datalaw.pdfscanner.settings.FlashMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX: a 4:3 preview and a full-resolution photo of the same field of view, tap / frame focus, flash. */
class CameraRig(private val context: Context, val previewView: PreviewView) {
    private val executor = ContextCompat.getMainExecutor(context)
    private var camera: Camera? = null
    private var capture: ImageCapture? = null

    init {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    fun bind(provider: ProcessCameraProvider, owner: LifecycleOwner, flash: FlashMode) {
        val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val previewSel = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
        val captureSel = ResolutionSelector.Builder()
            .setAspectRatioStrategy(ratio)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(previewSel).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val cap = ImageCapture.Builder()
            .setResolutionSelector(captureSel)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()
        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
        capture = cap
        setFlash(flash)
    }

    fun setFlash(m: FlashMode) {
        capture?.flashMode = when (m) {
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
            FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun setRotation(r: Int) { capture?.targetRotation = r }

    suspend fun takePhoto(file: File): File = suspendCancellableCoroutine { c ->
        val cap = capture
        if (cap == null) {
            c.resumeWithException(IllegalStateException("camera not ready"))
            return@suspendCancellableCoroutine
        }
        cap.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) { c.resume(file) }
            override fun onError(e: ImageCaptureException) { c.resumeWithException(e) }
        })
    }

    /** Focuses on a point of the preview (pixels of the preview view). [lock] keeps the focus until the next call. True if the camera reports a sharp focus. */
    suspend fun focusAt(x: Float, y: Float, lock: Boolean, withExposure: Boolean): Boolean {
        val cam = camera ?: return false
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val flags = if (withExposure) FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE else FocusMeteringAction.FLAG_AF
        val b = FocusMeteringAction.Builder(point, flags)
        if (lock) b.disableAutoCancel()
        val action = b.build()
        if (!cam.cameraInfo.isFocusMeteringSupported(action)) return false
        return try {
            suspendCancellableCoroutine { c ->
                val f = cam.cameraControl.startFocusAndMetering(action)
                f.addListener({
                    try { c.resume(f.get().isFocusSuccessful) } catch (e: Exception) { c.resume(false) }
                }, executor)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Back to the continuous autofocus of the system. */
    fun releaseFocus() { camera?.cameraControl?.cancelFocusAndMetering() }
}
