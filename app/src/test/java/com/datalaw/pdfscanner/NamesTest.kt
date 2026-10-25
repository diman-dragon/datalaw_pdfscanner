package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.data.Names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    @Test fun forbiddenCharactersBecomeSpaces() = assertEquals("a b c", Names.clean("a/b:c?.pdf"))

    @Test fun spacesAndExtensionAreTidied() = assertEquals("Report 2024", Names.clean("  Report  2024.PDF "))

    @Test fun withoutPdfIgnoresCase() = assertEquals("x", Names.withoutPdf("x.PDF"))

    @Test fun autoNameHasThePrefixAndTheTime() = assertTrue(Regex("scan_\\d{8}_\\d{4}").matches(Names.auto("scan")))
}
