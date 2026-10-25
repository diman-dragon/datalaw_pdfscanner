package com.datalaw.pdfscanner.imaging

import androidx.compose.ui.geometry.Offset

/** What can be wrong with the sheet in the camera or in a photo (Auto mode). */
enum class ShotProblem { NoSheet, Cut }

/** The one place that says whether a found sheet sits right: the live outline in the camera and the photo after the shot use it both. Frame mode checks nothing. */
object ShotCheck {
    /** [quad] = what was found (null = nothing): not found, or cut by the picture. */
    fun problem(quad: List<Offset>?): ShotProblem? = when {
        quad == null -> ShotProblem.NoSheet
        SheetFit.touchesEdge(quad) -> ShotProblem.Cut
        else -> null
    }
}
