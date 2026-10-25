package com.datalaw.pdfscanner.core

import android.util.Log
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.FolderMissingException
import java.io.IOException

/** What the user is told when something failed: a plain reason in their language. The technical text goes to the log, not to the screen. */
fun Throwable.userReason(): UiText {
    Log.e("PdfScanner", "failed", this)
    return tx(
        when (this) {
            is OutOfMemoryError -> R.string.err_memory
            is FolderMissingException -> R.string.err_folder
            is IOException -> R.string.err_io
            else -> R.string.err_unknown
        },
    )
}
