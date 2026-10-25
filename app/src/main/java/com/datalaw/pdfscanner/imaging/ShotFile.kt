package com.datalaw.pdfscanner.imaging

import androidx.compose.ui.geometry.Offset
import java.io.File

/**
 * A photo from the camera: the original JPEG, the frame it was made for ([crop] = x0, y0, x1, y1 as 0..1 of the upright photo,
 * null when the photo does not have the shape of the preview: the whole photo is used) and the orientation of the phone.
 * [quad]: Auto mode only, the corners of the item found in the photo (TL, TR, BR, BL as 0..1, turned to the shape of the item); the item is straightened from them.
 * [mm]: the size the item is put at on the sheet when it is not the size of its type (Full auto: it is fitted to the sheet).
 */
class ShotFile(val file: File, val crop: FloatArray?, val vr: Float, val land: Boolean, val quad: List<Offset>? = null, val mm: Pair<Double, Double>? = null)
