package com.datalaw.pdfscanner.core

import com.datalaw.pdfscanner.R
import kotlinx.coroutines.CancellationException

/** The name of a PDF and whether its text is recognised. */
class SaveChoice(val name: String, val recognize: Boolean)

/**
 * The Save dialog of every PDF: the name, then "Recognize and save" or "Save as is". The recognition needs the language data once (the internet);
 * when it cannot be had, the user may save as is. Null = cancelled.
 */
suspend fun askSave(d: Deps, def: String): SaveChoice? {
    val a = d.ui.askSave(tx(R.string.scan_nameq), def, tx(R.string.save_recognize), tx(R.string.save_plain), tx(R.string.ed_cancel)) ?: return null
    if (!a.recognize) return SaveChoice(a.name, false)
    return try {
        d.graph.ocr.ensure(d.ocrLangs())
        SaveChoice(a.name, true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (d.ui.askConfirm(tx(R.string.save_ocr_fail), tx(R.string.save_plain), tx(R.string.dlg_cancel))) SaveChoice(a.name, false) else null
    }
}
