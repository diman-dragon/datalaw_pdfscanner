package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Straightens a photographed sheet (perspective) to a flat page with the TRUE proportions of the sheet. */
object PageWarp {
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /**
     * Width / height of the real rectangle that was photographed as the quad q (TL, TR, BR, BL), from the principal point (cx, cy)
     * (Zhang & He: the focal length is recovered from the two vanishing directions; a poorly conditioned quad falls back to a typical camera).
     * null when it cannot be told.
     */
    fun trueAspect(q: List<PointF>, cx: Double, cy: Double): Double? {
        fun m(p: PointF) = doubleArrayOf(p.x - cx, p.y - cy, 1.0)
        val m1 = m(q[0]); val m2 = m(q[1]); val m3 = m(q[3]); val m4 = m(q[2])
        val k2 = dot(cross(m1, m4), m3) / dot(cross(m2, m4), m3)
        val k3 = dot(cross(m1, m4), m2) / dot(cross(m3, m4), m2)
        if (!k2.isFinite() || !k3.isFinite()) return null
        val n2 = doubleArrayOf(k2 * m2[0] - m1[0], k2 * m2[1] - m1[1], k2 * m2[2] - m1[2])
        val n3 = doubleArrayOf(k3 * m3[0] - m1[0], k3 * m3[1] - m1[1], k3 * m3[2] - m1[2])
        val big = max(cx, cy) * 2
        val prior = 0.75 * big
        var f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / (n2[2] * n3[2])
        var f = sqrt(f2)
        val wellPosed = abs(k2 - 1) > 0.05 && abs(k3 - 1) > 0.05
        if (!wellPosed || !(f2 > 0) || !f.isFinite() || f < 0.35 * big || f > 2 * big) {
            f2 = prior * prior
            f = prior
        }
        fun len2(n: DoubleArray) = (n[0] * n[0] + n[1] * n[1]) / f2 + n[2] * n[2]
        val r = sqrt(len2(n2) / len2(n3))
        return if (r.isFinite() && r > 0.2 && r < 5) r else null
    }

    /** Pixel size of the straightened page: the long side is [maxSide] at most, never more pixels than the photo has along the edges. */
    fun pageSize(q: List<PointF>, srcW: Int, srcH: Int, maxSide: Int): Pair<Int, Int> {
        val top = hypot((q[1].x - q[0].x).toDouble(), (q[1].y - q[0].y).toDouble())
        val bot = hypot((q[2].x - q[3].x).toDouble(), (q[2].y - q[3].y).toDouble())
        val lef = hypot((q[3].x - q[0].x).toDouble(), (q[3].y - q[0].y).toDouble())
        val rig = hypot((q[2].x - q[1].x).toDouble(), (q[2].y - q[1].y).toDouble())
        val wMax = max(top, bot)
        val hMax = max(lef, rig)
        val a = trueAspect(q, srcW / 2.0, srcH / 2.0) ?: (wMax / max(1.0, hMax))
        // use as many source pixels as the longer edge pair offers, then the true proportion
        var w: Double
        var h: Double
        if (wMax / a >= hMax) { w = wMax; h = wMax / a } else { h = hMax; w = hMax * a }
        val k = maxSide / max(w, h)
        if (k < 1) { w *= k; h *= k }
        return w.roundToInt().coerceAtLeast(16) to h.roundToInt().coerceAtLeast(16)
    }

    fun warp(src: Bitmap, q: List<PointF>, maxSide: Int): Bitmap {
        val (w, h) = pageSize(q, src.width, src.height, minOf(4000, maxSide))
        return warpTo(src, q, w, h)
    }

    /** The quad q (TL, TR, BR, BL) of [src] straightened to exactly w x h pixels. */
    fun warpTo(src: Bitmap, q: List<PointF>, w: Int, h: Int): Bitmap {
        Cv.ensure()
        var srcMat: Mat? = null
        var from: MatOfPoint2f? = null
        var to: MatOfPoint2f? = null
        var m: Mat? = null
        var dst: Mat? = null
        var blur: Mat? = null
        try {
            srcMat = Cv.rgb(src)
            from = MatOfPoint2f(*q.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
            to = MatOfPoint2f(Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0))
            m = Imgproc.getPerspectiveTransform(from, to)
            dst = Mat()
            // cubic, not linear: linear blurs the strokes of small text when a 16 MP shot is brought to the size of the page
            Imgproc.warpPerspective(srcMat, dst, m, Size(w.toDouble(), h.toDouble()), Imgproc.INTER_CUBIC)
            // a light unsharp mask gives back what any resampling takes
            blur = Mat()
            Imgproc.GaussianBlur(dst, blur, Size(0.0, 0.0), 1.0)
            Core.addWeighted(dst, 1.25, blur, -0.25, 0.0, dst)
            return Cv.bitmap(dst)
        } finally {
            srcMat?.release()
            dst?.release()
            blur?.release()
            m?.release()
            from?.release()
            to?.release()
        }
    }
}
