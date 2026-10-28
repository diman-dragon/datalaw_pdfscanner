package com.datalaw.pdfscanner.pdf

/**
 * PDF quality: [side] = the long side of a page in px, [budget] = bytes per page. Pixels are never enlarged beyond what the camera captured.
 * A4 is 11.69 in long: 150 dpi = 1754 px, 300 dpi = 3508 px, 600 dpi = 7016 px (35 MP); Auto (0) = as much as the camera gives, up to 6000 px.
 * What the phone cannot carry in memory is lowered by the loader by itself, never by a crash.
 */
data class PdfTarget(val side: Int, val budget: Int) {
    companion object {
        fun forDpi(dpi: Int): PdfTarget = when (dpi) {
            0 -> PdfTarget(6000, 6_000_000)
            300 -> PdfTarget(3508, 2_200_000)
            600 -> PdfTarget(7016, 9_000_000)
            else -> PdfTarget(1754, 700_000)
        }
    }
}
