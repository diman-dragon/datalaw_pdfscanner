package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.ShotCheck
import com.datalaw.pdfscanner.imaging.ShotProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShotCheckTest {
    private fun box(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1))
    private val frame = floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f)

    @Test fun autoWithoutSheet() = assertEquals(ShotProblem.NoSheet, ShotCheck.problem(true, null, null))

    @Test fun autoSheetCutByTheView() = assertEquals(ShotProblem.Cut, ShotCheck.problem(true, box(0.005f, 0.2f, 0.8f, 0.8f), null))

    @Test fun autoSheetFits() = assertNull(ShotCheck.problem(true, box(0.2f, 0.2f, 0.8f, 0.8f), null))

    @Test fun frameSheetPastTheFrame() = assertEquals(ShotProblem.Outside, ShotCheck.problem(false, box(0.2f, 0.2f, 0.95f, 0.8f), frame))

    @Test fun frameSheetInside() = assertNull(ShotCheck.problem(false, box(0.2f, 0.2f, 0.8f, 0.8f), frame))

    @Test fun frameWithoutSheetIsNotAProblem() = assertNull(ShotCheck.problem(false, null, frame))

    @Test fun noFrameMeansTheWholePicture() = assertNull(ShotCheck.problem(false, box(0.2f, 0.2f, 0.8f, 0.8f), null))
}
