package com.datalaw.pdfscanner.imaging

import androidx.compose.ui.geometry.Offset

/** What can be wrong with the sheet in the camera or in a photo. */
enum class ShotProblem { NoSheet, Cut, Outside }

/** The one place that says whether a sheet sits right: the live outline in the camera and the photo after the shot use it both. */
object ShotCheck {
    private val whole = floatArrayOf(0f, 0f, 1f, 1f)

    /**
     * [auto] = Auto mode (the sheet is found, no frame); [quad] = what was found (null = nothing); [frame] = the frame as x0, y0, x1, y1 (null = the whole picture).
     * Auto: not found, or cut by the picture. Frame: past the frame (a sheet that is not seen is not a problem there).
     */
    fun problem(auto: Boolean, quad: List<Offset>?, frame: FloatArray?): ShotProblem? = when {
        auto && quad == null -> ShotProblem.NoSheet
        auto && quad != null && SheetFit.touchesEdge(quad) -> ShotProblem.Cut
        !auto && quad != null && SheetFit.outside(quad, frame ?: whole) -> ShotProblem.Outside
        else -> null
    }
}
