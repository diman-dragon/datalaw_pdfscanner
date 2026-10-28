package com.datalaw.pdfscanner.docedit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.EdgeDetector
import com.datalaw.pdfscanner.imaging.Enhance
import com.datalaw.pdfscanner.imaging.Look
import com.datalaw.pdfscanner.imaging.Sharpness
import com.datalaw.pdfscanner.ui.SecondaryButton
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.BottomBar
import com.datalaw.pdfscanner.ui.GlyphButton
import com.datalaw.pdfscanner.ui.RotateButtons
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.ToolRow
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FULL = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

/** The page as it is on the screen: its look and turns applied, and how many quarter-turns that was (the frame is drawn to match). */
private class Shown(val bmp: Bitmap, val turns: Int)

/**
 * The editor of one page. The page itself shows every change at once (turn, Original / Auto); the frame is drawn over it, the outside is darkened.
 * Drag the corners; double tap or "Auto frame" finds the frame by itself, "Whole page" takes it off. The tools are the row the viewer has.
 */
@Composable
fun PageEditor(
    ctl: DocEditController,
    p: EditPage,
    number: Int,
    total: Int,
    showBack: Boolean,
    nextLabel: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onSwipe: (Int) -> Unit = {},
    nextEnabled: Boolean = true,
    onGrid: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val bmp by produceState<Bitmap?>(null, p) { value = try { ctl.source(p, 2000) } catch (e: Exception) { null } }
    var quad by remember(p) { mutableStateOf(p.quad ?: FULL) }
    val b = bmp
    val swipeNow by rememberUpdatedState(onSwipe)

    // The picture on the screen: the old one stays until the new one is ready, so nothing blinks.
    val shown by produceState<Shown?>(null, b, p.look, p.turns) {
        if (b == null) return@produceState
        val t = ((p.turns % 4) + 4) % 4
        val look = p.look
        optional {
            var x: Bitmap = b
            if (look != Look.Original) x = Enhance.apply(x, look)
            if (t != 0) x = BitmapLoader.rotate(x, t * 90)
            Shown(x, t)
        }?.let { value = it }
    }
    val sharp by produceState(1f, b) { if (b != null) optional { Sharpness.of(b) }?.let { value = it } }

    fun setQuad(q: List<Offset>) { quad = q; p.quad = q; ctl.markDirty() }
    fun autoFrame() {
        val s = b ?: return
        scope.launch {
            val pts = optional { EdgeDetector.detect(s) } ?: return@launch
            setQuad(pts.map { Offset(it.x / s.width, it.y / s.height) })
        }
    }

    val sh = shown
    val view: Bitmap? = sh?.bmp ?: b
    val k = sh?.turns ?: 0
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).systemBarsPadding()) {
        TitleBar(ctl.file.name, onBack = onCancel) {
            Text(stringResource(R.string.vw_page_n, number, total), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (onGrid != null) GlyphButton("▦", stringResource(R.string.de_layout_grid), onGrid)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(Dimens.screen)) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            if (view != null) {
                val scale = min(wPx / view.width, hPx / view.height)
                val dw = view.width * scale
                val dh = view.height * scale
                val ox = (wPx - dw) / 2
                val oy = (hPx - dh) / 2
                Image(view.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(view, k, p) {
                            var active = -1
                            var dx = 0f
                            var dy = 0f
                            detectDragGestures(
                                onDragStart = { pt ->
                                    var best = -1
                                    var bestD = 56.dp.toPx()
                                    quad.forEachIndexed { i, q ->
                                        val v = toView(q, k)
                                        val dist = hypot(pt.x - (ox + v.x * dw), pt.y - (oy + v.y * dh))
                                        if (dist < bestD) { bestD = dist; best = i }
                                    }
                                    active = best
                                    dx = 0f
                                    dy = 0f
                                },
                                // a drag that did not start on a corner and went mostly sideways is a swipe: the next page (left) or the previous one (right)
                                onDragEnd = {
                                    if (active < 0 && abs(dx) > 90.dp.toPx() && abs(dx) > 2 * abs(dy)) swipeNow(if (dx < 0) 1 else -1)
                                    active = -1
                                },
                                onDragCancel = { active = -1 },
                            ) { change, drag ->
                                if (active < 0) { dx += drag.x; dy += drag.y }
                                if (active >= 0) {
                                    change.consume()
                                    val q = quad.toMutableList()
                                    val v = toView(q[active], k)
                                    val nv = Offset((v.x + drag.x / dw).coerceIn(0f, 1f), (v.y + drag.y / dh).coerceIn(0f, 1f))
                                    q[active] = fromView(nv, k)
                                    setQuad(q)
                                }
                            }
                        }
                        .pointerInput(b) { detectTapGestures(onDoubleTap = { autoFrame() }) },
                ) {
                    val pts = quad.map { toView(it, k) }.map { Offset(ox + it.x * dw, oy + it.y * dh) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1..3) lineTo(pts[i].x, pts[i].y); close() }
                    val outside = Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(ox, oy, ox + dw, oy + dh)); addPath(path) }
                    drawPath(outside, Color.Black.copy(alpha = 0.4f))
                    drawPath(path, scheme.primary, style = Stroke(2.dp.toPx()))
                    pts.forEach { drawCircle(scheme.primary, 11.dp.toPx(), it); drawCircle(scheme.surface, 11.dp.toPx(), it, style = Stroke(1.5.dp.toPx())) }
                }
            }
        }

        Column(Modifier.padding(horizontal = Dimens.screen), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            if (Sharpness.isBlurry(sharp)) Text(stringResource(R.string.rs_warn_blur), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            tilt(quad, b)?.let { Text(stringResource(R.string.rs_warn_tilt_n, it), style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            Text(stringResource(R.string.rs_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        ToolRow {
            RotateButtons { d -> p.turns = ((p.turns + d) % 4 + 4) % 4; ctl.markDirty() }
            Seg(Look.entries.map { it to stringResource(it.labelRes()) }, p.look, { p.look = it; ctl.markDirty() }, Modifier.weight(1f), scroll = true)
        }
        ToolRow {
            SecondaryButton(stringResource(R.string.rs_autoframe), { autoFrame() }, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.rs_resetframe), { setQuad(FULL) }, Modifier.weight(1f))
        }
        BottomBar {
            if (showBack) SecondaryButton(stringResource(R.string.rs_back), onBack)
            if (ctl.pages.size > 1) SecondaryButton(stringResource(R.string.sc_delete), onDelete)
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(nextLabel), onNext, enabled = nextEnabled)
        }
    }
}
