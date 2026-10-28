# Changelog
## 0.0.23
- Fix (language): dialogs and menus were not translated. Each of them is a window of its own, and Compose starts the language of such a window again from the phone's, so the language chosen in the app never reached it. The language is now handed over to every dialog and every menu (`LocaleBridge`)
- Change: the viewer is gone. A file opens straight in its editor (the grid of pages, tap a page to edit it); the "Edit" item of the file menu is gone with it. Zoom and the strip / pages switch of the viewer went with it
- Add: the way a document is shown is remembered. The button in the title bar switches between "all pages" (grid) and "one page at a time" (swipe to turn, Back closes the document, the title bar has the button back to the grid). The choice is kept for every document, and each document remembers the page it was left on. In the one-page view the main button is "Save" (active when something was changed)
- Remove: text recognition, completely. The recognition engine and its language data download, the screen, the "Recognize and save" choice (Save is now a plain name dialog), the language setting, the invisible text layer of the PDF writer, the Tesseract library, the Jitpack repository and the INTERNET permission (the app no longer asks for the network at all). The pages go to a separate editor as they are
- Fix: the help texts spoke of recognition, of a 16 MP limit and of the "Full auto" cell: rewritten
## 0.0.22
- Change (quality, resolution): the PDF quality is now what it says. 150 / 300 / 600 dpi = 1754 / 3508 / 7016 px along A4 (600 was 4000 px = 340 dpi, with a smaller size budget than Auto); Auto = the most the camera gives, up to 6000 px. The size budgets follow (0.7 / 2.2 / 9 MB per page, Auto 6 MB). The caps of 4000 px in the straightening, the editor and the sheet are lifted (5200 px where the picture is warped: it is held three times in memory)
- Change (camera): the photo is asked of the camera at the size the quality needs (150/300 dpi up to ~16 MP, Auto up to ~27 MP, 600 dpi the highest the camera gives, with the "higher resolution over capture rate" mode)
- Change (memory): a big photo is decoded only when it fits half of what the PHONE has free (/proc/meminfo), the turn counted; otherwise it is decoded smaller, never with a crash. A recognised page is brought to ~300 dpi for the text layer (a 600 dpi page would take minutes)
- Change (sharpness): no more three photos. The photo is taken in a calm: the gyroscope limit follows the shutter time (a slow shutter in the dark asks for a hand that hardly moves, a fast one or the flash forgives much); the phone must stay under it for 120 ms, and the limit loosens by half every second up to 3 s. The screen says "Hold still". Auto-shoot uses the same limit
- Add (sharpness): the optical stabiliser of the lens is switched on for the photos when the phone has one
- Add (sharpness, from the camera advice): in the dark with no flash the exposure is lowered by one stop for the shot (a twice faster shutter, half the blur) and the light is given back in software (a linear lift, clipped at white)
- Fix (language): a message shown after the language was changed in Settings was still in the OLD language (the message line kept the context of the start); the share window of the crash report spoke the language of the phone
## 0.0.21
- Fix (sharpness, found on a real scan: a dim page, smeared text at the bottom, grey noisy paper): the +0.5 EV of 0.0.19 is gone (in the dark it makes the shutter even slower and the shot more blurred)
- Fix: the "Auto" flash of the camera fires only when the shutter is already open as long as it can be, so the shot was blurred before the flash was asked. Now the app reads the shutter time of the preview and fires the flash itself as soon as it is slower than 1/40 s (a hand cannot hold that); On and Off stay as chosen
- Change: in the dark with the flash off the camera takes three photos and keeps the sharpest (before: only when the first was soft)
- Fix: the live search of the sheet (a second camera stream) is bound only in Auto mode; in Frame mode it only cost the camera its biggest photo size (the scan in question was 10 MP). Switching Frame / Auto in the camera re-opens the camera for a moment
- Fix: swipe in the page editor: a sideways swipe on the page goes to the next or the previous page (the hint promised it, the code was not there); a drag that starts on a corner still moves the corner
- Fix: Back on the Quick, Scan or Settings tab goes to the Library first and only there leaves the app
## 0.0.20
- Fix: the build failed: `PdfPageView.kt` (split out of the viewer) lacked the `fillMaxSize` import
## 0.0.19
- Quality, camera: half a stop more exposure for documents (the meter takes white paper for grey and under-exposes it: the paper is whiter, the shot less noisy)
- Quality, camera: a soft shot is no longer shown to the person at once: the camera takes up to two more photos and keeps the sharpest; "Retake / Save as is" is asked only when even the best one is soft
- Quality, camera: the tap on the shutter shakes the phone, so the photo waits for it to settle (a moment, at most half a second)
- Quality, picture: a page is brought from the 16 MP shot to its size with cubic interpolation (was linear, which blurs small text) and a light unsharp mask
- Quality, compression: the JPEG starts at quality 92 (was 82): a clean page, which is small anyway, keeps crisp text; a noisy photo still goes down to what the size budget allows (5 steps of search instead of 4)
## 0.0.18
- Add: the full crash report. When (and only when) the last run ended badly (an uncaught exception, a native crash, "not responding", killed for memory while on the screen) the app starts with a window "The app closed unexpectedly": what to do ("contact the developer and pass this report"), the whole report, and the buttons Copy / Send / Close. The report stays until it is closed
- Add: the report holds the version and build, the device, Android and ABI, language, the time, the age of the process, the Java and native heap, the free memory of the device, the stack with its causes, how the process ended, and the trail of the last 40 things done (tabs, screens, dialogs, saves, shots), kept in a small file (`Breadcrumbs`)
- Atomicity: `CameraScreen` is split (the permission, the shot with its sharpness and sheet check, the hint and the colour of the frame)
## 0.0.17
- Add: page looks like in the scanner apps, all by plain image processing (no neural network): "Auto" (uneven light and soft shadows), "No shadows" (also the folds and creases of the sheet; paper pure white), "Gray", "B/W" (black text on white); the switch of the editor scrolls sideways
- Change: Scan tab: first the switch Frame / Auto, under it the ready sizes (only for Frame), last "Shoot"; everything stands in the middle of the screen. The separate "Full auto" cell is gone: "Auto" is the full automatic scan. Quick scan stands in the middle too
- Change: Settings: a setting is one card, a tap on it changes the value (shown on the right); About is centred
- Fix: the label of "Save" in the document editor was broken into pieces: buttons come from one kit (PrimaryButton, SecondaryButton) and keep their label on ONE line; "Save PDF" is "Save", "Delete page" on a button is "Delete", the duplicate "Cancel" of the document editor is gone (the close button is in the title); stale texts fixed (the hint of the editor spoke of a swipe, the empty library of an "Add" button that does not exist)
- Atomicity: the viewer is split (page view, zoom gestures, the maths of the zoom focus), the page editor too (geometry, safe extras, look names); the maths has unit tests; text sizes and glyph sizes come from the theme roles and `Dimens`; `check_layers.py` also rejects a raw Material button in a feature
## 0.0.16
- Change: saving no longer keeps the camera open. After the name is chosen the camera closes at once and the Library opens; the page is finished, recognised and written in the background and the progress is a line with a bar at the top of the app (also for editing a document)
- Change: the page editor has no small preview window any more: the page itself shows the turn and Original / Auto at once, the frame is drawn over it and the outside of the frame is darkened
- Add: the page editor has "Auto frame" and "Whole page" buttons (the double tap still finds the frame)
- Change: the viewer and the page editor share one tool row (turn left / right + a switch: Strip / Pages in the viewer, Original / Auto in the editor) and the same title; the viewer can turn a page to look at it (the file is not changed)
- Change: viewer zoom: a double tap zooms in at the place tapped and the next one returns to the normal size; two fingers zoom around the point between them; the page is drawn 1.5x wide from the start and a new size is drawn only after the zoom stops, so zooming never waits; drawn pages are cached
## 0.0.15
- Fix: `local.properties` (a Windows SDK path of one machine) is no longer in the repository and is listed in `.gitignore`
- Diagnostics: R8 is off for the release build while the `NullPointerException` in a main-thread coroutine is hunted (the crash report showed only `a0.y1.g`, no class names); it also rules R8 in or out as the cause. The R8 mapping is kept as a CI artifact for when it is turned back on
## 0.0.14
- Fix: the Save and Rename windows asked for the keyboard before their own window had composed the text field (`FocusRequester is not initialized` closes the app, and it was thrown outside any guard): the request now waits a moment and can no longer throw
- Fix: in the page editor the preview, the sharpness warning and the automatic frame ran without a guard: a failure of memory or of OpenCV closed the app, now the editor goes on without that extra
- Fix: Back right after scrolling a PDF closed the file under the renderer that was still drawing a page (`PdfReader.close` never waited for it): the document now closes when that page is done
- Fix: a second tap on Shoot while the first one was still checking the folder put a second camera over the first
- Fix: `EdgeDetector.find` released its RGBA buffer only when nothing went wrong (the live search runs it five times a second)
## 0.0.13
- Fix: a Scan page could never be finished (the app hung on "Saving..."): `PageRenderer.placed` took the one-permit gate twice (0.0.12 made it one permit); the extra `serialized` wrapper is gone
- Fix: the photo is taken at up to ~16 MP (was: the highest the camera lists, 50-200 MP on many phones): the PDF page never holds more than 4000 px and 4 MB, but every decode, rotation and crop of a huge JPEG got the app killed for memory on the shot
- Fix: a failed text recognition (memory, native library) no longer loses the scan: the page is saved without the text layer
- Fix: a crop of the whole picture recycled the bitmap that was then compressed (`PageRenderer.raw`)
- Fix: the camera is not bound when the screen was closed before the camera provider was ready
- A crash is never silent: an uncaught exception is saved by `CrashReport` and, together with how the last process ended (Android 11+: native crash, killed for memory, not responding), told at the next start in plain words, with "Copy details" for the developer
## 0.0.12
- Fix: the app fell over when shooting (OOM): `largeHeap` for the full-resolution photos, one heavy render at a time (`PageRenderer` gate 2 -> 1), OpenCV Mats released on any failure (`Cv.bitmap`, `EdgeDetector`, `Enhance`, `PageWarp`), live frames no longer pile up in memory while the camera takes the photo (`SheetWatcher` keeps one frame in flight)
## 0.0.11
- Fix: the build (the tap handler of the camera overlay went to the wrong parameter)
- Frame mode is only the frame: no live search, no outline, no comparison of the sheet with the frame, no check after the shot (`SheetFit.outside`, `ShotProblem.Outside` and their texts are gone); the analysis runs in Auto mode only
- All screens are made of one kit (`ui/ScreenKit`: TitleBar, BottomBar, GlyphButton, OverflowMenu, CenterNote, PageBadge) with `AppCard`, `Seg` and the theme: the viewer, the document editor, the page editor and the recognition screen have nothing of their own (title bar, text roles, cards, buttons, colours of the handles and the outline from the theme); the language choice of the recognition is a row of cards
## 0.0.10
- Fix: the build could not find Tesseract4Android 4.9.0 (it is not on Maven Central): Jitpack is back, limited to that one group
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
- Light window background (no black flash at start in the light theme); the 10 unused raster launcher icons, the icons dependency, the `.vscode` folder are removed
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
