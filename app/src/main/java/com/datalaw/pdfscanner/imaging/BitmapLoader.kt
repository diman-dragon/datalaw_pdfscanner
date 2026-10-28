package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Photos from disk: decoded at the size that is needed (never bigger), turned upright by EXIF. */
object BitmapLoader {
    /** Available heap memory before OutOfMemoryError in bytes. */
    fun availableMemory(): Long = try {
        val runtime = Runtime.getRuntime()
        val max = runtime.maxMemory()
        val used = runtime.totalMemory() - runtime.freeMemory()
        (max - used).coerceAtLeast(0L)
    } catch (e: Throwable) {
        0L
    }

    /** What the phone can still give, from the kernel (bitmaps live outside the Java heap, so the heap alone says nothing); -1 = unknown. */
    private fun deviceAvailable(): Long = try {
        java.io.File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemAvailable:") }?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.times(1024L) ?: -1L
        }
    } catch (e: Throwable) {
        -1L
    }

    /** A big bitmap is only decoded when it fits both the Java heap and half of what the phone has free (a 64 MP photo is 256 MB, and turning it needs a copy). */
    fun hasRoom(needBytes: Long, safetyBuffer: Long = 16 * 1024 * 1024L): Boolean = try {
        val dev = deviceAvailable()
        availableMemory() > (needBytes + safetyBuffer) && (dev < 0 || dev / 2 > needBytes)
    } catch (e: Throwable) {
        false
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
        while (maxOf(b.outWidth, b.outHeight) / sample > 600 && !hasRoom((b.outWidth.toLong() / sample) * (b.outHeight / sample) * 4 * 2)) {
            sample *= 2
        }
        var curSample = sample
        while (true) {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = curSample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val raw = BitmapFactory.decodeFile(f.absolutePath, o) ?: throw java.io.IOException("cannot decode photo")
                val m = matrixFor(orientation(f))
                if (m == null) return raw
                val up = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                if (up !== raw) raw.recycle()
                return up
            } catch (e: OutOfMemoryError) {
                System.gc()
                if (curSample >= 32) throw e
                curSample *= 2
            }
        }
    }

    /** The same bitmap when it already fits, otherwise a smaller copy with the long side [maxSide]. */
    fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= maxSide) return b
        val k = maxSide.toFloat() / long
        val targetW = Math.round(b.width * k).coerceAtLeast(1)
        val targetH = Math.round(b.height * k).coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(b, targetW, targetH, true)
        } catch (e: OutOfMemoryError) {
            System.gc()
            b
        }
    }

    /** A copy turned by [degrees] clockwise (multiples of 90). */
    fun rotate(b: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return b
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return try {
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } catch (e: OutOfMemoryError) {
            System.gc()
            b
        }
    }
}
