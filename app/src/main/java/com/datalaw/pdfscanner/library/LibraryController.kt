package com.datalaw.pdfscanner.library

import android.net.Uri
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Deps
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.data.PdfFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibState {
    data object Loading : LibState
    data object NoFolder : LibState
    data object Error : LibState
    data class Ok(val files: List<PdfFile>) : LibState
}

/** The list of PDFs in the chosen folder and the actions on a file. */
class LibraryController(private val d: Deps) {
    private val _state = MutableStateFlow<LibState>(LibState.Loading)
    val state: StateFlow<LibState> = _state.asStateFlow()

    fun refresh() {
        d.scope.launch {
            val uri = d.settings().folderUri
            if (uri == null) { _state.value = LibState.NoFolder; return@launch }
            _state.value = try {
                LibState.Ok(d.graph.folders.list(Uri.parse(uri)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibState.Error
            }
        }
    }

    fun rename(f: PdfFile) {
        d.scope.launch {
            val typed = d.ui.askText(tx(R.string.lib_renameq), Names.withoutPdf(f.name), tx(R.string.dlg_ok), tx(R.string.dlg_cancel)) ?: return@launch
            val name = Names.clean(typed)
            if (name.isEmpty()) return@launch
            try {
                d.graph.folders.rename(Uri.parse(f.uri), "$name.pdf") ?: throw IllegalStateException("rename failed")
                d.nav.libraryChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                d.ui.askAlert(tx(R.string.lib_fail))
            }
        }
    }

    fun delete(f: PdfFile) {
        d.scope.launch {
            if (!d.ui.askConfirm(tx(R.string.lib_delq), tx(R.string.lib_delete), tx(R.string.dlg_cancel), danger = true)) return@launch
            try {
                d.graph.folders.remove(Uri.parse(f.uri))
                d.nav.libraryChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                d.ui.askAlert(tx(R.string.lib_fail))
            }
        }
    }
}
