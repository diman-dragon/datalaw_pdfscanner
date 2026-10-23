package com.datalaw.pdfscanner.camera

import com.datalaw.pdfscanner.ui.UiText
import kotlinx.coroutines.flow.StateFlow

/** What the camera screen shows (label in the frame, hint at the bottom, page counter, status such as "Saving…"). */
data class CamUi(val label: UiText, val hint: UiText, val badge: UiText? = null, val status: UiText? = null)

/** The logic behind a camera screen (Quick scan, Scan): the screen only shoots and shows; the session decides what a shot means. */
interface CameraSession {
    val ui: StateFlow<CamUi>
    fun frameRatio(land: Boolean): Float          // width / height of the frame
    suspend fun onShot(shot: ShotFile): Boolean   // true = the work is done, close the camera
    suspend fun canClose(): Boolean
    fun onClosed()
}
