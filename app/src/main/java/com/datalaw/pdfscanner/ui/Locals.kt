package com.datalaw.pdfscanner.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.datalaw.pdfscanner.AppGraph
import com.datalaw.pdfscanner.settings.Settings

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph is not provided") }
val LocalSettings = compositionLocalOf { Settings() }
