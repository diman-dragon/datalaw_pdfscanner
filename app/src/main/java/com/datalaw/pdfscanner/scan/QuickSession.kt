package com.datalaw.pdfscanner.scan

import com.datalaw.pdfscanner.ui.askSave
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.camera.CamUi
import com.datalaw.pdfscanner.camera.CameraSession
import com.datalaw.pdfscanner.camera.ShotFile
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.EdgeDetector
import com.datalaw.pdfscanner.imaging.SheetFit
import com.datalaw.pdfscanner.settings.CamMode
import com.datalaw.pdfscanner.ui.Deps
import com.datalaw.pdfscanner.ui.Tab
import com.datalaw.pdfscanner.ui.tx
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
    private val whole = floatArrayOf(0f, 0f, 1f, 1f)

    override fun frameRatio(land: Boolean): Float = if (land) 297f / 210f else 210f / 297f

    /** The sheet in the photo (corners as 0..1), null when none is seen. */
    private suspend fun findSheet(shot: ShotFile): List<Offset>? = withContext(Dispatchers.Default) {
        try {
            val img = BitmapLoader.load(shot.file, 1200)
            try {
                val aspect = img.width.toFloat() / img.height
                EdgeDetector.find(img)?.map { Offset(it.x / img.width, it.y / img.height) }?.takeIf { SheetFit.plausible(it, aspect) }
            } finally {
                img.recycle()
            }
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun onShot(shot: ShotFile): Boolean {
        val auto = d.settings().camMode == CamMode.Auto
        val quad = findSheet(shot)
        val problem = when {
            auto && quad == null -> R.string.qs_nosheet_ask
            auto && quad != null && SheetFit.touchesEdge(quad) -> R.string.qs_cut_ask
            !auto && quad != null && SheetFit.outside(quad, shot.crop ?: whole) -> R.string.qs_outside_ask
            else -> null
        }
        if (problem != null) {
            val a = d.ui.askChoice(tx(problem), listOf("retake" to tx(R.string.qs_blur_retake), "save" to tx(R.string.qs_blur_save)))
            if (a != "save") { shot.file.delete(); return false }
        }
        val def = Names.auto("qscan")
        val choice = askSave(d, def) ?: return false
        val name = Names.clean(choice.name).ifEmpty { def }
        val renderer = d.renderer(choice.recognize)
        _ui.value = _ui.value.copy(status = tx(R.string.scan_saving))
        return try {
            val page = when {
                auto && quad != null -> renderer.straightened(shot, quad)
                auto -> renderer.raw(ShotFile(shot.file, null, shot.vr, shot.land))
                else -> renderer.raw(shot)
            }
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
