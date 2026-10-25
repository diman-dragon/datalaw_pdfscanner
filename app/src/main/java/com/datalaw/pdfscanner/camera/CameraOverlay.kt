package com.datalaw.pdfscanner.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What is drawn over the preview: the frame with its corners (Frame mode), the label, the outline of the found sheet (it fills as the shot comes
 * nearer, [hold] 0..1) and the focus ring. [ring] = x, y and the result (null = still focusing). A tap is reported as it is.
 */
@Composable
internal fun CameraOverlay(
    frame: FloatArray,
    auto: Boolean,
    label: String,
    frameColor: Color,
    bad: Boolean,
    sheet: List<Offset>?,
    hold: Float,
    ring: Triple<Float, Float, Boolean?>?,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapNow by rememberUpdatedState(onTap)
    Canvas(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { tapNow(it) } }) {
        val a = frame[0] * size.width
        val b = frame[1] * size.height
        val c = frame[2] * size.width
        val d = frame[3] * size.height
        if (!auto) {
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRect(Rect(a, b, c, d))
            }
            drawPath(dim, Color(0x6B000000))
            drawRect(frameColor, Offset(a, b), Size(c - a, d - b), style = Stroke(2.dp.toPx()))
            val len = 26.dp.toPx()
            val sw = 5.dp.toPx()
            for ((cx, cy, dx, dy) in listOf(listOf(a, b, 1f, 1f), listOf(c, b, -1f, 1f), listOf(c, d, -1f, -1f), listOf(a, d, 1f, -1f))) {
                drawLine(frameColor, Offset(cx, cy), Offset(cx + dx * len, cy), sw, StrokeCap.Round)
                drawLine(frameColor, Offset(cx, cy), Offset(cx, cy + dy * len), sw, StrokeCap.Round)
            }
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 13.sp.toPx()
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
        drawContext.canvas.nativeCanvas.drawText(label, (if (auto) 0f else a) + 10.dp.toPx(), (if (auto) 0f else b) + 22.dp.toPx(), paint)
        sheet?.let { q ->
            val p = q.map { Offset(it.x * size.width, it.y * size.height) }
            val outline = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1..3) lineTo(p[i].x, p[i].y); close() }
            val ink = if (bad) Color(0xFFFF5A4D) else Color(0xFF4CD964)
            drawPath(outline, ink.copy(alpha = 0.18f + 0.35f * hold))
            drawPath(outline, ink, style = Stroke(3.dp.toPx()))
            p.forEach { drawCircle(ink, 6.dp.toPx(), it) }
        }
        ring?.let { (rx, ry, ok) ->
            drawCircle(when (ok) { true -> Color(0xFF4CD964); false -> Color(0xFFFFB020); null -> Color.White }, 30.dp.toPx(), Offset(rx, ry), style = Stroke(2.dp.toPx()))
        }
    }
}
