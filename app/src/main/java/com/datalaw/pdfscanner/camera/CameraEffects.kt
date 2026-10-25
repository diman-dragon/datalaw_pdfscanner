package com.datalaw.pdfscanner.camera

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal enum class FocusState { Idle, Seeking, Locked, NoDetail }

/**
 * Focus: locked on the item when the phone is steady; again after the phone has moved or the item has slid elsewhere.
 * [center] = where the item is (in pixels of the preview), [width] = the width of the preview, [focusAt] = true when the camera locked there.
 */
@Composable
internal fun SmartFocusEffect(
    ready: Boolean,
    busy: () -> Boolean,
    steady: () -> Boolean,
    center: () -> Offset,
    width: () -> Int,
    focusAt: suspend (Float, Float) -> Boolean,
    onState: (FocusState) -> Unit,
) {
    val busyNow by rememberUpdatedState(busy)
    val steadyNow by rememberUpdatedState(steady)
    val centerNow by rememberUpdatedState(center)
    val widthNow by rememberUpdatedState(width)
    val focusNow by rememberUpdatedState(focusAt)
    val stateNow by rememberUpdatedState(onState)
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        var moved = true
        var steadyTicks = 0
        var at: Offset? = null
        while (isActive) {
            delay(300)
            if (busyNow()) continue
            val c = centerNow()
            if (steadyNow()) steadyTicks++ else { steadyTicks = 0; moved = true }
            val was = at
            if (was != null && (c - was).getDistance() > 0.12f * widthNow()) moved = true
            if (moved && steadyTicks >= 3 && widthNow() > 0) {
                stateNow(FocusState.Seeking)
                stateNow(if (focusNow(c.x, c.y)) FocusState.Locked else FocusState.NoDetail)
                at = c
                moved = false
            }
        }
    }
}

/**
 * Full auto: when the item is seen whole, fits the view, is still and in focus for a moment ([canShoot]), the shot is made by itself.
 * The next one waits until the item has left the view or another one has come. [onHold] = how far the wait is, 0..1.
 */
@Composable
internal fun AutoShootEffect(enabled: Boolean, sheet: () -> List<Offset>?, canShoot: () -> Boolean, onHold: (Float) -> Unit, shoot: () -> Unit) {
    val sheetNow by rememberUpdatedState(sheet)
    val canNow by rememberUpdatedState(canShoot)
    val holdNow by rememberUpdatedState(onHold)
    val shootNow by rememberUpdatedState(shoot)
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var anchor: List<Offset>? = null
        var since = 0L
        var armed = true
        var lastShot = 0L
        while (isActive) {
            delay(100)
            val q = sheetNow()
            val now = SystemClock.elapsedRealtime()
            if (q == null) { anchor = null; holdNow(0f); armed = true; continue }
            val ref = anchor
            if (ref == null || q.indices.any { (q[it] - ref[it]).getDistance() > 0.02f }) {
                anchor = q
                since = now
                if (now - lastShot > 2000) armed = true
            }
            val held = if (canNow() && armed) now - since else 0L
            holdNow((held / 1200f).coerceIn(0f, 1f))
            if (held >= 1200) { armed = false; lastShot = now; holdNow(0f); shootNow() }
        }
    }
}
