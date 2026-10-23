package com.datalaw.pdfscanner.pdf

/** A recognised word: the box is 0..1 of the page (y down). */
data class PdfWord(val t: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** One page of a PDF to write: an RGB JPEG that fills an A4-wide page, plus the optional invisible (searchable) text. */
class PdfPage(val jpeg: ByteArray, val w: Int, val h: Int, val words: List<PdfWord> = emptyList()) {
    fun withWords(w: List<PdfWord>) = PdfPage(jpeg, this.w, h, w)
}
