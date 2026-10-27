package com.datalaw.pdfscanner.camera

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Breadcrumbs
import com.datalaw.pdfscanner.core.CameraSession
import com.datalaw.pdfscanner.core.askSaveAsIs
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.core.hint
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.core.userReason
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.Sharpness
import com.datalaw.pdfscanner.imaging.SheetFinder
import com.datalaw.pdfscanner.imaging.SheetFit
import com.datalaw.pdfscanner.imaging.ShotCheck
import com.datalaw.pdfscanner.imaging.ShotFile
import com.datalaw.pdfscanner.imaging.ShotProblem
import com.datalaw.pdfscanner.imaging.shotSharpness
import com.datalaw.pdfscanner.settings.CamMode
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The bare camera: a preview, the shutter, the flash, tap to focus, and (Quick / Scan) the Frame / Auto modes with the live outline of the sheet.
 * After a shot the session decides what happens (the photo is checked for sharpness first: a soft shot asks "Retake / Save as is").
 * The loops (focus, the shot by itself), the drawing and the bars are in their own files.
 */
@Composable
fun CameraScreen(session: CameraSession, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val camUi by session.ui.collectAsStateWithLifecycle()
    val host = graph.ui

    val permission = rememberCameraPermission()

    val closeNow = { session.onClosed(); onClose() }
    val askClose: () -> Unit = { scope.launch { if (session.canClose()) closeNow() } }
    BackHandler(onBack = askClose)

    if (!permission.granted) {
        CameraPermissionPrompt(permission.ask, askClose)
        return
    }

    val rig = remember { CameraRig(appCtx, PreviewView(activity ?: ctx)) }
    val gyro = remember { Gyro(appCtx) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(FocusState.Idle) }
    var ring by remember { mutableStateOf<Triple<Float, Float, Boolean?>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<List<Offset>?>(null) }
    var watching by remember { mutableStateOf(false) }
    var hold by remember { mutableFloatStateOf(0f) }
    val smart = session.smart
    val mode = when {
        !smart -> CamMode.Frame
        session.forcedAuto -> CamMode.Auto
        else -> settings.camMode
    }
    val auto = mode == CamMode.Auto
    val autoNow by rememberUpdatedState(auto)
    LaunchedEffect(auto) { if (!auto) live = null }

    // The live search of the sheet is bound only in Auto: it costs the camera its biggest photo size, and Frame mode does not need it
    DisposableEffect(owner, auto) {
        gyro.start()
        var gone = false // the screen may be closed before the camera provider is ready: then nothing is bound
        val fut = ProcessCameraProvider.getInstance(appCtx)
        fut.addListener({
            if (!gone) {
                try { watching = rig.bind(fut.get(), owner, settings.flash, if (smart && auto) SheetWatcher(session.expectRatio, session.minAreaFrac, { autoNow && !busy }) { live = it } else null); ready = true } catch (e: Exception) { failed = true }
            }
        }, ContextCompat.getMainExecutor(appCtx))
        onDispose {
            gone = true
            gyro.stop()
            if (fut.isDone) runCatching { fut.get().unbindAll() }
            ready = false
        }
    }
    LaunchedEffect(settings.flash, ready) { if (ready) rig.setFlash(settings.flash) }
    LaunchedEffect(land, ready) {
        if (ready) rig.setRotation(rig.previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
    }
    LaunchedEffect(ring) { if (ring != null) { delay(1200); ring = null } }

    val aspect = if (land) 4f / 3f else 3f / 4f
    val frame = FrameMath.frame(session.frameRatio(land), aspect)
    val sheet = live
    // Auto mode only, by the same rule as after the shot; Frame mode searches and compares nothing
    val problem = if (smart && auto && (sheet != null || watching)) ShotCheck.problem(sheet) else null
    val bad = problem == ShotProblem.Cut

    fun shoot() {
        if (busy || !ready) return
        busy = true
        scope.launch {
            try {
                // the tap on the shutter shakes the phone: let it settle (at most half a second) before the photo is taken
                delay(100)
                var waited = 0
                while (!gyro.steady && waited < 400) { delay(50); waited += 50 }
                val region = if (auto) live?.let { SheetFit.bbox(it) } else frame
                // in the dark: the flash goes off as soon as the shutter would be too slow for a hand; with no flash the camera takes three photos and keeps the sharpest
                val dark = rig.dim
                val flashed = rig.flashForShot(settings.flash)
                Breadcrumbs.note("shoot: dim=$dark flash=$flashed")
                val shot = takeShot(rig, appCtx.cacheDir, host, session, auto, region, aspect, land, burst = dark && !flashed)
                if (shot != null && session.onShot(shot)) closeNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.toast(tx(R.string.scan_fail_n, e.userReason()))
            } finally {
                busy = false
            }
        }
    }

    SmartFocusEffect(
        ready = ready,
        busy = { busy },
        steady = { gyro.steady },
        center = {
            val q = live
            val cx = if (auto && q != null) q.map { it.x }.average().toFloat() else (frame[0] + frame[2]) / 2
            val cy = if (auto && q != null) q.map { it.y }.average().toFloat() else (frame[1] + frame[3]) / 2
            Offset(cx * rig.previewView.width, cy * rig.previewView.height)
        },
        width = { rig.previewView.width },
        focusAt = { x, y -> rig.focusAt(x, y, lock = true, withExposure = false) },
        onState = { focus = it },
    )
    AutoShootEffect(
        enabled = ready && session.autoShoot,
        sheet = { live },
        canShoot = { !busy && sheet != null && problem == null && focus != FocusState.Seeking && gyro.steady },
        onHold = { hold = it },
        shoot = { shoot() },
    )

    val frameColor = frameColorOf(bad, focus)
    val label = if (smart && auto) (camUi.autoLabel ?: camUi.label).get() else camUi.label.get()
    val hint = cameraHint(failed, camUi, hold, problem, session.sheetLike, focus, smart && auto)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = minOf(maxWidth, maxHeight * aspect)
            Box(Modifier.align(Alignment.Center).size(w, w / aspect)) {
                AndroidView(factory = { rig.previewView }, modifier = Modifier.fillMaxSize())
                CameraOverlay(frame, auto, label, frameColor, bad, sheet, hold, ring, onTap = { p ->
                    if (ready) {
                        ring = Triple(p.x, p.y, null)
                        scope.launch {
                            val ok = rig.focusAt(p.x, p.y, lock = true, withExposure = true)
                            ring = Triple(p.x, p.y, ok)
                            focus = if (ok) FocusState.Locked else FocusState.NoDetail
                        }
                    }
                })
            }
        }
        CameraTopBar(
            onClose = askClose, smart = smart, forcedAuto = session.forcedAuto, mode = mode,
            onMode = { m -> scope.launch { graph.settingsStore.setCamMode(m) } },
            badge = camUi.badge, flash = settings.flash,
            onFlash = { scope.launch { graph.settingsStore.setFlash(settings.flash.next()) } },
        )
        CameraBottomBar(hint, busy, ready, { shoot() }, Modifier.align(Alignment.BottomCenter))
    }
}
