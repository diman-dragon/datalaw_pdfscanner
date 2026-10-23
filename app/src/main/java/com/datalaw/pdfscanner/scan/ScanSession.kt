package com.datalaw.pdfscanner.scan

import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.camera.CamUi
import com.datalaw.pdfscanner.camera.CameraSession
import com.datalaw.pdfscanner.camera.ShotFile
import com.datalaw.pdfscanner.data.Names
import com.datalaw.pdfscanner.imaging.PlacedItem
import com.datalaw.pdfscanner.imaging.SheetComposer
import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.ui.Deps
import com.datalaw.pdfscanner.ui.Tab
import com.datalaw.pdfscanner.ui.UiText
import com.datalaw.pdfscanner.ui.tx
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SCAN TAB: the same bare camera with the frame of the chosen type. After every shot: "Shoot more" or "Finish".
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
    private val _ui = MutableStateFlow(CamUi(tx(type.nameRes), defaultHint))
    override val ui: StateFlow<CamUi> = _ui.asStateFlow()

    override fun frameRatio(land: Boolean): Float {
        val m = ScanTypes.itemMm(type, land)
        return (m.first / m.second).toFloat()
    }

    private fun texts(hint: UiText, label: UiText) { _ui.value = _ui.value.copy(hint = hint, label = label) }
    private fun resetTexts() = texts(defaultHint, tx(type.nameRes))
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

    override suspend fun onShot(shot: ShotFile): Boolean {
        shots.add(shot)
        val mm = ScanTypes.itemMm(type, shot.land)
        val c = cur ?: Draft(ArrayList(), mm, mm.first > 210.5, type.parts).also { cur = it } // an A4 held landscape does not fit the sheet: it is turned
        c.sides.add(shot)
        if (type.sides == 2 && c.sides.size == 1) {
            val a = d.ui.askChoice(tx(R.string.sc_askback), listOf("back" to tx(R.string.sc_back), "skip" to tx(R.string.sc_noback)))
            if (a == "back") { texts(tx(R.string.sc_hint_back), tx(R.string.sc_label_back, tx(type.nameRes))); return false }
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
            val typed = d.ui.askText(tx(R.string.scan_nameq), def, tx(R.string.sc_save), tx(R.string.ed_cancel))
            if (typed != null) return finish(Names.clean(typed).ifEmpty { def })
            a = askMore()
        }
    }

    private suspend fun finish(name: String): Boolean {
        _ui.value = _ui.value.copy(status = tx(R.string.scan_saving))
        return try {
            val list = sheets.toList()
            val saved = access.save(name, list.size) { i -> list[i].await() }
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

    override suspend fun canClose(): Boolean =
        (sheets.isEmpty() && cur == null) || d.ui.askConfirm(tx(R.string.sc_discardq), tx(R.string.sc_discard), tx(R.string.dlg_stay), danger = true)

    override fun onClosed() {
        sheets.forEach { it.cancel() }
        d.graph.clearShots()
    }
}
