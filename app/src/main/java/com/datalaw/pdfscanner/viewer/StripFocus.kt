package com.datalaw.pdfscanner.viewer

/**
 * The maths of zooming around a finger in the strip: pages one under another with a gap between them. [h] is the height in px of page i at zoom z.
 * All positions are in px from the top of the strip.
 */
internal object StripFocus {
    /** The position of the top edge of the screen, from the first visible page and how far that page is scrolled. */
    fun top(first: Int, firstOffset: Int, z: Float, gap: Float, h: (Int, Float) -> Float): Float {
        var y = firstOffset.toFloat()
        for (j in 0 until first) y += h(j, z) + gap
        return y
    }

    /** The page under position [y], and how far down that page it is (0..1). */
    fun pointAt(y: Float, z: Float, n: Int, gap: Float, h: (Int, Float) -> Float): Pair<Int, Float> {
        var p = 0
        var rest = y
        while (p < n - 1 && rest > h(p, z)) { rest -= h(p, z) + gap; p++ }
        return p to (rest / h(p, z)).coerceIn(0f, 1f)
    }

    /** Where that point of that page is at zoom [z]. */
    fun yOf(page: Int, fraction: Float, z: Float, gap: Float, h: (Int, Float) -> Float): Float {
        var y = fraction * h(page, z)
        for (j in 0 until page) y += h(j, z) + gap
        return y
    }

    /** The first visible page and how far it is scrolled, for a screen whose top edge is at [y]. */
    fun itemAt(y: Float, z: Float, n: Int, gap: Float, h: (Int, Float) -> Float): Pair<Int, Float> {
        var idx = 0
        var off = y
        while (idx < n - 1 && off >= h(idx, z) + gap) { off -= h(idx, z) + gap; idx++ }
        return idx to off.coerceAtLeast(0f)
    }
}
