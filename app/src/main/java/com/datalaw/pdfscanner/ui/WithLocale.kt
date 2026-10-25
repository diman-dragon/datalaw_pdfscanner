package com.datalaw.pdfscanner.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.datalaw.pdfscanner.settings.AppLang
import java.util.Locale

/** The activity as it was, only its strings come from the chosen language. It stays a wrapper of the activity: the system pickers and the back button look for it through the wrappers. */
private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}

/** The language of the app: the strings are taken from resources with the chosen locale (Auto = the language of the phone). */
@Composable
fun WithLocale(lang: AppLang, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val cfg = LocalConfiguration.current
    val newCfg = remember(cfg, lang) { if (lang == AppLang.Auto) cfg else Configuration(cfg).apply { setLocale(Locale.forLanguageTag(lang.tag)) } }
    val ctx = remember(base, newCfg, lang) { if (lang == AppLang.Auto) base else LocalizedContext(base, base.createConfigurationContext(newCfg).resources) }
    // the same tree for every language: switching it does not tear the screen down
    CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides newCfg, LocalResources provides ctx.resources) { content() }
}
