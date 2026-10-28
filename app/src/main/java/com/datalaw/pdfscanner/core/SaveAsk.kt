package com.datalaw.pdfscanner.core

import com.datalaw.pdfscanner.R

/** The Save dialog of every PDF: the name, "Save" or "Cancel". Null = cancelled. */
suspend fun askName(d: Deps, def: String): String? =
    d.ui.askText(tx(R.string.scan_nameq), def, tx(R.string.sc_save), tx(R.string.ed_cancel))
