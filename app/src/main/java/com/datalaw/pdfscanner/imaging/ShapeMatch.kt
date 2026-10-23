package com.datalaw.pdfscanner.imaging

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max

/** Is what was found the thing that is scanned: compares the REAL shape of the quad (perspective removed) with the shape of the item. */
object ShapeMatch {
    /** Width / height of the real rectangle seen as the quad [q] (0..1 corners) in a photo of w x h px; the ratio of the edge lengths when it cannot be told. */
    fun aspectOf(q: List<Offset>, w: Int, h: Int): Double {
        val p = q.map { PointF(it.x * w, it.y * h) }
        PageWarp.trueAspect(p, w / 2.0, h / 2.0)?.let { return it }
        val top = hypot((p[1].x - p[0].x).toDouble(), (p[1].y - p[0].y).toDouble())
        val left = hypot((p[3].x - p[0].x).toDouble(), (p[3].y - p[0].y).toDouble())
        return top / max(1.0, left)
    }

    /** True when the quad can be an item of the shape [expected] (width / height), held either way; [tol] = how far the ratio may be off. */
    fun matches(q: List<Offset>, w: Int, h: Int, expected: Double, tol: Double = 0.3): Boolean {
        val a = aspectOf(q, w, h)
        val seen = max(a, 1 / a)
        val want = max(expected, 1 / expected)
        val k = seen / want
        return k >= 1 / (1 + tol) && k <= 1 + tol
    }
}
