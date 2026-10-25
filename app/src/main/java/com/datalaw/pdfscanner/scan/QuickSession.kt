package com.datalaw.pdfscanner.scan

import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.CamUi
import com.datalaw.pdfscanner.core.CameraSession
import com.datalaw.pdfscanner.core.Deps
import com.datalaw.pdfscanner.core.FolderAccess
import com.datalaw.pdfscanner.core.Tab
import com.datalaw.pdfscanner.core.ask
import com.datalaw.pdfscanner.core.askSave
import com.datalaw.pdfscanner.core.askSaveAsIs
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.core.userReason
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.imaging.SheetFinder
import com.datalaw.pdfscanner.imaging.ShotCheck
import com.datalaw.pdfscanner.imaging.ShotFile
import com.datalaw.pdfscanner.settings.CamMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * QUICK SCAN: one shot at the best quality of the camera, saved as qscan_YYYYMMDD_HHMM.pdf (the name can be edited); then the Library opens.
 * Frame mode: the photo is cut to the A4 frame. Auto mode: the sheet is found in the photo and straightened.
 * Before saving, a sheet that goes past the frame (Frame) or past the camera view (Auto), or is not found (Auto), asks "Retake / Save as is".
 */
class QuickSession(private val d: Deps) : CameraSession {
    private val _ui = MutableStateFlow(CamUi(tx(R.string.qs_label), tx(R.string.qs_hint), autoLabel = tx(R.string.qs_label_auto), autoHint = tx(R.string.qs_hint_auto)))
    override val ui: StateFlow<CamUi> = _ui.asStateFlow()
    override val smart: Boolean = true
    private val access = FolderAccess(d)

    override fun frameRatio(land: Boolean): Float = if (land) 297f / 210f else 210f / 297f

    override suspend fun onShot(shot: ShotFile): Boolean {
        val auto = d.settings().camMode == CamMode.Auto
        val found = if (auto) withContext(Dispatchers.Default) { SheetFinder.find(shot.file, 0.2, null) } else null
        val problem = if (auto) ShotCheck.problem(found?.quad) else null
        if (problem != null && !d.ui.askSaveAsIs(problem.ask(sheet = true))) { shot.file.delete(); return false }
        val def = Names.auto("qscan")
        val choice = askSave(d, def) ?: return false
        val name = Names.clean(choice.name).ifEmpty { def }
        val renderer = d.renderer(choice.recognize)
        _ui.value = _ui.value.copy(status = tx(R.string.scan_saving))
        return try {
            val page = when {
                auto && found != null -> renderer.straightened(shot, found.quad)
                auto -> renderer.raw(ShotFile(shot.file, null, shot.vr, shot.land))
                else -> renderer.raw(shot)
            }
            val saved = access.save(name, 1) { page }
            d.ui.toast(tx(R.string.scan_saved_n, saved.name))
            d.nav.libraryChanged()
            d.nav.goTo(Tab.Library)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            d.ui.toast(tx(R.string.scan_fail_n, e.userReason()))
            false
        } finally {
            _ui.value = _ui.value.copy(status = null)
        }
    }

    override suspend fun canClose(): Boolean = true
    override fun onClosed() { d.graph.clearShots() }
}
