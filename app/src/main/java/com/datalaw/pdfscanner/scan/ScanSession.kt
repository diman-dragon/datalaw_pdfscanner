package com.datalaw.pdfscanner.scan

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.CamUi
import com.datalaw.pdfscanner.core.CameraSession
import com.datalaw.pdfscanner.core.Deps
import com.datalaw.pdfscanner.core.FolderAccess
import com.datalaw.pdfscanner.core.Tab
import com.datalaw.pdfscanner.core.UiText
import com.datalaw.pdfscanner.core.ask
import com.datalaw.pdfscanner.core.askSave
import com.datalaw.pdfscanner.core.askSaveAsIs
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.core.userReason
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.imaging.Found
import com.datalaw.pdfscanner.imaging.PlacedItem
import com.datalaw.pdfscanner.imaging.SheetComposer
import com.datalaw.pdfscanner.imaging.SheetFinder
import com.datalaw.pdfscanner.imaging.ShotCheck
import com.datalaw.pdfscanner.imaging.ShotFile
import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.settings.CamMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * SCAN TAB: the same bare camera with the frame of the chosen type (Frame mode), or without it: the item is found in the camera by itself (Auto mode). After every shot: "Shoot more" or "Finish".
 * An ID card asks for the back (both on ONE sheet with a gap); a receipt can continue (parts side by side, then the next sheet).
 * Every item is made into an A4 sheet at its real size in the background while the user decides what is next.
 */
class ScanSession(private val d: Deps, private val type: ScanType) : CameraSession {
    private class Draft(val sides: MutableList<ShotFile>, val mm: Pair<Double, Double>, val rotate: Boolean, val row: Boolean)

    private val access = FolderAccess(d)
    private val sheets = ArrayList<Deferred<PdfPage>>()
    private val shots = ArrayList<ShotFile>()
    private var cur: Draft? = null

    private val defaultHint: UiText = tx(if (type.orient == Orient.Video) R.string.sc_hint_page else R.string.sc_hint_item)
    private val autoHint: UiText = tx(if (type.free) R.string.sc_hint_full else R.string.sc_hint_auto)
    private fun autoLabelOf(label: UiText): UiText = if (type.free) label else tx(R.string.sc_label_auto, label)
    private val _ui = MutableStateFlow(CamUi(tx(type.nameRes), defaultHint, autoLabel = autoLabelOf(tx(type.nameRes)), autoHint = autoHint))
    override val ui: StateFlow<CamUi> = _ui.asStateFlow()
    override val smart: Boolean = true
    override val expectRatio: Double? = if (type.free) null else type.mm.first / type.mm.second
    override val minAreaFrac: Double = if (type.free) 0.12 else 0.1
    override val forcedAuto: Boolean = type.free
    override val autoShoot: Boolean = type.free
    override val sheetLike: Boolean = type.orient == Orient.Video

    override fun frameRatio(land: Boolean): Float {
        val m = ScanTypes.itemMm(type, land)
        return (m.first / m.second).toFloat()
    }

    private fun texts(hint: UiText, label: UiText, forAuto: UiText = hint) {
        _ui.value = _ui.value.copy(hint = hint, label = label, autoHint = forAuto, autoLabel = autoLabelOf(label))
    }
    private fun resetTexts() = texts(defaultHint, tx(type.nameRes), autoHint)
    private fun badge() { _ui.value = _ui.value.copy(badge = tx(R.string.sc_count_n, sheets.size)) }

    private suspend fun askMore(): String =
        d.ui.askChoice(tx(R.string.sc_count_n, sheets.size), listOf("more" to tx(R.string.sc_more), "finish" to tx(R.string.sc_finish))) ?: "more"

    private fun complete(c: Draft) {
        val cap = if (c.row) SheetComposer.rowCap(c.mm.first) else Int.MAX_VALUE
        var i = 0
        while (i < c.sides.size) {
            val part = if (c.row) c.sides.subList(i, minOf(c.sides.size, i + cap)).toList() else c.sides.toList()
            val item = PlacedItem(part, c.mm, c.rotate, c.row)
            sheets.add(d.scope.async(Dispatchers.Default) { d.renderer().placed(item) })
            if (!c.row) break
            i += cap
        }
        badge()
    }

