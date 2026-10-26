package com.datalaw.pdfscanner.imaging

/**
 * How a page looks. Original = as photographed; Auto = uneven light and soft shadows removed, text crisp;
 * Clean = also the folds and creases of the sheet, the paper pure white; Gray = Clean in grey; Bw = black text on white (for text documents).
 */
enum class Look { Original, Auto, Clean, Gray, Bw }
