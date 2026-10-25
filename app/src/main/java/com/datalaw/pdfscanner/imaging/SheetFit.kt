package com.datalaw.pdfscanner.imaging

import androidx.compose.ui.geometry.Offset
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min

/** Where a found sheet is relative to the frame and to the picture. Corners: TL, TR, BR, BL as 0..1 of the picture. */
object SheetFit {
    private const val EDGE_TOL = 0.012f

    /** The box around the corners as x0, y0, x1, y1 (0..1). */
    fun bbox(q: List<Offset>): FloatArray = floatArrayOf(
        q.minOf { it.x }.coerceIn(0f, 1f), q.minOf { it.y }.coerceIn(0f, 1f), q.maxOf { it.x }.coerceIn(0f, 1f), q.maxOf { it.y }.coerceIn(0f, 1f),
    )

    /** True when a corner sits on the border of the picture: the camera view cuts the sheet off. */
    fun touchesEdge(q: List<Offset>): Boolean =
        q.any { it.x < EDGE_TOL || it.x > 1 - EDGE_TOL || it.y < EDGE_TOL || it.y > 1 - EDGE_TOL }

    /** A believable sheet: every corner near a right angle (perspective allowed) and not a sliver. [aspect] = picture width / height. */
    fun plausible(q: List<Offset>, aspect: Float, checkRatio: Boolean = true): Boolean {
        if (q.size != 4) return false
        val p = q.map { Offset(it.x * aspect, it.y) }
        for (i in 0..3) {
            val a = p[(i + 3) % 4] - p[i]
            val b = p[(i + 1) % 4] - p[i]
            val la = a.getDistance()
            val lb = b.getDistance()
            if (la < 1e-4f || lb < 1e-4f) return false
            val cos = ((a.x * b.x + a.y * b.y) / (la * lb)).coerceIn(-1f, 1f)
            val deg = Math.toDegrees(acos(cos.toDouble()))
            if (deg < 55 || deg > 125) return false
        }
        val w = ((p[1] - p[0]).getDistance() + (p[2] - p[3]).getDistance()) / 2
        val h = ((p[3] - p[0]).getDistance() + (p[2] - p[1]).getDistance()) / 2
        return !checkRatio || min(w, h) / max(w, h) >= 0.3f
    }
}