    /** The corners turned to the shape of the item (a card shown upright is turned to lie), and whether an A4 / A5 is wide. */
    private fun oriented(f: Found): Pair<List<Offset>, Boolean> {
        val q = f.quad
        val wide = f.aspect > 1
        if (type.orient == Orient.Video) return q to wide
        val want = type.mm.first > type.mm.second
        return (if (wide == want) q else listOf(q[3], q[0], q[1], q[2])) to false
    }

    /** Checks the item in the photo and, in Auto mode, finds its corners. null = the user chose to retake. */
    private suspend fun prepare(raw: ShotFile): ShotFile? {
        val auto = type.free || d.settings().camMode == CamMode.Auto
        val found = if (auto) withContext(Dispatchers.Default) { SheetFinder.find(raw.file, 0.08, expectRatio) } else null
        val problem = if (auto) ShotCheck.problem(found?.quad) else null
        if (problem != null && !d.ui.askSaveAsIs(problem.ask(sheetLike))) { raw.file.delete(); return null }
        if (!auto) return raw
        if (found == null) return ShotFile(raw.file, null, raw.vr, raw.land)
        val (quad, wide) = oriented(found)
        return ShotFile(raw.file, raw.crop, raw.vr, if (type.orient == Orient.Video) wide else raw.land, quad, if (type.free) ItemFit.mm(found.aspect) else null)
    }

    override suspend fun onShot(shot: ShotFile): Boolean {
        val checked = prepare(shot) ?: return false
        return place(checked)
    }

    private suspend fun place(shot: ShotFile): Boolean {
        shots.add(shot)
        val mm = shot.mm ?: ScanTypes.itemMm(type, shot.land)
        val c = cur ?: Draft(ArrayList(), mm, mm.first > 210.5, type.parts).also { cur = it } // an A4 held landscape does not fit the sheet: it is turned
        c.sides.add(shot)
        if (type.sides == 2 && c.sides.size == 1) {
            val a = d.ui.askChoice(tx(R.string.sc_askback), listOf("back" to tx(R.string.sc_back), "skip" to tx(R.string.sc_noback)))
            if (a == "back") { texts(tx(R.string.sc_hint_back), tx(R.string.sc_label_back, tx(type.nameRes)), tx(R.string.sc_hint_back_auto)); return false }
        }
        if (type.parts) {
            val n = c.sides.size
            val count = sheets.size + Math.ceil(n.toDouble() / SheetComposer.rowCap(mm.first)).toInt()
            val a = d.ui.askChoice(tx(R.string.sc_count_n, count), listOf("more" to tx(R.string.sc_more), "part" to tx(R.string.sc_part), "finish" to tx(R.string.sc_finish))) ?: "more"
            if (a == "part") { texts(tx(R.string.sc_hint_part), tx(R.string.sc_label_part, tx(type.nameRes), n + 1)); return false }
            complete(c); cur = null; resetTexts()
            return proceed(a)
        }
        complete(c); cur = null; resetTexts()
        return proceed(askMore())
    }

    /** a = "more" (back to the camera) or "finish" (the name, then save; a cancelled name asks again). */
    private suspend fun proceed(first: String): Boolean {
        var a = first
        while (true) {
            if (a == "more") return false
            val def = Names.auto("scan")
            val choice = askSave(d, def)
            if (choice != null) return finish(Names.clean(choice.name).ifEmpty { def }, choice.recognize)
            a = askMore()
        }
    }

    private suspend fun finish(name: String, recognize: Boolean): Boolean {
        _ui.value = _ui.value.copy(status = tx(R.string.scan_saving))
        return try {
            val list = sheets.toList()
            val renderer = d.renderer(recognize)
            val saved = access.save(name, list.size) { i ->
                val page = list[i].await()
                if (recognize) renderer.recognized(page) else page
            }
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

    override suspend fun canClose(): Boolean =
        (sheets.isEmpty() && cur == null) || d.ui.askConfirm(tx(R.string.sc_discardq), tx(R.string.sc_discard), tx(R.string.dlg_stay), danger = true)

    override fun onClosed() {
        sheets.forEach { it.cancel() }
        d.graph.clearShots()
    }
}
