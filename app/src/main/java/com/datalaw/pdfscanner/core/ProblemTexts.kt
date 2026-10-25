package com.datalaw.pdfscanner.core

import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.imaging.ShotProblem

/** The live hint in the camera. [sheet] = a sheet of paper (A4, A5), false = an item (card, passport, receipt). */
fun ShotProblem.hint(sheet: Boolean): UiText = tx(
    when (this) {
        ShotProblem.NoSheet -> if (sheet) R.string.qs_nosheet else R.string.sc_nosheet
        ShotProblem.Cut -> if (sheet) R.string.qs_cut else R.string.sc_cut
    },
)

/** The question after the shot. */
fun ShotProblem.ask(sheet: Boolean): UiText = tx(
    when (this) {
        ShotProblem.NoSheet -> if (sheet) R.string.qs_nosheet_ask else R.string.sc_nosheet_ask
        ShotProblem.Cut -> if (sheet) R.string.qs_cut_ask else R.string.sc_cut_ask
    },
)
