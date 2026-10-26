package com.datalaw.pdfscanner.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.CamUi
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.core.hint
import com.datalaw.pdfscanner.imaging.ShotProblem

/** The colour of the frame: red = the sheet goes past the view, green = focus is locked, amber = nothing to focus on, white = nothing to tell. */
internal fun frameColorOf(bad: Boolean, focus: FocusState): Color = when {
    bad -> Color(0xFFFF5A4D)
    focus == FocusState.Locked -> Color(0xFF4CD964)
    focus == FocusState.NoDetail -> Color(0xFFFFB020)
    else -> Color.White
}

/** The one line under the picture: the most important thing the person has to know right now (a failure first, the quiet hint of the mode last). */
@Composable
internal fun cameraHint(failed: Boolean, ui: CamUi, hold: Float, problem: ShotProblem?, sheetLike: Boolean, focus: FocusState, smartAuto: Boolean): String = when {
    failed -> stringResource(R.string.cam_unavailable)
    ui.status != null -> ui.status!!.get()
    hold > 0.05f -> stringResource(R.string.cam_hold)
    problem != null -> problem.hint(sheetLike).get()
    focus == FocusState.Seeking -> stringResource(R.string.qs_focusing)
    focus == FocusState.Locked -> stringResource(R.string.qs_focuslock)
    focus == FocusState.NoDetail -> stringResource(R.string.qs_nodetail)
    smartAuto -> (ui.autoHint ?: ui.hint).get()
    else -> ui.hint.get()
}
