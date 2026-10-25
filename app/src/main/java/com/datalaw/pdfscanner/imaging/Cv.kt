package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** OpenCV start-up and the Bitmap <-> Mat bridge (the engine works on 3-channel RGB Mats). */
object Cv {
    private val ok: Boolean by lazy { OpenCVLoader.initLocal() }

    fun ensure() {
        check(ok) { "OpenCV failed to initialise" }
    }

    fun rgb(b: Bitmap): Mat {
        ensure()
        val rgba = Mat()
        try {
            Utils.bitmapToMat(b, rgba)
            val rgb = Mat()
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            return rgb
        } finally {
            rgba.release()
        }
    }

    fun bitmap(rgb: Mat): Bitmap {
        val rgba = Mat()
        try {
            Imgproc.cvtColor(rgb, rgba, Imgproc.COLOR_RGB2RGBA)
            val b = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, b)
            return b
        } finally {
            rgba.release()
        }
    }
}
