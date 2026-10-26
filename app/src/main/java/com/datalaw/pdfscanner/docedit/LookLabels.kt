package com.datalaw.pdfscanner.docedit

import androidx.annotation.StringRes
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.imaging.Look

/** The name of a look on the switch (the low layer knows nothing of strings). */
@StringRes
internal fun Look.labelRes(): Int = when (this) {
    Look.Original -> R.string.fx_color
    Look.Auto -> R.string.fx_auto
    Look.Clean -> R.string.fx_clean
    Look.Gray -> R.string.fx_gray
    Look.Bw -> R.string.fx_bw
}
