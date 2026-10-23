package com.datalaw.pdfscanner.camera

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.SheetFit
import com.datalaw.pdfscanner.imaging.Sharpness
import com.datalaw.pdfscanner.settings.FocusMode
import com.datalaw.pdfscanner.settings.QuickMode
import com.datalaw.pdfscanner.settings.FlashMode
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.get
import com.datalaw.pdfscanner.ui.tx
import com.datalaw.pdfscanner.ui.uiRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class FocusState { Idle, Seeking, Locked, NoDetail }

/** How the sheet sits: fits, goes past the frame (Frame mode), is cut by the camera view (Auto mode), or is not seen (Auto mode). */
private enum class Fit { Unknown, Ok, Outside, Cut, NoSheet }

/**
 * The bare camera: a preview with a fixed frame, the shutter, the flash, tap to focus. After a shot the session decides what happens
 * (the photo is checked for sharpness first when the session wants it: a soft shot asks "Retake / Save as is").
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
    val camUi by session.ui.collectAsState()
    val host = graph.ui

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(appCtx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val closeNow = { session.onClosed(); onClose() }
    val askClose: () -> Unit = { scope.launch { if (session.canClose()) closeNow() } }
    BackHandler(onBack = askClose)

    if (!granted) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.cam_perm), color = Color.White)
            Button({ permission.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.cam_grant)) }
            Button(askClose, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.vw_close)) }
        }
        return
    }

    val rig = remember { CameraRig(appCtx, PreviewView(activity ?: ctx)) }
    val gyro = remember { Gyro(appCtx) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(FocusState.Idle) }
    var ring by remember { mutableStateOf<Triple<Float, Float, Boolean?>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val smart = session.smart
    val mode = if (smart) settings.quickMode else QuickMode.Frame
    val auto = mode == QuickMode.Auto
    val modeNow by rememberUpdatedState(mode)
    var live by remember { mutableStateOf<List<Offset>?>(null) }
    var watching by remember { mutableStateOf(false) }

    DisposableEffect(owner) {
        gyro.start()
        val fut = ProcessCameraProvider.getInstance(appCtx)
        fut.addListener({
            try { watching = rig.bind(fut.get(), owner, settings.flash, if (smart) SheetWatcher { live = it } else null); ready = true } catch (e: Exception) { failed = true }
        }, ContextCompat.getMainExecutor(appCtx))
        onDispose {
            gyro.stop()
            if (fut.isDone) runCatching { fut.get().unbindAll() }
            ready = false
        }
    }
    LaunchedEffect(settings.flash, ready) { if (ready) rig.setFlash(settings.flash) }
    LaunchedEffect(land, ready) {
        if (ready) rig.setRotation(rig.previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
    }

    val aspect = if (land) 4f / 3f else 3f / 4f
    val frame = FrameMath.frame(session.frameRatio(land), aspect)
    val sheet = live
    val fit = when {
        !smart -> Fit.Unknown
        sheet == null -> if (auto && watching) Fit.NoSheet else Fit.Unknown
        auto -> if (SheetFit.touchesEdge(sheet)) Fit.Cut else Fit.Ok
        else -> if (SheetFit.outside(sheet, frame)) Fit.Outside else Fit.Ok
    }
    fun centerFocusPoint(): Offset {
        val q = live
        val auto = modeNow == QuickMode.Auto
        val cx = if (auto && q != null) q.map { it.x }.average().toFloat() else (frame[0] + frame[2]) / 2
        val cy = if (auto && q != null) q.map { it.y }.average().toFloat() else (frame[1] + frame[3]) / 2
        return Offset(cx * rig.previewView.width, cy * rig.previewView.height)
    }

    // Smart focus: lock the focus on the frame; when the phone has moved and is steady again, focus again.
    LaunchedEffect(ready, settings.focus) {
        if (!ready) return@LaunchedEffect
        if (settings.focus == FocusMode.System) { rig.releaseFocus(); focus = FocusState.Idle; return@LaunchedEffect }
        var moved = true
        var steadyTicks = 0
        while (isActive) {
            delay(300)
            if (busy) continue
            if (gyro.steady) steadyTicks++ else { steadyTicks = 0; moved = true }
            if (moved && steadyTicks >= 3 && rig.previewView.width > 0) {
                focus = FocusState.Seeking
                val c = centerFocusPoint()
                focus = if (rig.focusAt(c.x, c.y, lock = true, withExposure = false)) FocusState.Locked else FocusState.NoDetail
                moved = false
            }
        }
    }
    LaunchedEffect(ring) { if (ring != null) { delay(1200); ring = null } }

    fun shoot() {
        if (busy || !ready) return
        busy = true
        scope.launch {
            try {
                val dir = File(appCtx.cacheDir, "shots").apply { mkdirs() }
                val file = rig.takePhoto(File(dir, "shot_${System.nanoTime()}.jpg"))
                val (w, h) = withContext(Dispatchers.IO) { BitmapLoader.uprightSize(file) }
                val photoAspect = w.toFloat() / h
                val region = if (auto) live?.let { bbox(it) } else frame
                val crop = if (Math.abs(photoAspect - aspect) < 0.04f * aspect) region else null
                val shot = ShotFile(file, crop, aspect, land)
                val s = withContext(Dispatchers.Default) { blobSharpness(shot) }
                if (Sharpness.isBlurry(s)) {
                    val a = host.askChoice(tx(R.string.qs_blur), listOf("retake" to tx(R.string.qs_blur_retake), "save" to tx(R.string.qs_blur_save)))
                    if (a != "save") { file.delete(); return@launch }
                }
                if (session.onShot(shot)) closeNow()
            } catch (e: Exception) {
                host.toast(tx(R.string.scan_fail_n, e.message ?: ""))
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = minOf(maxWidth, maxHeight * aspect)
            Box(Modifier.align(Alignment.Center).size(w, w / aspect)) {
                AndroidView(factory = { rig.previewView }, modifier = Modifier.fillMaxSize())
                val bad = fit == Fit.Outside || fit == Fit.Cut
                val frameColor = when {
                    bad -> Color(0xFFFF5A4D)
                    focus == FocusState.Locked -> Color(0xFF4CD964)
                    focus == FocusState.NoDetail -> Color(0xFFFFB020)
                    else -> Color.White
                }
                val labelText = if (smart && auto) stringResource(R.string.qs_label_auto) else camUi.label.get()
                Canvas(
                    Modifier.fillMaxSize().pointerInput(ready, settings.focus) {
                        detectTapGestures { p ->
                            ring = Triple(p.x, p.y, null)
                            scope.launch {
                                val ok = rig.focusAt(p.x, p.y, lock = true, withExposure = true)
                                ring = Triple(p.x, p.y, ok)
                                focus = if (ok) FocusState.Locked else FocusState.NoDetail
                            }
                        }
                    },
                ) {
                    val a = frame[0] * size.width
                    val b = frame[1] * size.height
                    val c = frame[2] * size.width
                    val d = frame[3] * size.height
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(Rect(a, b, c, d))
                    }
                    if (!auto) {
                        drawPath(dim, Color(0x6B000000))
                        drawRect(frameColor, Offset(a, b), Size(c - a, d - b), style = Stroke(2.dp.toPx()))
                        val L = 26.dp.toPx()
                        val sw = 5.dp.toPx()
                        for ((cx, cy, dx, dy) in listOf(listOf(a, b, 1f, 1f), listOf(c, b, -1f, 1f), listOf(c, d, -1f, -1f), listOf(a, d, 1f, -1f))) {
                            drawLine(frameColor, Offset(cx, cy), Offset(cx + dx * L, cy), sw, StrokeCap.Round)
                            drawLine(frameColor, Offset(cx, cy), Offset(cx, cy + dy * L), sw, StrokeCap.Round)
                    }
                    }
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 13.sp.toPx(); setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK) }
                    drawContext.canvas.nativeCanvas.drawText(labelText, (if (auto) 0f else a) + 10.dp.toPx(), (if (auto) 0f else b) + 22.dp.toPx(), paint)
                    sheet?.let { q ->
                        val p = q.map { Offset(it.x * size.width, it.y * size.height) }
                        val outline = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1..3) lineTo(p[i].x, p[i].y); close() }
                        val ink = if (bad) Color(0xFFFF5A4D) else Color(0xFF4CD964)
                        drawPath(outline, ink.copy(alpha = 0.18f))
                        drawPath(outline, ink, style = Stroke(3.dp.toPx()))
                        p.forEach { drawCircle(ink, 6.dp.toPx(), it) }
                    }
                    ring?.let { (rx, ry, ok) ->
                        drawCircle(when (ok) { true -> Color(0xFF4CD964); false -> Color(0xFFFFB020); null -> Color.White }, 30.dp.toPx(), Offset(rx, ry), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }

        // top: close, counter, flash
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✕", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable(onClick = askClose).padding(8.dp))
            if (smart) ModeToggle(mode) { m -> scope.launch { graph.settingsStore.setQuickMode(m) } } else camUi.badge?.let { Text(it.get(), color = Color.White, fontSize = 15.sp) }
            val flashLabel = when (settings.flash) { FlashMode.Auto -> R.string.fl_auto; FlashMode.On -> R.string.fl_on; FlashMode.Off -> R.string.fl_off }
            Text("⚡ " + stringResource(R.string.cam_flash) + ": " + stringResource(flashLabel), color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable { scope.launch { graph.settingsStore.setFlash(settings.flash.next()) } }.padding(8.dp))
        }

        // bottom: hint / status and the shutter
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val shootLabel = stringResource(R.string.scan_shoot)
            val hint = when {
                failed -> stringResource(R.string.cam_unavailable)
                camUi.status != null -> camUi.status!!.get()
                fit == Fit.Outside -> stringResource(R.string.qs_outside)
                fit == Fit.Cut -> stringResource(R.string.qs_cut)
                fit == Fit.NoSheet -> stringResource(R.string.qs_nosheet)
                focus == FocusState.Seeking -> stringResource(R.string.qs_focusing)
                focus == FocusState.Locked -> stringResource(R.string.qs_focuslock)
                focus == FocusState.NoDetail -> stringResource(R.string.qs_nodetail)
                smart && auto -> stringResource(R.string.qs_hint_auto)
                else -> camUi.hint.get()
            }
            Text(
                hint, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Box(
                Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(7.dp)
                    .background(if (busy) Color.Gray else Color.White, CircleShape)
                    .semantics { contentDescription = shootLabel }
                    .clickable(enabled = !busy && ready) { shoot() },
            )
        }
    }
}

private fun bbox(q: List<Offset>) = floatArrayOf(
    q.minOf { it.x }.coerceIn(0f, 1f), q.minOf { it.y }.coerceIn(0f, 1f), q.maxOf { it.x }.coerceIn(0f, 1f), q.maxOf { it.y }.coerceIn(0f, 1f),
)

private fun blobSharpness(shot: ShotFile): Float = try {
    val c = shot.crop
    val img = BitmapLoader.load(shot.file, 1600)
    val region = if (c != null && Math.abs(img.width.toFloat() / img.height - shot.vr) < 0.04f * shot.vr) {
        val x0 = (c[0] * img.width).toInt().coerceIn(0, img.width - 1)
        val y0 = (c[1] * img.height).toInt().coerceIn(0, img.height - 1)
        android.graphics.Bitmap.createBitmap(img, x0, y0, ((c[2] - c[0]) * img.width).toInt().coerceIn(1, img.width - x0), ((c[3] - c[1]) * img.height).toInt().coerceIn(1, img.height - y0))
    } else img
    val s = Sharpness.of(region)
    if (region !== img) region.recycle()
    img.recycle()
    s
} catch (e: Throwable) {
    1f
}
