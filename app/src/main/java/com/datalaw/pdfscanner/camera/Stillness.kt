package com.datalaw.pdfscanner.camera

import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * How still the phone must be for a sharp shot. A blur is the turn of the phone during the exposure: the angle it turns (rad/s * shutter time) against the angle
 * one pixel of the photo looks at. So the limit is not one number: a slow shutter (the dark) asks for a hand that hardly moves, a fast one (light, flash) forgives much.
 */
internal object Stillness {
    private const val RAD_PER_PX = 0.00028f // the angle one pixel of a ~12 MP photo looks at (about 65 degrees across 4000 px)
    private const val BLUR_PX = 1.5f        // a blur of this many pixels is not seen
    const val LOOSE = 0.15f                 // the old fixed limit: enough when the flash or a fast shutter freezes the shot
    const val TIGHT = 0.01f                 // no hand holds better than this
    private const val CALM_MS = 120L        // the phone must stay under the limit this long
    private const val MAX_WAIT_MS = 3000L   // after this the shot is taken anyway (the blur check still asks)

    /** The fastest turn (rad/s) at which a shutter of [shutterNs] nanoseconds still gives less than [BLUR_PX] of blur. */
    fun limit(shutterNs: Long): Float {
        val t = (shutterNs / 1_000_000_000f).coerceAtLeast(1f / 250f)
        return (BLUR_PX * RAD_PER_PX / t).coerceIn(TIGHT, LOOSE)
    }

    /**
     * Waits until the phone has been under the limit for a moment, so that the photo is taken in a calm. The limit loosens by half every second, so that a hand that
     * cannot be calmer than this is not waited for forever. [settling] tells the screen to say "hold still".
     */
    suspend fun await(rate: () -> Float, shutterNs: () -> Long, flash: Boolean, settling: (Boolean) -> Unit) {
        val start = SystemClock.elapsedRealtime()
        var calmSince = -1L
        settling(true)
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val waited = now - start
                val base = if (flash) LOOSE else limit(shutterNs())
                val limit = base * (1f + waited / 2000f)
                if (rate() < limit) {
                    if (calmSince < 0) calmSince = now
                    if (now - calmSince >= CALM_MS) return
                } else {
                    calmSince = -1L
                }
                if (waited >= MAX_WAIT_MS) return
                delay(25)
            }
        } finally {
            settling(false)
        }
    }
}
