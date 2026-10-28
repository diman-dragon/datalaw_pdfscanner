package com.datalaw.pdfscanner.pdf

/** One page of a PDF to write: an RGB JPEG that fills an A4-wide page ([w] x [h] px). */
class PdfPage(val jpeg: ByteArray, val w: Int, val h: Int)
