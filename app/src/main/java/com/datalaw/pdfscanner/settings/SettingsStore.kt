package com.datalaw.pdfscanner.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDs by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val lang = stringPreferencesKey("lang")
        val folderUri = stringPreferencesKey("folder_uri")
        val folderName = stringPreferencesKey("folder_name")
        val dpi = intPreferencesKey("pdf_dpi")
        val search = booleanPreferencesKey("pdf_search")
        val focus = stringPreferencesKey("focus")
        val flash = stringPreferencesKey("flash")
        val libView = stringPreferencesKey("lib_view")
        val quickMode = stringPreferencesKey("quick_mode")
        val scanType = stringPreferencesKey("scan_type")
        val ocrLangs = stringSetPreferencesKey("ocr_langs")
    }

    private inline fun <reified E : Enum<E>> Preferences.pick(key: Preferences.Key<String>, def: E): E {
        val s = this[key] ?: return def
        return enumValues<E>().firstOrNull { it.name == s } ?: def
    }

    val flow: Flow<Settings> = context.settingsDs.data.map { p ->
        Settings(
            theme = p.pick(K.theme, ThemeMode.Auto),
            lang = p.pick(K.lang, AppLang.Auto),
            folderUri = p[K.folderUri],
            folderName = p[K.folderName] ?: "",
            pdfDpi = p[K.dpi]?.takeIf { it == 150 || it == 300 || it == 600 } ?: 150,
            pdfSearch = p[K.search] ?: false,
            focus = p.pick(K.focus, FocusMode.Smart),
            flash = p.pick(K.flash, FlashMode.Auto),
            libView = p.pick(K.libView, LibView.List),
            quickMode = p.pick(K.quickMode, QuickMode.Frame),
            scanType = p[K.scanType] ?: "a4",
            ocrLangs = p[K.ocrLangs] ?: setOf("ru", "en"),
        )
    }

    private suspend fun <T> put(key: Preferences.Key<T>, v: T) {
        context.settingsDs.edit { it[key] = v }
    }

    suspend fun setTheme(v: ThemeMode) = put(K.theme, v.name)
    suspend fun setLang(v: AppLang) = put(K.lang, v.name)
    suspend fun setPdfDpi(v: Int) = put(K.dpi, v)
    suspend fun setPdfSearch(v: Boolean) = put(K.search, v)
    suspend fun setFocus(v: FocusMode) = put(K.focus, v.name)
    suspend fun setFlash(v: FlashMode) = put(K.flash, v.name)
    suspend fun setLibView(v: LibView) = put(K.libView, v.name)
    suspend fun setQuickMode(v: QuickMode) = put(K.quickMode, v.name)
    suspend fun setScanType(v: String) = put(K.scanType, v)
    suspend fun setOcrLangs(v: Set<String>) = put(K.ocrLangs, v)
    suspend fun setFolder(uri: String, name: String) {
        context.settingsDs.edit { it[K.folderUri] = uri; it[K.folderName] = name }
    }
}
