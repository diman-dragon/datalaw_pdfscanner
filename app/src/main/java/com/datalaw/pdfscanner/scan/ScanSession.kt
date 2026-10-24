package com.datalaw.pdfscanner.scan

import com.datalaw.pdfscanner.ui.askSave
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.camera.CamUi
import com.datalaw.pdfscanner.camera.CameraSession
import com.datalaw.pdfscanner.camera.ShotFile
import com.datalaw.pdfscanner.data.Names
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.EdgeDetector
import com.datalaw.pdfscanner.imaging.PlacedItem
import com.datalaw.pdfscanner.imaging.ShapeMatch
import com.datalaw.pdfscanner.imaging.SheetFit
import com.datalaw.pdfscanner.settings.CamMode
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
    private val whole = floatArrayOf(0f, 0f, 1f, 1f)

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

    private class Found(val quad: List<Offset>, val aspect: Double)

    /** The item in the photo: corners as 0..1 and the real width / height they show; null when nothing of the item's shape is seen. */
    private suspend fun findItem(shot: ShotFile): Found? = withContext(Dispatchers.Default) {
        try {
            val img = BitmapLoader.load(shot.file, 1200)
            try {
                val found = EdgeDetector.find(img, 0.08)?.map { Offset(it.x / img.width, it.y / img.height) }
                val expected = type.mm.first / type.mm.second
                if (found == null || !SheetFit.plausible(found, img.width.toFloat() / img.height, checkRatio = type.free) ||
                    (!type.free && !ShapeMatch.matches(found, img.width, img.height, expected))) null
                else Found(found, ShapeMatch.aspectOf(found, img.width, img.height))
            } finally {
                img.recycle()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Full auto: the size the found item is put at. A page (A4, A5, letter...) fills the sheet; anything else is fitted into the sheet, its shape kept. */
    private fun fitMm(aspect: Double): Pair<Double, Double> {
        val portrait = aspect < 1
        val r = if (portrait) aspect else 1 / aspect // short side / long side
        val long = if (Math.abs(r - 210.0 / 297.0) < 0.07) 297.0 else minOf(277.0, 190.0 / r)
        val short = if (long == 297.0) 210.0 else long * r
        return if (portrait) short to long else long to short
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
        val found = findItem(raw)
        val sheet = type.orient == Orient.Video
        val problem = when {
            auto && found == null -> if (sheet) R.string.qs_nosheet_ask else R.string.sc_nosheet_ask
            auto && found != null && SheetFit.touchesEdge(found.quad) -> if (sheet) R.string.qs_cut_ask else R.string.sc_cut_ask
            !auto && found != null && SheetFit.outside(found.quad, raw.crop ?: whole) -> if (sheet) R.string.qs_outside_ask else R.string.sc_outside_ask
            else -> null
        }
        if (problem != null) {
            val a = d.ui.askChoice(tx(problem), listOf("retake" to tx(R.string.qs_blur_retake), "save" to tx(R.string.qs_blur_save)))
            if (a != "save") { raw.file.delete(); return null }
        }
        if (!auto) return raw
        if (found == null) return ShotFile(raw.file, null, raw.vr, raw.land)
        val (quad, wide) = oriented(found)
        return ShotFile(raw.file, raw.crop, raw.vr, if (type.orient == Orient.Video) wide else raw.land, quad, if (type.free) fitMm(found.aspect) else null)
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
