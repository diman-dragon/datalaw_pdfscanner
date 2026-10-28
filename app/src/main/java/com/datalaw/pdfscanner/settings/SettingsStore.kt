package com.datalaw.pdfscanner.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDs by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val lang = stringPreferencesKey("lang")
        val folderUri = stringPreferencesKey("folder_uri")
        val folderName = stringPreferencesKey("folder_name")
        val dpi = intPreferencesKey("pdf_dpi")
        val flash = stringPreferencesKey("flash")
        val libView = stringPreferencesKey("lib_view")
        val camMode = stringPreferencesKey("cam_mode")
        val scanType = stringPreferencesKey("scan_type")
        val docLayout = stringPreferencesKey("doc_layout")
        val pages = stringPreferencesKey("doc_pages")
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
            pdfDpi = p[K.dpi]?.takeIf { it == 0 || it == 150 || it == 300 || it == 600 } ?: 0,
            flash = p.pick(K.flash, FlashMode.Auto),
            libView = p.pick(K.libView, LibView.List),
            camMode = p.pick(K.camMode, CamMode.Frame),
            scanType = p[K.scanType] ?: "a4",
            docLayout = p.pick(K.docLayout, DocLayout.Grid),
        )
    }

    private suspend fun <T> put(key: Preferences.Key<T>, v: T) {
        context.settingsDs.edit { it[key] = v }
    }

    suspend fun setTheme(v: ThemeMode) = put(K.theme, v.name)
    suspend fun setLang(v: AppLang) = put(K.lang, v.name)
    suspend fun setPdfDpi(v: Int) = put(K.dpi, v)
    suspend fun setFlash(v: FlashMode) = put(K.flash, v.name)
    suspend fun setLibView(v: LibView) = put(K.libView, v.name)
    suspend fun setCamMode(v: CamMode) = put(K.camMode, v.name)
    suspend fun setScanType(v: String) = put(K.scanType, v)
    suspend fun setDocLayout(v: DocLayout) = put(K.docLayout, v.name)

    /** The page a document was left on ("page TAB uri" per line, the last 40 documents). */
    suspend fun pageOf(uri: String): Int {
        val s = context.settingsDs.data.first()[K.pages] ?: return 0
        for (line in s.lines()) {
            val tab = line.indexOf('\t')
            if (tab > 0 && line.substring(tab + 1) == uri) return line.substring(0, tab).toIntOrNull() ?: 0
        }
        return 0
    }

    suspend fun setPageOf(uri: String, page: Int) {
        context.settingsDs.edit { p ->
            val others = (p[K.pages] ?: "").lines().filter { it.isNotEmpty() && it.substring(it.indexOf('\t') + 1) != uri }.take(39)
            p[K.pages] = (listOf("$page\t$uri") + others).joinToString("\n")
        }
    }
    suspend fun setFolder(uri: String, name: String) {
        context.settingsDs.edit { it[K.folderUri] = uri; it[K.folderName] = name }
    }
}
