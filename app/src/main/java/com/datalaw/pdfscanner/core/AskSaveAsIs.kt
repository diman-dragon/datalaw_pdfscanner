package com.datalaw.pdfscanner.core

import com.datalaw.pdfscanner.R

/** "Retake / Save as is" for a shot that has a problem (soft, the sheet past the frame...). True = save as is. */
suspend fun UiHost.askSaveAsIs(message: UiText): Boolean =
    askChoice(message, listOf("retake" to tx(R.string.qs_blur_retake), "save" to tx(R.string.qs_blur_save))) == "save"
