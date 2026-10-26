package com.datalaw.pdfscanner.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Two fingers change the zoom around the middle between them; one finger is left to the scrolling. */
internal fun Modifier.pinchZoom(get: () -> Float, set: (Float, Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) {
                    set(get() * z, ev.calculateCentroid())
                    ev.changes.forEach { it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

/** A double tap zooms in at the place tapped, and the next one returns to the normal size. */
internal fun Modifier.doubleTapZoom(onTap: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { onTap(it) })
}
