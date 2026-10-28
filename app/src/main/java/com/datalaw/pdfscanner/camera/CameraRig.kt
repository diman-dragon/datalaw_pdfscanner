package com.datalaw.pdfscanner.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
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
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** What [CameraRig.prepareShot] decided for one photo: [flash] = the flash fires, [gain] = how much lighter the page must be made afterwards, [settle] = wait for the meter. */
class ShotPlan(val flash: Boolean, val gain: Float, val settle: Boolean)

/** CameraX: a 4:3 preview and a photo (up to ~16 MP) of the same field of view, tap / frame focus, flash. */
class CameraRig(private val context: Context, val previewView: PreviewView) {
    private val executor = ContextCompat.getMainExecutor(context)
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null
    @Volatile private var exposureNs = 0L // the shutter time of the last preview frame

    private companion object {
        /** 1/40 s: a slower shutter than this blurs a handheld shot of small text. */
        const val SLOW_SHUTTER_NS = 25_000_000L
        // one thread for the whole process: the analysis never blocks the screen
        val analysisExecutor by lazy { Executors.newSingleThreadExecutor() }
    }

    init {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    /** Binds the camera. With a [watcher] the live frames are analysed too; false = that is not possible on this phone (the camera still works). */
    fun bind(provider: ProcessCameraProvider, owner: LifecycleOwner, flash: FlashMode, watcher: SheetWatcher? = null, size: CaptureSize = CaptureSize.Normal): Boolean {
        val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        fun attempt(withAnalysis: Boolean) {
            val previewSel = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
            // the size of the photo follows the PDF quality: ~16 MP is enough for 300 dpi, more is asked only for Auto and 600 dpi (a 50-200 MP photo is NOT asked for
            // by default: every decode, turn and crop of it is 4 times bigger and the phone kills the app for memory)
            val captureSel = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).apply {
                when (size) {
                    CaptureSize.Normal -> setResolutionStrategy(ResolutionStrategy(Size(4608, 3456), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    CaptureSize.Large -> {
                        setResolutionStrategy(ResolutionStrategy(Size(6000, 4500), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    }
                    CaptureSize.Max -> {
                        setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    }
                }
            }.build()
            val previewBuilder = Preview.Builder().setResolutionSelector(previewSel)
            watchShutter(previewBuilder)
            val preview = previewBuilder.build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cap = ImageCapture.Builder()
                .setResolutionSelector(captureSel)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(95)
                .build()
            provider.unbindAll()
            analysis = null
            if (withAnalysis && watcher != null) {
                val an = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                an.setAnalyzer(analysisExecutor, watcher)
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap, an)
                analysis = an
            } else {
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
            }
            capture = cap
        }
        var live = false
        if (watcher != null) live = try { attempt(true); true } catch (e: Exception) { false }
        if (!live) attempt(false)
        setFlash(flash)
        enableStabilizer()
        return live
    }

    /**
     * The camera's own "Auto" flash fires only when the shutter is already open as long as it can be: by then a hand has shaken the shot. For a document the flash
     * is used as soon as the shutter is slower than a hand can hold (about 1/40 s). [mode] is the person's choice; On / Off are never changed.
     * Returns true when the flash will fire for the next photo.
     */
    fun flashForShot(mode: FlashMode): Boolean {
        val m = if (mode == FlashMode.Auto && dim) FlashMode.On else mode
        setFlash(m)
        return m == FlashMode.On || (m == FlashMode.Auto && dim)
    }

    /** The light is so weak that the camera keeps the shutter open for longer than a hand holds still. */
    val dim: Boolean get() = exposureNs > SLOW_SHUTTER_NS

    /** The shutter time (ns) of the last preview frame: how long the sensor is exposed, so how still the hand must be. 0 = not known yet. */
    val shutterNs: Long get() = exposureNs

    /**
     * The phone is set up for one photo of a document. In the dark with no flash the exposure is lowered by one stop: the shutter is twice as fast, the shot is half
     * as blurred (it is darker; [ShotPlan.gain] gives the light back in software). [ShotPlan.settle] = the meter was changed and needs a moment to follow.
     */
    fun prepareShot(mode: FlashMode): ShotPlan {
        val flash = flashForShot(mode)
        val changed = setBias(if (dim && !flash) -1f else 0f)
        return ShotPlan(flash, Math.pow(2.0, -biasEv.toDouble()).toFloat(), changed)
    }

    /** After the photo: the exposure goes back to what the meter wants. */
    fun endShot() { setBias(0f) }

    @Volatile private var biasEv = 0f

    /** Sets the exposure compensation as near to [ev] stops as the camera allows; true = it changed. */
    private fun setBias(ev: Float): Boolean {
        val cam = camera ?: return false
        return try {
            val st = cam.cameraInfo.exposureState
            if (!st.isExposureCompensationSupported) return false
            val step = st.exposureCompensationStep.toFloat()
            if (step <= 0f) return false
            val index = Math.round(ev / step).coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)
            val now = index * step
            if (now == biasEv) return false
            cam.cameraControl.setExposureCompensationIndex(index)
            biasEv = now
            true
        } catch (e: Exception) {
            false // not every phone lets the exposure be changed: the shot is then simply as the meter wants it
        }
    }

    /** The optical stabiliser of the lens (when the phone has one): off by default in the still-photo requests of many phones, on it takes a good part of the hand's shake away. */
    @Suppress("UnsafeOptInUsageError")
    private fun enableStabilizer() {
        val cam = camera ?: return
        try {
            val modes = Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: return
            if (CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON !in modes) return
            Camera2CameraControl.from(cam.cameraControl).addCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                    .build(),
            )
        } catch (e: Exception) {
            // no stabiliser, or this phone does not allow the request: the shot is simply taken without it
        }
    }

    /** Reads the shutter time the camera chose for every preview frame (it tells how dark it is). */
    @Suppress("UnsafeOptInUsageError")
    private fun watchShutter(b: Preview.Builder) {
        Camera2Interop.Extender(b).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposureNs = it }
            }
        })
    }

    fun setFlash(m: FlashMode) {
        capture?.flashMode = when (m) {
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
            FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun setRotation(r: Int) { capture?.targetRotation = r; analysis?.targetRotation = r }

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
}
