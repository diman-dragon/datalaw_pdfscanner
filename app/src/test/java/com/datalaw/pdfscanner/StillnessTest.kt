package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.camera.Stillness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessTest {
    private fun ns(seconds: Double) = (seconds * 1_000_000_000).toLong()

    @Test fun aSlowShutterAsksForAHandThatHardlyMoves() {
        assertEquals(0.0126f, Stillness.limit(ns(1.0 / 30)), 0.001f)
    }

    @Test fun aFasterShutterForgivesMore() {
        assertEquals(0.042f, Stillness.limit(ns(1.0 / 100)), 0.002f)
        assertTrue(Stillness.limit(ns(1.0 / 100)) > Stillness.limit(ns(1.0 / 30)))
    }

    @Test fun theLimitStaysBetweenWhatAHandCanDoAndTheOldFixedValue() {
        assertEquals(Stillness.TIGHT, Stillness.limit(ns(2.0)), 0.0001f)   // a very slow shutter: the tightest limit
        assertEquals(0.105f, Stillness.limit(0L), 0.002f)                  // unknown shutter: counted as 1/250 s
        assertTrue(Stillness.limit(0L) <= Stillness.LOOSE)
    }
}
