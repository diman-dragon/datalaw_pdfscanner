package com.datalaw.pdfscanner.scan

import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.camera.CamUi
import com.datalaw.pdfscanner.camera.CameraSession
import com.datalaw.pdfscanner.camera.ShotFile
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.ui.Deps
import com.datalaw.pdfscanner.ui.Tab
import com.datalaw.pdfscanner.ui.tx
import com.datalaw.pdfscanner.ui.uiRaw
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** QUICK SCAN: one shot of an A4 frame, no processing, saved as qscan_YYYYMMDD_HHMM.pdf (the name can be edited); then the Library opens. */
class QuickSession(private val d: Deps) : CameraSession {
    private val _ui = MutableStateFlow(CamUi(uiRaw("A4"), tx(R.string.qs_hint)))
    override val ui: StateFlow<CamUi> = _ui.asStateFlow()
    private val access = FolderAccess(d)

    override fun frameRatio(land: Boolean): Float = if (land) 297f / 210f else 210f / 297f

    override suspend fun onShot(shot: ShotFile): Boolean {
        val def = Names.auto("qscan")
        val typed = d.ui.askText(tx(R.string.scan_nameq), def, tx(R.string.sc_save), tx(R.string.ed_cancel)) ?: return false
        val name = Names.clean(typed).ifEmpty { def }
        _ui.value = _ui.value.copy(status = tx(R.string.scan_saving))
        return try {
            val page = d.renderer().raw(shot)
            val saved = access.save(name, 1) { page }
            d.ui.toast(tx(R.string.scan_saved_n, saved.name))
            d.nav.libraryChanged()
            d.nav.goTo(Tab.Library)
            true
        } catch (e: Exception) {
            d.ui.toast(tx(R.string.scan_fail_n, e.message ?: ""))
            false
        } finally {
            _ui.value = _ui.value.copy(status = null)
        }
    }

    override suspend fun canClose(): Boolean = true
    override fun onClosed() { d.graph.clearShots() }
}
