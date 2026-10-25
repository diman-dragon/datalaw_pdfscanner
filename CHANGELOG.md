# Changelog
## 0.0.9
- Fix: the app fell over when a language was chosen (Settings, language): the language context did not lead to the activity, so the system pickers had no owner. Now it is a wrapper of the activity; the strings come from `LocalResources` as well; the same tree for every language
- Fix: the build lost the `BuildConfig` import of the settings screen (after the package move)
- One look: `Dimens` (margins, corners), four text roles (`AppTypography`), one card (`AppCard`) for settings, the library and the scan types; `Seg` can fill the width
- Settings: every setting is a card, the title and the explanation on the left, the control under them across the width; the About card is left-aligned too
- Library: rows and cells are cards like on the scan screens; the name and the date are aligned; the empty state is centred; the view switch is on the right
- The "i" of the title bar is a ring with a letter, not an emoji; the title bar is higher and its title is a card title
- Camera: the close button and the flash are round buttons on dark glass; the flash is a picture (bolt with A, filled bolt, crossed-out bolt)
## 0.0.8
- Audit: packages without circles (low layers <- core <- ui <- features <- app); `core` holds what is shared (graph, dialogs, texts, Deps, camera contract), the shell is `app`, the settings screen is `prefs`, the recognition screen is `recognize`; screens take the narrow `AppActions`; `tools/check_layers.py` guards it (also in CI)
- CameraScreen split: the screen, `CameraEffects` (focus, the shot by itself), `CameraOverlay`, `CameraBars`
- One rule for "the sheet sits right" (`ShotCheck`) for the live outline and the photo; one finder (`SheetFinder`); one "Retake / Save as is" (`askSaveAsIs`); Quick and Scan share them; `ItemFit` is pure
- Failures show a plain reason in the user's language (`userReason`), the raw text goes to the log
- Cancellation of a coroutine is no longer swallowed by `catch (e: Exception)`
- `collectAsStateWithLifecycle` everywhere; the page previews are cached by bytes; the OCR release uses a scope, not a raw thread
- Language data of Tesseract: a fixed release first, the branch as a fallback; a checksum check is ready (`TessData.sha256`, to be filled)
- Light window background (no black flash at start in the light theme); the 10 unused raster launcher icons, the icons dependency, Jitpack and the `.vscode` folder are removed
- Lint report in CI (does not fail the build); the unit tests do not hide Android calls any more; new tests: ShotCheck, ItemFit, FrameMath, Names
## 0.0.7
- Text recognition moved from Settings to the Save dialog of every PDF (Quick, Scan, editor): "Recognize and save" or "Save as is" (the language data is fetched once; if that fails, save as is). The Scan pages get their text when saving
- Image quality: Auto = the best the camera gives (up to 6000 px), now the default
- Camera focus setting removed: the camera always locks the focus on the item when the phone is steady (and again when it moved or the item slid), a tap focuses where the finger is
- Settings: one screen
- Quick and Scan screens: only the modes and the Shoot button; the explanation and the save place are behind the (i) in the title bar
- Scan: "Full auto" preset: no size to pick, the camera shoots by itself when the sheet is seen whole and the phone is still; the page is fitted to the A4 sheet with its shape kept
- Library: the Add button is removed
## 0.0.6
- Scan: the same camera as Quick scan: Frame and Auto modes (one switch for both tabs, also on the screens), live outline of the item, red frame and a warning when the item goes past the frame or the view
- Scan, Auto: the item is found in the photo (it has to have the shape of the chosen type), straightened and put on the A4 sheet at its real size; a card is turned to lie, an A4 / A5 follows its own shape
- Scan screen: the types are a 3 x 2 grid of cells (a picture of the real shape, the name, the size), no scrolling; the mode switch with one line of what it does
- The mode setting is shared (CamMode); EdgeDetector.find takes the smallest share of the picture; PageWarp.warpTo straightens to an exact size
## 0.0.5
- Quick scan: two modes on the camera, Frame and Auto (the switch is remembered). Frame = the fixed A4 frame. Auto = no frame, the camera finds the sheet itself (live outline) and the photo is straightened to a flat page of true proportions
- The camera watches the sheet live: in Frame mode the frame and the outline turn red and the hint says the sheet goes past the frame; in Auto mode it warns when the sheet is cut by the camera view or cannot be seen
- On the shot the same check is made on the photo: a sheet that does not fit (or is not found in Auto) asks "Retake / Save as is"
- If the phone cannot run the live analysis together with the full-quality photo, the camera works as before and the check is made on the shot only
- EdgeDetector.find returns null when no sheet is found (detect keeps the old fallback for the editor)
## 0.0.4
- Quick scan screen: the long paragraph is four short lines with pictograms (A4 frame, maximum camera quality, the PDF image quality from the settings, edge detection and Auto in the editor)
- Quick camera: the frame label says "A4 · max quality"; the hint sits on a dark plate (readable on any background); the flash reads "Flash: Auto"; the shutter has a label for screen readers
## 0.0.3
- Contrast: the app has a root surface now, so every text gets the theme colour (it was black on the black dark background); stronger secondary grey in both themes, visible borders and dividers in dark, a visible menu in dark; the camera text is always white
- Fonts: nothing under 12 sp, secondary text 13 sp
- Library: smaller previews (rows 42x56 dp, cards in 3 columns with the menu on the preview corner); rows / cards switch is two pictograms
## 0.0.2
- Settings: the save folder is a card (folder name, the way to it as Device storage › Documents › Scans, Change button); the same readable way on the Quick and Scan screens
- Settings: "PDF quality" is now "Image quality (dpi)" with a hint; the "never enlarged" line is removed
- Settings: short hints under every option on both pages (theme, language, camera focus, searchable text)
- Size: arm64-v8a only (was arm64 + armv7), compressed native libraries, library languages limited to en / ru, the APK is always a minified release (without secrets it is signed with the debug key)
- Removed 25 unused strings
## 0.0.1
- A new native app (Kotlin, Jetpack Compose, CameraX, OpenCV, Tesseract) built from scratch; the look and the behaviour follow the old Capacitor version 0.2.8
- Library: folder through the Storage Access Framework, list / cards, first-page preview with the number of sheets, menu (Edit, Open in another app, Rename, Delete), Add copies PDFs into the folder
- Viewer: strip and page-by-page modes, pinch / double-tap zoom, text recognition (ru / en / it / sr), edit page / document
- Quick scan: bare camera with a fixed A4 frame, one shot, sharpness check (retake / save as is), file name window, saved at the chosen PDF quality
- Scan: A4, A5, card (2 sides), business card, passport, receipt (several parts), everything on A4 sheets at the real size of the item
- Camera: tap to focus, focus lock on the frame (steady phone detected by the gyroscope), flash auto / on / off
- Document editor: frame corners (automatic edge detection by OpenCV), Original / Auto look, rotation, page order and deletion, replace the original or save a copy
- Image engine on OpenCV: edge detection, perspective correction with the true page proportions, shadow / crease removal ("Auto"), sharpness measure
- Own streaming PDF writer with an invisible text layer (searchable PDF)
- Settings: folder, PDF quality 150 / 300 / 600 dpi, searchable text, theme, language, camera focus
