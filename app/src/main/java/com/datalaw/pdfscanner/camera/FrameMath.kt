package com.datalaw.pdfscanner.camera

/** The frame in the preview: centred, as big as possible (92% of one side), of the given width/height ratio; x0, y0, x1, y1 as 0..1. */
object FrameMath {
    /** [aspect] = width / height of the preview. */
    fun frame(ratio: Float, aspect: Float): FloatArray {
        var fw = 0.92f
        var fh = fw * aspect / ratio
        if (fh > 0.92f) {
            fh = 0.92f
            fw = fh * ratio / aspect
        }
        return floatArrayOf((1 - fw) / 2, (1 - fh) / 2, (1 + fw) / 2, (1 + fh) / 2)
    }
}
