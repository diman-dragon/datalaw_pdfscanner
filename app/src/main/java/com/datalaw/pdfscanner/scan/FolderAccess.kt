package com.datalaw.pdfscanner.scan

import android.net.Uri
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.FolderMissingException
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.pdf.PdfWriter
import com.datalaw.pdfscanner.ui.Deps
import com.datalaw.pdfscanner.ui.tx

/** The scan folder: guard before shooting, choosing, and saving a PDF into it (a lost folder asks for another and the save goes on). */
class FolderAccess(private val d: Deps) {
    /** Picks a folder, remembers it. Null if cancelled. */
    suspend fun choose(): Uri? {
        val u = d.ui.pickFolder() ?: return null
        val repo = d.graph.folders
        runCatching { repo.takePermission(u) }
        val name = repo.displayName(repo.treeRoot(u)) ?: ""
        d.graph.settingsStore.setFolder(u.toString(), name)
        d.nav.libraryChanged()
        return u
    }

    /** True if a folder is chosen; otherwise explains and opens the picker (it has a "New folder" button). */
    suspend fun ensure(): Boolean {
        if (d.settings().folderUri != null) return true
        if (!d.ui.askConfirm(tx(R.string.scan_nofolder), tx(R.string.dlg_pick), tx(R.string.dlg_cancel))) return false
        return choose() != null
    }

    /** Writes a PDF into the chosen folder. Name without extension. */
    suspend fun save(name: String, pages: Int, getPage: suspend (Int) -> PdfPage): PdfFile {
        val tree = Uri.parse(d.settings().folderUri ?: throw IllegalStateException("no folder"))
        val repo = d.graph.folders
        return try {
            repo.savePdf(tree, name) { PdfWriter.write(it, pages, getPage) }
        } catch (e: FolderMissingException) {
            if (!d.ui.askConfirm(tx(R.string.save_folderlost), tx(R.string.dlg_pick), tx(R.string.dlg_cancel))) throw e
            val f = choose() ?: throw e
            repo.savePdf(f, name) { PdfWriter.write(it, pages, getPage) }
        }
    }
}
