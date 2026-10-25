package com.datalaw.pdfscanner.docedit

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.imaging.EdgeDetector
import com.datalaw.pdfscanner.imaging.Look
import com.datalaw.pdfscanner.imaging.PageRenderer
import com.datalaw.pdfscanner.imaging.Sharpness
import com.datalaw.pdfscanner.ui.BottomBar
import com.datalaw.pdfscanner.ui.GlyphButton
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FULL = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

/** The editor of one page: drag the corners (double tap = automatic frame), Original / Auto, rotate; Back / Next / Delete. */
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
) {
    val scope = rememberCoroutineScope()
    val bmp by produceState<Bitmap?>(null, p) { value = try { ctl.source(p, 2000) } catch (e: Exception) { null } }
    var quad by remember(p) { mutableStateOf(p.quad ?: FULL) }
    val b = bmp

    val preview by produceState<Bitmap?>(null, b, quad, p.look, p.turns) {
        if (b == null) return@produceState
        delay(150)
        value = withContext(Dispatchers.Default) { PageRenderer.preview(b, quad, p.look, p.turns, 360) }
    }
    val sharp by produceState(1f, b) { if (b != null) value = withContext(Dispatchers.Default) { Sharpness.of(b) } }

    fun setQuad(q: List<Offset>) { quad = q; p.quad = q; ctl.markDirty() }

    val scheme = MaterialTheme.colorScheme
    val rotLeft = stringResource(R.string.rs_rotl)
    val rotRight = stringResource(R.string.rs_rotr)
    Column(Modifier.fillMaxSize().background(scheme.background).systemBarsPadding()) {
        TitleBar(if (total > 1) stringResource(R.string.rs_pageof, number, total) else stringResource(R.string.rs_title), onBack = onCancel)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(Dimens.screen)) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            if (b != null) {
                val scale = min(wPx / b.width, hPx / b.height)
                val dw = b.width * scale
                val dh = b.height * scale
                val ox = (wPx - dw) / 2
                val oy = (hPx - dh) / 2
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(b) {
                            var active = -1
                            detectDragGestures(
                                onDragStart = { pt ->
                                    var best = -1
                                    var bestD = 56.dp.toPx()
                                    quad.forEachIndexed { i, q ->
                                        val dist = hypot(pt.x - (ox + q.x * dw), pt.y - (oy + q.y * dh))
                                        if (dist < bestD) { bestD = dist; best = i }
                                    }
                                    active = best
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 },
                            ) { change, drag ->
                                if (active >= 0) {
                                    change.consume()
                                    val q = quad.toMutableList()
                                    val c = q[active]
                                    q[active] = Offset((c.x + drag.x / dw).coerceIn(0f, 1f), (c.y + drag.y / dh).coerceIn(0f, 1f))
                                    setQuad(q)
                                }
                            }
                        }
                        .pointerInput(b) {
                            detectTapGestures(onDoubleTap = {
                                scope.launch {
                                    val pts = withContext(Dispatchers.Default) { EdgeDetector.detect(b) }
                                    setQuad(pts.map { Offset(it.x / b.width, it.y / b.height) })
                                }
                            })
                        },
                ) {
                    val pts = quad.map { Offset(ox + it.x * dw, oy + it.y * dh) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1..3) lineTo(pts[i].x, pts[i].y); close() }
                    drawPath(path, scheme.primary.copy(alpha = 0.15f), style = Fill)
                    drawPath(path, scheme.primary, style = Stroke(2.dp.toPx()))
                    pts.forEach { drawCircle(scheme.primary, 11.dp.toPx(), it); drawCircle(scheme.surface, 11.dp.toPx(), it, style = Stroke(1.5.dp.toPx())) }
                }
                preview?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.align(Alignment.TopEnd).width(84.dp).clip(RoundedCornerShape(Dimens.thumb)).border(1.dp, scheme.outline, RoundedCornerShape(Dimens.thumb)).background(Color.White),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = Dimens.screen).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            if (Sharpness.isBlurry(sharp)) Text(stringResource(R.string.rs_warn_blur), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            tilt(quad, b)?.let { Text(stringResource(R.string.rs_warn_tilt_n, it), style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            Text(stringResource(R.string.rs_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                GlyphButton("⟲", rotLeft, { p.turns = (p.turns + 3) % 4; ctl.markDirty() })
                GlyphButton("⟳", rotRight, { p.turns = (p.turns + 1) % 4; ctl.markDirty() })
                Seg(listOf(Look.Original to stringResource(R.string.fx_color), Look.Auto to stringResource(R.string.fx_auto)), p.look, { p.look = it; ctl.markDirty() }, Modifier.weight(1f), fill = true)
            }
        }
        BottomBar {
            if (showBack) OutlinedButton(onBack) { Text(stringResource(R.string.rs_back)) }
            if (ctl.pages.size > 1) OutlinedButton(onDelete) { Text(stringResource(R.string.sc_delete)) }
            Spacer(Modifier.weight(1f))
            Button(onNext) { Text(stringResource(nextLabel)) }
        }
    }
}

/** Percent of perspective distortion of the frame (null when small). */
private fun tilt(q: List<Offset>, b: Bitmap?): Int? {
    if (b == null) return null
    fun d(a: Offset, c: Offset) = hypot((a.x - c.x) * b.width, (a.y - c.y) * b.height)
    val top = d(q[0], q[1]); val bot = d(q[3], q[2]); val lef = d(q[0], q[3]); val rig = d(q[1], q[2])
    val t = max(abs(top / max(bot, 1f) - 1f), abs(lef / max(rig, 1f) - 1f))
    return if (t > 0.15f) (t * 100).toInt() else null
}
