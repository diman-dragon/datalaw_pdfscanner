package com.datalaw.pdfscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.FolderPath

/** Where a folder is, as readable steps: ["Device storage", "Documents", "Scans"]. Null when no folder is chosen. */
@Composable
fun folderTrail(uri: String?): List<String>? {
    val p = uri?.let { FolderPath.of(it) } ?: return null
    val volume = stringResource(if (p.primary) R.string.set_internal else R.string.set_volume_sd)
    return listOf(volume) + p.path.split('/').filter { it.isNotEmpty() }
}

const val TRAIL_SEP = " › "
