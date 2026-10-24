package com.datalaw.pdfscanner.settings

enum class ThemeMode { Auto, Light, Dark }
enum class FlashMode { Auto, On, Off; fun next(): FlashMode = entries[(ordinal + 1) % entries.size] }
enum class CamMode { Frame, Auto }
enum class LibView { List, Cards }
enum class AppLang(val tag: String) { Auto(""), En("en"), Ru("ru") }

data class Settings(
    val theme: ThemeMode = ThemeMode.Auto,
    val lang: AppLang = AppLang.Auto,
    val folderUri: String? = null,
    val folderName: String = "",
    val pdfDpi: Int = 0, // 0 = Auto: the best the camera gives
    val flash: FlashMode = FlashMode.Auto,
    val libView: LibView = LibView.List,
    val camMode: CamMode = CamMode.Frame,
    val scanType: String = "a4",
    val ocrLangs: Set<String> = setOf("ru", "en"),
)
