package com.datalaw.pdfscanner.docedit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** A point of the page (0..1 of the page as it was shot) as it is seen on the picture turned by [k] quarter-turns clockwise. */
internal fun toView(o: Offset, k: Int): Offset = when (k) {
    1 -> Offset(1f - o.y, o.x)
    2 -> Offset(1f - o.x, 1f - o.y)
    3 -> Offset(o.y, 1f - o.x)
    else -> o
}

/** The way back of [toView]: a point touched on the turned picture, in the page as it was shot. */
internal fun fromView(o: Offset, k: Int): Offset = when (k) {
    1 -> Offset(o.y, 1f - o.x)
    2 -> Offset(1f - o.x, 1f - o.y)
    3 -> Offset(1f - o.y, o.x)
    else -> o
}

/** Percent of perspective distortion of the frame (null when small). */
internal fun tilt(q: List<Offset>, b: Bitmap?): Int? {
    if (b == null) return null
    fun d(a: Offset, c: Offset) = hypot((a.x - c.x) * b.width, (a.y - c.y) * b.height)
    val top = d(q[0], q[1]); val bot = d(q[3], q[2]); val lef = d(q[0], q[3]); val rig = d(q[1], q[2])
    val t = max(abs(top / max(bot, 1f) - 1f), abs(lef / max(rig, 1f) - 1f))
    return if (t > 0.15f) (t * 100).toInt() else null
}

/** Where the page lies in a view of [w] x [h] px: the biggest fit of a [bw] x [bh] picture that leaves [margin] px free on every side, centred. Returns left, top, width, height. */
internal fun fitInto(w: Float, h: Float, bw: Float, bh: Float, margin: Float): FloatArray {
    val fit = maxOf(minOf((w - 2 * margin) / bw, (h - 2 * margin) / bh), 0.01f)
    val dw = bw * fit
    val dh = bh * fit
    return floatArrayOf((w - dw) / 2, (h - dh) / 2, dw, dh)
}
