package com.datalaw.pdfscanner.ui

import com.datalaw.pdfscanner.AppGraph
import com.datalaw.pdfscanner.camera.CameraSession
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.imaging.PageRenderer
import com.datalaw.pdfscanner.ocr.OcrLangs
import com.datalaw.pdfscanner.pdf.PdfTarget
import com.datalaw.pdfscanner.settings.Settings
import kotlinx.coroutines.CoroutineScope

enum class Tab { Library, Quick, Scan, Settings }

/** Full-screen things on top of the tabs; a stack, so Back returns to what was below (viewer -> editor -> viewer). */
sealed interface Overlay {
    data class Viewer(val file: PdfFile) : Overlay
    data class DocEdit(val file: PdfFile, val page: Int? = null) : Overlay
    data class Camera(val session: CameraSession) : Overlay
    data class Ocr(val file: PdfFile, val page: Int, val pages: Int) : Overlay
}

interface Nav {
    fun goTo(tab: Tab)
    fun push(o: Overlay)
    fun pop()
    fun libraryChanged()
}

/** What controllers need: the graph, a scope, the current settings and navigation. */
class Deps(val graph: AppGraph, val scope: CoroutineScope, val settings: () -> Settings, val nav: Nav) {
    val ui: UiHost get() = graph.ui

    /** The Tesseract language data names of the chosen languages. */
    fun ocrLangs(): List<String> = OcrLangs.tess(settings().ocrLangs).ifEmpty { listOf("rus", "eng") }

    /** [recognize] = the pages get an invisible text layer (chosen when the PDF is saved). */
    fun renderer(recognize: Boolean = false): PageRenderer =
        PageRenderer(PdfTarget.forDpi(settings().pdfDpi), if (recognize) graph.ocr else null, if (recognize) ocrLangs() else emptyList())
}

