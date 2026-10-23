package com.datalaw.pdfscanner.pdf

/**
 * PDF quality: [side] = the long side of a page in px (A4: 11.69 in; 600 dpi would be 7000 px, capped for phone memory),
 * [budget] = bytes per page. Pixels are never invented: a page is never enlarged beyond what the camera captured.
 */
data class PdfTarget(val side: Int, val budget: Int) {
    companion object {
        fun forDpi(dpi: Int): PdfTarget = when (dpi) {
            300 -> PdfTarget(3508, 800_000)
            600 -> PdfTarget(4800, 2_200_000)
            else -> PdfTarget(1754, 300_000)
        }
    }
}
