package com.datalaw.pdfscanner.scan

import androidx.annotation.StringRes
import com.datalaw.pdfscanner.R

/** Video = a page shape that follows the phone (A4 held landscape is turned), Land = an item shown landscape, Port = portrait. */
enum class Orient { Video, Land, Port }

class ScanType(
    val id: String,
    val mm: Pair<Double, Double>,
    val orient: Orient,
    @StringRes val nameRes: Int,
    val sides: Int = 1,
    val parts: Boolean = false,
    @StringRes val examplesRes: Int? = null,
    /** Full auto: no size to choose; whatever the camera finds is fitted to the A4 sheet and shot by itself. */
    val free: Boolean = false,
)

object ScanTypes {
    val all = listOf(
        ScanType("a4", 210.0 to 297.0, Orient.Video, R.string.st_a4),
        ScanType("a5", 148.0 to 210.0, Orient.Video, R.string.st_a5),
        ScanType("id", 85.6 to 54.0, Orient.Land, R.string.st_id, sides = 2, examplesRes = R.string.st_id_eg),
        ScanType("card", 90.0 to 50.0, Orient.Land, R.string.st_card),
        ScanType("passport", 176.0 to 125.0, Orient.Land, R.string.st_passport),
        ScanType("receipt", 80.0 to 200.0, Orient.Port, R.string.st_receipt, parts = true),
        ScanType("auto", 210.0 to 297.0, Orient.Video, R.string.st_auto, free = true),
    )

    fun byId(id: String): ScanType = all.firstOrNull { it.id == id } ?: all.first()

    /** What the "Auto" switch scans: whatever the camera finds, fitted to A4 and shot by itself. */
    val auto: ScanType = all.first { it.free }

    /** The size chosen for the "Frame" switch (a stored "auto" from an older version means A4). */
    fun preset(id: String): ScanType = all.firstOrNull { it.id == id && !it.free } ?: all.first { !it.free }

    /** Real size (w, h) of the item in the picture: A4 / A5 held landscape swap. */
    fun itemMm(t: ScanType, land: Boolean): Pair<Double, Double> =
        if (t.orient == Orient.Video && land) t.mm.second to t.mm.first else t.mm
}
