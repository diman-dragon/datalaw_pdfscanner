package com.datalaw.pdfscanner.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.datalaw.pdfscanner.settings.AppLang
import java.util.Locale

/** The language of the app: the strings are taken from a context with the chosen locale. */
@Composable
fun WithLocale(lang: AppLang, content: @Composable () -> Unit) {
    if (lang == AppLang.Auto) {
        content()
        return
    }
    val base = LocalContext.current
    val cfg = LocalConfiguration.current
    val newCfg = remember(cfg, lang) { Configuration(cfg).apply { setLocale(Locale.forLanguageTag(lang.tag)) } }
    val ctx = remember(base, newCfg) { base.createConfigurationContext(newCfg) }
    CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides newCfg) { content() }
}
