package com.datalaw.pdfscanner.camera

import com.datalaw.pdfscanner.ui.UiText
import kotlinx.coroutines.flow.StateFlow

/** What the camera screen shows (label in the frame, hint at the bottom, page counter, status such as "Saving…"). */
data class CamUi(
    val label: UiText,
    val hint: UiText,
    val badge: UiText? = null,
    val status: UiText? = null,
    val autoLabel: UiText? = null, // the label and the hint in Auto mode, when it differs
    val autoHint: UiText? = null,
)

/** The logic behind a camera screen (Quick scan, Scan): the screen only shoots and shows; the session decides what a shot means. */
interface CameraSession {
    val ui: StateFlow<CamUi>
    /** True = the camera shows the Frame / Auto switch and watches the sheet live. */
    val smart: Boolean get() = false
    /** The shape (width / height) of what is scanned, null = any sheet; and the smallest share of the picture it fills. */
    val expectRatio: Double? get() = null
    val minAreaFrac: Double get() = 0.2
    /** True = the thing is a sheet of paper (the texts say "sheet"), false = an item (card, passport, receipt). */
    val sheetLike: Boolean get() = true
    fun frameRatio(land: Boolean): Float          // width / height of the frame
    suspend fun onShot(shot: ShotFile): Boolean   // true = the work is done, close the camera
    suspend fun canClose(): Boolean
    fun onClosed()
}
