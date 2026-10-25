package com.datalaw.pdfscanner.pdf

/**
 * PDF quality: [side] = the long side of a page in px (A4: 11.69 in; capped to 4000 px for phone memory stability),
 * [budget] = bytes per page. Pixels are never enlarged beyond what the camera captured.
 * dpi 0 = Auto: up to 4000 px on the long side.
 */
data class PdfTarget(val side: Int, val budget: Int) {
    companion object {
        fun forDpi(dpi: Int): PdfTarget = when (dpi) {
            0 -> PdfTarget(4000, 4_000_000)
            300 -> PdfTarget(3508, 800_000)
            600 -> PdfTarget(4000, 2_200_000)
            else -> PdfTarget(1754, 300_000)
        }
    }
}
