package com.datalaw.pdfscanner.scan

/** Full auto: the size (mm) a found item is put at on the A4 sheet. */
object ItemFit {
    /** [aspect] = width / height of the item. A page (A4, A5, letter...) fills the sheet; anything else is fitted into the sheet, its shape kept. */
    fun mm(aspect: Double): Pair<Double, Double> {
        val portrait = aspect < 1
        val r = if (portrait) aspect else 1 / aspect // short side / long side
        val long = if (Math.abs(r - 210.0 / 297.0) < 0.07) 297.0 else minOf(277.0, 190.0 / r)
        val short = if (long == 297.0) 210.0 else long * r
        return if (portrait) short to long else long to short
    }
}
