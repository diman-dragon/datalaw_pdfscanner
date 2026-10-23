package com.datalaw.pdfscanner.data

import android.net.Uri
import android.provider.DocumentsContract

/** A readable place of a tree uri: the volume ("primary" = device storage) and the path inside it. */
data class FolderPath(val primary: Boolean, val volume: String, val path: String) {
    companion object {
        fun of(tree: String): FolderPath? = try {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(tree))
            val i = id.indexOf(':')
            val vol = if (i < 0) id else id.substring(0, i)
            val path = if (i < 0) "" else id.substring(i + 1)
            FolderPath(vol == "primary", vol, path)
        } catch (e: Exception) {
            null
        }
    }
}
