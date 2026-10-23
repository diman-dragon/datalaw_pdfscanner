package com.datalaw.pdfscanner.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Names {
    private val bad = Regex("[\\\\/:*?\"<>|]+")
    private val spaces = Regex("\\s+")
    private val pdfExt = Regex("\\.pdf$", RegexOption.IGNORE_CASE)

    /** prefix_YYYYMMDD_HHMM */
    fun auto(prefix: String): String = "${prefix}_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}"

    /** A file name the provider will accept, without the .pdf extension. */
    fun clean(s: String): String = s.replace(bad, " ").replace(spaces, " ").trim().replace(pdfExt, "").trim()

    fun withoutPdf(s: String): String = s.replace(pdfExt, "")
}
