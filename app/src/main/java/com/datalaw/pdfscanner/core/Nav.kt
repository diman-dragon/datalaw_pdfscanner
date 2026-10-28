package com.datalaw.pdfscanner.core

import androidx.annotation.StringRes
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.imaging.PageRenderer
import com.datalaw.pdfscanner.pdf.PdfTarget
import com.datalaw.pdfscanner.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

enum class Tab { Library, Quick, Scan, Settings }

/** Full-screen things on top of the tabs; a stack, so Back returns to what was below. A file opens in its editor: there is no separate viewer. */
sealed interface Overlay {
    data class DocEdit(val file: PdfFile) : Overlay
    data class Camera(val session: CameraSession) : Overlay
}

interface Nav {
    fun goTo(tab: Tab)
    fun push(o: Overlay)
    fun pop()
    fun libraryChanged()
}

/** What the screens may ask of the app: navigation, a toast, the folder picker and the start of the two scan tabs. The view model implements it. */
interface AppActions : Nav {
    val libVersion: StateFlow<Int>
    val deps: Deps
    fun toast(@StringRes id: Int)
    fun chooseFolder()
    fun startQuick()
    fun startScan()
}

/** What controllers need: the graph, a scope, the current settings and navigation. */
class Deps(val graph: AppGraph, val scope: CoroutineScope, val settings: () -> Settings, val nav: Nav) {
    val ui: UiHost get() = graph.ui

    fun renderer(): PageRenderer = PageRenderer(PdfTarget.forDpi(settings().pdfDpi))
}

