package com.datalaw.pdfscanner.docedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** The maths of the zoom of a page, with no screen (so it has a test). A point c of the page lies on the screen at c * scale + offset. */
internal object ZoomMath {
    const val MAX = 4f
    const val TAP = 2.5f

    /** The offset that keeps the page covering the whole view: never an empty edge. */
    fun clamp(offset: Offset, scale: Float, w: Float, h: Float) =
        Offset(offset.x.coerceIn(w * (1f - scale), 0f), offset.y.coerceIn(h * (1f - scale), 0f))

    /** Zoom to [to] around the screen point [focus] (the point stays under the finger), the fingers also moved by [pan]. Returns the new scale and offset. */
    fun zoomed(scale: Float, offset: Offset, to: Float, focus: Offset, pan: Offset, w: Float, h: Float): Pair<Float, Offset> {
        val s = to.coerceIn(1f, MAX)
        val c = (focus - offset) / scale
        return s to clamp(focus + pan - c * s, s, w, h)
    }
}

/** The zoom of the page that is being edited: the state the picture is drawn from and the gestures change. */
internal class EditorZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** A point of the screen as a point of the page (the page is not zoomed: its own pixels). */
    fun toPage(p: Offset): Offset = (p - offset) / scale

    fun zoomAt(to: Float, focus: Offset, pan: Offset, w: Float, h: Float) {
        val (s, o) = ZoomMath.zoomed(scale, offset, to, focus, pan, w, h)
        scale = s
        offset = o
    }

    fun panBy(d: Offset, w: Float, h: Float) { offset = ZoomMath.clamp(offset + d, scale, w, h) }

    /** A double tap: zoomed in at the place tapped, and the next one back to the whole page. */
    fun toggleAt(focus: Offset, w: Float, h: Float) = zoomAt(if (scale > 1.2f) 1f else ZoomMath.TAP, focus, Offset.Zero, w, h)
}
