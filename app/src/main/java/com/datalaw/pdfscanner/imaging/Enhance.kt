package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** "Auto": flat-field correction. The paper brightness is estimated on a small copy (the max filter drops the text), then divided out. */
object Enhance {
    private const val FIX = 0.9   // how much of the shadow is removed (1 = all, the paper becomes flat white)
    private const val SHARP = 0.4 // gentle unsharp mask

    fun auto(src: Bitmap): Bitmap {
        Cv.ensure()
        val rgb = Cv.rgb(src)
        val k = minOf(1.0, 160.0 / maxOf(rgb.cols(), rgb.rows()))
        val small = Mat()
        Imgproc.resize(rgb, small, Size(Math.round(rgb.cols() * k).toDouble().coerceAtLeast(8.0), Math.round(rgb.rows() * k).toDouble().coerceAtLeast(8.0)), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)))
        Imgproc.GaussianBlur(small, small, Size(0.0, 0.0), 3.0)
        val paper = Mat()
        Imgproc.resize(small, paper, Size(rgb.cols().toDouble(), rgb.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        small.release()
        Core.max(paper, Scalar(8.0, 8.0, 8.0), paper)
        // a part of the shadow stays: paper' = FIX * paper + (1 - FIX) * 255
        Core.addWeighted(paper, FIX, paper, 0.0, 255.0 * (1 - FIX), paper)
        Core.divide(rgb, paper, rgb, 255.0)
        paper.release()
        val blur = Mat()
        Imgproc.GaussianBlur(rgb, blur, Size(0.0, 0.0), 1.2)
        Core.addWeighted(rgb, 1 + SHARP, blur, -SHARP, 0.0, rgb)
        blur.release()
        val out = Cv.bitmap(rgb)
        rgb.release()
        return out
    }
}
