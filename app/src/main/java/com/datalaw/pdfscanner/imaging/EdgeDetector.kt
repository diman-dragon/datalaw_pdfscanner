package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Finds the sheet in a photo: the four corners (TL, TR, BR, BL) in pixels of the bitmap. */
object EdgeDetector {
    /** The corners of the sheet, or null when no sheet-like shape is seen ([minAreaFrac] = the smallest share of the picture it may fill). */
    fun find(src: Bitmap, minAreaFrac: Double = 0.2): List<PointF>? {
        Cv.ensure()
        val k = minOf(1.0, 600.0 / maxOf(src.width, src.height))
        val sw = Math.round(src.width * k).toInt().coerceAtLeast(8)
        val sh = Math.round(src.height * k).toInt().coerceAtLeast(8)
        val small = if (k < 1.0) Bitmap.createScaledBitmap(src, sw, sh, true) else src
        // every Mat is released whatever happens: a leak here exhausts the native memory and the process dies
        val rgba = Mat()
        val gray = Mat()
        val closed = Mat()
        val edges = Mat()
        var quad: List<Point>? = null
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            rgba.release()

            // closing erases dark text and thin lines on the paper, the sheet stays one bright blob
            val ks = (maxOf(sw, sh) / 80).coerceAtLeast(5) or 1
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(ks.toDouble(), ks.toDouble()))
            Imgproc.morphologyEx(gray, closed, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()
            Imgproc.GaussianBlur(closed, closed, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(closed, edges, 30.0, 100.0)
            val dil = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, dil)
            dil.release()

            val minArea = sw.toDouble() * sh * minAreaFrac
            quad = largest(edges, minArea, false)
            if (quad == null) { // a bright sheet on a dark table: threshold
                val bw = Mat()
                try {
                    Imgproc.threshold(closed, bw, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                    quad = largest(bw, minArea, true)
                } finally {
                    bw.release()
                }
            }
        } finally {
            gray.release(); closed.release(); edges.release()
            if (small !== src) small.recycle()
        }

        return quad?.map { PointF((it.x / k).toFloat(), (it.y / k).toFloat()) }?.let { order(it) }
    }

    /** Like [find], but never null: nothing found = the whole photo with a small margin. */
    fun detect(src: Bitmap): List<PointF> = find(src) ?: run {
        val mx = src.width * 0.04f
        val my = src.height * 0.04f
        listOf(PointF(mx, my), PointF(src.width - mx, my), PointF(src.width - mx, src.height - my), PointF(mx, src.height - my))
    }

    /** The largest convex 4-gon among the contours, or the min-area rectangle of the largest one when [allowRect]. */
    private fun largest(bin: Mat, minArea: Double, allowRect: Boolean): List<Point>? {
        val contours = ArrayList<MatOfPoint>()
        val work = bin.clone()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(work, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        } finally {
            work.release()
            hierarchy.release()
        }
        var best: List<Point>? = null
        var bestArea = 0.0
        var big: MatOfPoint? = null
        var bigArea = 0.0
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < minArea) continue
            if (area > bigArea) { bigArea = area; big = c }
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray())) && area > bestArea) {
                bestArea = area
                best = approx.toList()
            }
            c2f.release(); approx.release()
        }
        if (best == null && allowRect && big != null) {
            val big2f = MatOfPoint2f(*big.toArray())
            try {
                val rr = Imgproc.minAreaRect(big2f)
                val p = Array(4) { Point() }
                rr.points(p)
                best = p.toList()
            } finally {
                big2f.release()
            }
        }
        contours.forEach { it.release() }
        return best
    }

    /** TL, TR, BR, BL */
    fun order(p: List<PointF>): List<PointF> {
        val tl = p.minByOrNull { it.x + it.y }!!
        val br = p.maxByOrNull { it.x + it.y }!!
        val tr = p.minByOrNull { it.y - it.x }!!
        val bl = p.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }
}
