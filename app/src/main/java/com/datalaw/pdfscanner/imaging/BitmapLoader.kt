package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Photos from disk: decoded at the size that is needed (never bigger), turned upright by EXIF. */
object BitmapLoader {
    /** A big bitmap of this heap is only decoded when that much is free (a full-resolution photo needs ~50 MB). */
    private fun hasRoom(needBytes: Long): Boolean = try {
        val rm = android.app.ActivityManager.getRuntimeMemory()
        val max = Runtime.getRuntime().maxMemory()
        val used = max - Runtime.getRuntime().freeMemory()
        val slack = if (rm > max) rm - max else 0L // largeHeap: the extra is there before the heap grows
        max - used + slack > needBytes
    } catch (e: Throwable) {
        true
    }

    private fun orientation(f: File): Int = try {
        ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun matrixFor(o: Int): Matrix? {
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }

    /** Width and height of the photo as it looks upright (no pixels decoded). */
    fun uprightSize(f: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        val swap = when (orientation(f)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
            else -> false
        }
        return if (swap) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    /** Upright bitmap whose long side is at least [longSide] (when the photo has that many pixels) and at most twice that. */
    fun load(f: File, longSide: Int): Bitmap {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= longSide) sample *= 2
        // never decode more than the heap can take: keep raising the sample until there is room (an OOM here would kill the app)
        while (maxOf(b.outWidth, b.outHeight) > longSide && !hasRoom((b.outWidth.toLong() / sample) * (b.outHeight / sample) * 4)) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeFile(f.absolutePath, o) ?: throw java.io.IOException("cannot decode photo")
        val m = matrixFor(orientation(f)) ?: return raw
        val up = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (up !== raw) raw.recycle()
        return up
    }

    /** The same bitmap when it already fits, otherwise a smaller copy with the long side [maxSide]. */
    fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= maxSide) return b
        val k = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(b, Math.round(b.width * k).coerceAtLeast(1), Math.round(b.height * k).coerceAtLeast(1), true)
    }

    /** A copy turned by [degrees] clockwise (multiples of 90). */
    fun rotate(b: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return b
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}
