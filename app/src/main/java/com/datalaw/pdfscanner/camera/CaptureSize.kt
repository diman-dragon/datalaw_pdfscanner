package com.datalaw.pdfscanner.camera

/**
 * How many pixels the photo is asked of the camera, by the PDF quality chosen: a bigger photo only helps when the page can use it, and it costs time and memory.
 * Normal = up to ~16 MP (150 / 300 dpi: an A4 page at 300 dpi is 8.7 MP); Large = up to ~27 MP (Auto); Max = everything the camera gives (600 dpi: 35 MP of page).
 */
enum class CaptureSize {
    Normal, Large, Max;

    companion object {
        fun forDpi(dpi: Int): CaptureSize = when (dpi) {
            0 -> Large
            600 -> Max
            else -> Normal
        }
    }
}
