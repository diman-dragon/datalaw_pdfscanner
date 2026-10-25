package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.ShotCheck
import com.datalaw.pdfscanner.imaging.ShotProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShotCheckTest {
    private fun box(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1))

    @Test fun nothingFound() = assertEquals(ShotProblem.NoSheet, ShotCheck.problem(null))

    @Test fun sheetCutByTheView() = assertEquals(ShotProblem.Cut, ShotCheck.problem(box(0.005f, 0.2f, 0.8f, 0.8f)))

    @Test fun sheetFits() = assertNull(ShotCheck.problem(box(0.2f, 0.2f, 0.8f, 0.8f)))
}
