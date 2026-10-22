# Kotlin migration patch

## Что переведено
Только нативный Android-слой (Capacitor plugins + helpers):

| Было (Java) | Стало (Kotlin) |
|-------------|----------------|
| `MainActivity.java` | `MainActivity.kt` |
| `FolderPlugin.java` | `FolderPlugin.kt` |
| `OcrPlugin.java` | `OcrPlugin.kt` |
| `PdfPreview.java` | `PdfPreview.kt` |
| `PdfViewer.java` | `PdfViewer.kt` |

Плюс правки Gradle, чтобы включить Kotlin.

## Как применить
1. Скопировать содержимое `android/` поверх вашего `android/`.
2. **Удалить** старые `.java` в  
   `android/app/src/main/java/com/datalaw/pdfscanner/`  
   (иначе будет конфликт дубликатов классов).
3. Sync Gradle / `./gradlew assembleDebug`.

Web/JS (`www/`) не трогали — это не нативный код.

## Зачем и что даёт
- **Null-safety** — меньше NPE на URI/stream/cursor.
- Меньше boilerplate (try-with-resources → `.use {}`, data class, object).
- Единый язык с экосистемой Android (Google рекомендует Kotlin).
- Проще дальше: coroutines вместо `Thread {}`, sealed class для ошибок, и т.д.
- Поведение и API плагинов (имена методов, JSON) **не изменились** — JS-сторона работает как раньше.
