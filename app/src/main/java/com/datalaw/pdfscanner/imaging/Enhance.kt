package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * The looks of a page: plain image processing (no neural network), all of it flat-field correction. The brightness of the paper is estimated on a small copy
 * (the max filter drops the text) and divided out, so the shadow and the uneven light go. For the stronger looks a median filter also drops thin light or dark
 * lines from that estimate: the fold of the sheet does not get into the paper and disappears with it.
 */
object Enhance {
    private const val SHARP = 0.4 // gentle unsharp mask

    private enum class Out { Colour, Gray, Bw }

    /** [look] applied to [src]; the result is a new bitmap (Original returns [src] itself). [src] is not recycled. */
    fun apply(src: Bitmap, look: Look): Bitmap = when (look) {
        Look.Original -> src
        Look.Auto -> process(src, 0.9, false, Out.Colour)   // a part of the shadow stays: it looks like a photo of paper
        Look.Clean -> process(src, 1.0, true, Out.Colour)   // paper pure white, no shadows, no folds
        Look.Gray -> process(src, 1.0, true, Out.Gray)
        Look.Bw -> process(src, 1.0, true, Out.Bw)
    }

    private fun process(src: Bitmap, fix: Double, folds: Boolean, out: Out): Bitmap {
        Cv.ensure()
        val rgb = Cv.rgb(src)
        val small = Mat()
        val soft = Mat()
        val paper = Mat()
        val blur = Mat()
        val gray = Mat()
        val bin = Mat()
        var dk: Mat? = null
        try {
            val k = minOf(1.0, 160.0 / maxOf(rgb.cols(), rgb.rows()))
            Imgproc.resize(rgb, small, Size(Math.round(rgb.cols() * k).toDouble().coerceAtLeast(8.0), Math.round(rgb.rows() * k).toDouble().coerceAtLeast(8.0)), 0.0, 0.0, Imgproc.INTER_AREA)
            dk = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(small, small, dk)
            val estimate = if (folds) { Imgproc.medianBlur(small, soft, 9); soft } else small
            Imgproc.GaussianBlur(estimate, estimate, Size(0.0, 0.0), 3.0)
            Imgproc.resize(estimate, paper, Size(rgb.cols().toDouble(), rgb.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
            Core.max(paper, Scalar(8.0, 8.0, 8.0), paper)
            // paper' = fix * paper + (1 - fix) * 255
            Core.addWeighted(paper, fix, paper, 0.0, 255.0 * (1 - fix), paper)
            Core.divide(rgb, paper, rgb, 255.0)
            when (out) {
                Out.Colour -> sharpen(rgb, blur)
                Out.Gray -> {
                    Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
                    sharpen(gray, blur)
                    Imgproc.cvtColor(gray, rgb, Imgproc.COLOR_GRAY2RGB)
                }
                Out.Bw -> {
                    Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
                    val block = (minOf(gray.cols(), gray.rows()) / 30).coerceIn(15, 101).let { if (it % 2 == 0) it + 1 else it }
                    Imgproc.adaptiveThreshold(gray, bin, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 10.0)
                    Imgproc.cvtColor(bin, rgb, Imgproc.COLOR_GRAY2RGB)
                }
            }
            return Cv.bitmap(rgb)
        } finally {
            rgb.release()
            small.release()
            soft.release()
            paper.release()
            blur.release()
            gray.release()
            bin.release()
            dk?.release()
        }
    }

    private fun sharpen(m: Mat, blur: Mat) {
        Imgproc.GaussianBlur(m, blur, Size(0.0, 0.0), 1.2)
        Core.addWeighted(m, 1 + SHARP, blur, -SHARP, 0.0, m)
    }
}
