package com.datalaw.pdfscanner.docedit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * All the fingers of the page editor in one place, so that they never fight:
 *  - two fingers zoom around the point between them and move the page;
 *  - a double tap zooms in at the place tapped, the next one zooms out;
 *  - one finger that starts on a corner of the frame moves the corner ([cornerAt] tells which, in pixels of the page);
 *  - one finger elsewhere moves the page when it is zoomed, and when it is not, a sideways swipe goes to the next or the previous page ([onSwipe] gets +1 / -1).
 * [key] restarts the handler when the size of the view changes.
 */
internal fun Modifier.pageGestures(
    zoom: EditorZoom,
    key: Any,
    viewport: () -> Size,
    cornerAt: (Offset) -> Int,
    moveCorner: (Int, Offset) -> Unit,
    onSwipe: (Int) -> Unit,
): Modifier = pointerInput(key) {
    var lastTapAt = 0L
    var lastTapPos = Offset.Zero
    val slop = viewConfiguration.touchSlop
    val swipeMin = 90.dp.toPx()
    val tapNear = 80.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val vp = viewport()
        var corner = cornerAt(zoom.toPage(down.position))
        var moved = false
        var multi = false
        var sx = 0f
        var sy = 0f
        do {
            val ev = awaitPointerEvent()
            if (ev.changes.count { it.pressed } >= 2) {
                multi = true
                corner = -1
                val z = ev.calculateZoom()
                val pan = ev.calculatePan()
                if (z != 1f || pan != Offset.Zero) {
                    zoom.zoomAt(zoom.scale * z, ev.calculateCentroid(), pan, vp.width, vp.height)
                    ev.changes.forEach { it.consume() }
                }
            } else if (!multi) {
                val ch = ev.changes.firstOrNull { it.id == down.id }
                if (ch != null && ch.pressed) {
                    if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                    val d = ch.positionChange()
                    if (moved && d != Offset.Zero) {
                        when {
                            corner >= 0 -> moveCorner(corner, d / zoom.scale)
                            zoom.scale > 1.02f -> zoom.panBy(d, vp.width, vp.height)
                            else -> { sx += d.x; sy += d.y }
                        }
                        ch.consume()
                    }
                }
            }
        } while (ev.changes.any { it.pressed })

        if (!moved && !multi) {
            val now = System.currentTimeMillis()
            if (now - lastTapAt < 300L && (down.position - lastTapPos).getDistance() < tapNear) {
                lastTapAt = 0L
                zoom.toggleAt(down.position, vp.width, vp.height)
            } else {
                lastTapAt = now
                lastTapPos = down.position
            }
        } else if (!multi && corner < 0 && zoom.scale <= 1.02f && abs(sx) > swipeMin && abs(sx) > 2 * abs(sy)) {
            onSwipe(if (sx < 0) 1 else -1)
        }
    }
}
