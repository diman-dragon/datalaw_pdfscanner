package com.datalaw.pdfscanner.docedit

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Deps
import com.datalaw.pdfscanner.core.FolderAccess
import com.datalaw.pdfscanner.core.askName
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.core.userReason
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.imaging.Look
import com.datalaw.pdfscanner.imaging.PageRenderer
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.pdf.PdfTarget
import com.datalaw.pdfscanner.settings.DocLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A page of the document being edited: the frame (normalised corners TL TR BR BL, null = whole page), the look, the turns. */
class EditPage(val index: Int, val ratio: Float) {
    var quad by mutableStateOf<List<Offset>?>(null)
    var look by mutableStateOf(Look.Original)
    var turns by mutableIntStateOf(0)
    var thumb by mutableStateOf<Bitmap?>(null)
    val touched: Boolean
        get() = (quad?.let { !PageRenderer.isFull(it) } ?: false) || look != Look.Original || turns % 4 != 0
}

/** Edit an existing PDF: frame / look / rotation of each page, order, deletion; save replaces the original or makes a copy. */
class DocEditController(private val d: Deps, val file: PdfFile) {
    val pages = mutableStateListOf<EditPage>()
    var loading by mutableStateOf(true)
    var editing by mutableIntStateOf(-1)   // index in [pages] of the page open in the editor, -1 = the grid
    var sequence by mutableStateOf(false)  // the editor walks through all pages (Back / Next)
    var busy by mutableStateOf(false)
    /** How the document is shown: one page at a time (true) or all pages in a grid. Taken from the settings at the start, changed by the person, remembered. */
    var layoutPages by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    private var reader: PdfReader? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(d.scope.coroutineContext + job)
    private val access = FolderAccess(d)

    fun markDirty() { dirty = true }

    /** Something was changed: a frame, a look, a turn, the order or a deleted page. */
    val hasChanges: Boolean get() = dirty || pages.any { it.touched }

    fun start() {
        scope.launch {
            try {
                layoutPages = d.settings().docLayout == DocLayout.Pages
                val r = PdfReader.open(d.graph.app.contentResolver, Uri.parse(file.uri))
                reader = r
                r.ratios.forEachIndexed { i, ratio -> pages.add(EditPage(i, ratio)) }
                loading = false
                if (layoutPages && pages.isNotEmpty()) editing = d.graph.settingsStore.pageOf(file.uri).coerceIn(0, pages.size - 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                d.ui.toast(tx(R.string.de_fail))
                d.nav.pop()
            }
        }
    }

    /** One page at a time, from the page the document was left on; remembered for the next opening. */
    fun showPages() {
        scope.launch {
            layoutPages = true
            sequence = false
            d.graph.settingsStore.setDocLayout(DocLayout.Pages)
            if (pages.isNotEmpty()) editing = d.graph.settingsStore.pageOf(file.uri).coerceIn(0, pages.size - 1)
        }
    }

    /** All pages in a grid; remembered for the next opening. */
    fun showGrid() {
        layoutPages = false
        sequence = false
        editing = -1
        scope.launch { d.graph.settingsStore.setDocLayout(DocLayout.Grid) }
    }

    /** The page the person is on, so that the document opens on it next time. */
    fun rememberPage(i: Int) {
        scope.launch { d.graph.settingsStore.setPageOf(file.uri, i) }
    }

    fun close() {
        job.cancel()
        reader?.close()
    }

    /** The page as a bitmap whose long side is about [side] px. */
    suspend fun source(p: EditPage, side: Int): Bitmap {
        val w = if (p.ratio > 1f) side / p.ratio else side.toFloat()
        return reader!!.render(p.index, w.toInt().coerceAtLeast(64), maxHeight = side)
    }

    suspend fun makeThumb(p: EditPage): Bitmap = withContext(Dispatchers.Default) {
        val src = source(p, 520)
        val out = PageRenderer.preview(src, p.quad, p.look, p.turns, 520)
        if (out !== src) src.recycle()
        out
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in pages.indices) return
        val t = pages[i]; pages[i] = pages[j]; pages[j] = t
        dirty = true
    }

    /** Asks first; returns true when the page is gone. */
    suspend fun delete(i: Int): Boolean {
        if (pages.size < 2) return false
        if (!d.ui.askConfirm(tx(R.string.sc_deleteq), tx(R.string.sc_delete), tx(R.string.dlg_cancel), danger = true)) return false
        pages.removeAt(i)
        dirty = true
        return true
    }

    fun requestClose() {
        scope.launch {
            if (!hasChanges || d.ui.askConfirm(tx(R.string.de_discardq), tx(R.string.de_discard), tx(R.string.dlg_stay), danger = true)) d.nav.pop()
        }
    }

    fun save() {
        if (busy || pages.isEmpty()) return
        scope.launch {
            val base = Names.withoutPdf(file.name)
            val typed = askName(d, base) ?: return@launch
            val name = Names.clean(typed).ifEmpty { base }
            val same = name.equals(base, ignoreCase = true)
            if (same && !d.ui.askConfirm(tx(R.string.de_replaceq), tx(R.string.de_replace), tx(R.string.dlg_cancel))) return@launch
            busy = true
            d.ui.beginProgress(tx(R.string.scan_saving))
            try {
                val list = pages.toList()
                val renderer = d.renderer()
                val side = PdfTarget.forDpi(d.settings().pdfDpi).side.coerceAtMost(5200)
                val r = access.save(if (same) "$base~new" else name, list.size) { i ->
                    d.ui.setProgress(tx(R.string.scan_saving_n, i + 1, list.size))
                    val p = list[i]
                    val src = source(p, side)
                    try { renderer.edited(src, p.quad, p.look, p.turns) } finally { src.recycle() }
                }
                var savedName = r.name
                if (same) { // replace the original in place (never deleted or renamed first); if that fails the new file stays
                    val repo = d.graph.folders
                    try {
                        repo.replaceWith(Uri.parse(file.uri), Uri.parse(r.uri))
                        savedName = file.name
                    } catch (e1: Exception) {
                        try { // the original is gone: the new file simply takes its name
                            repo.rename(Uri.parse(r.uri), "$base.pdf")
                            savedName = "$base.pdf"
                        } catch (e2: Exception) {
                            d.ui.askAlert(tx(R.string.de_keptnew_n, r.name))
                        }
                    }
                }
                d.ui.toast(tx(R.string.scan_saved_n, savedName))
                dirty = false
                d.nav.libraryChanged()
                d.nav.pop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                d.ui.toast(tx(R.string.scan_fail_n, e.userReason()))
            } finally {
                busy = false
                d.ui.endProgress()
            }
        }
    }
}
