# Changelog
## 0.1.5
- Camera: the frame is back and mandatory: live auto-detection of the edges, draggable corners, double-tap = automatic again; the shot itself is still the ORIGINAL at the maximum camera quality (the preview is matched to the photo's aspect so the frame maps exactly; if it still does not fit, the edges are found automatically on the photo)
- Quick scan: shot -> result screen (filter, rotation, edges, Retake) -> file name -> save; full-size render up to 4200 px
- Scan tab: the frame is stored with every page; on Done you pick the filter for all pages; tap a page (or its menu) to adjust edges / filter / rotation; the heavy warp of each page runs in the background so Save only writes the file
- Library / viewer: document editor and text recognition stay as in 0.1.3
## 0.1.4
- Build fix: Tesseract4Android is on JitPack (not Maven Central): JitPack repository added, library 4.9.0

## 0.1.3
- Everywhere (Quick, Scan, camera, gallery) only the ORIGINAL is captured, at the maximum camera quality: ImageCapture.takePhoto at the largest photo size (full video frame as a fallback); no frame, no filters, no downscaling. The camera screen no longer shows the edge frame or filters
- The original JPEG goes into the PDF untouched (EXIF orientation -> /Rotate), so saving is fast and lossless; PDF is streamed page by page (low memory)
- Editing moved to the Library and the viewer: new document editor (Library menu / viewer menu -> Edit): per page edges, filter, rotation, reorder, delete; Save replaces the original or saves a new file
- Scan tab: pages can only be deleted; the edge/filter editing there is removed
- Full-size edge result: the preview is fast, the full-size warp (up to 4000 px) runs on Save
- Text recognition (OCR) in the viewer menu: Russian, English, Italian, Serbian (Cyrillic + Latin), this page or the whole document, copy the text. Tesseract (tesseract4android); language data is downloaded once on first use (needs internet)
- Viewer: header menu (Edit / Recognize text / Open in another app); the menu sheet now sits above all overlays
- Native: OcrPlugin.java, PdfPreview.renderPage, viewPage quality parameter
## 0.1.2
- Camera timer in the Scan tab is a series: press it once and pages are shot automatically every N seconds (countdown on screen) until you press Stop or close the camera; in Quick scan it still shoots one page
- Viewer: pinch to zoom (1x-4x) around the fingers; +/- buttons stay
- Viewer: two modes, switch in the bottom bar (remembered): strip (vertical scroll) and page by page (swipe left/right, page fitted to the screen)
- Viewer: pages are rendered at the real on-screen size (sharp after zoom); page counter works in both modes
## 0.1.1
- Camera: the unclear "Auto" button is now a self-timer (shoots after N seconds with an on-screen countdown, tap again to cancel); the delay 3 / 5 / 10 s is chosen in Settings (default 5)
- Camera: double-tap outside the corners returns the frame to automatic detection; the edge editor's "Auto" is renamed "Detect edges"
- Saving asks for a file name (suggests Scan_<date>_<time>); Cancel returns to the result screen (Quick) or keeps the pages (Scan)
- Faster save in the Scan tab: every page is rendered to JPEG in the background right after it is shot/edited, Save only writes the file
- Settings: SD card folders are shown as SD\path; the folder hint text is removed
- Library: built-in light PDF viewer (vertical pages, lazy rendering, zoom 100-300%, Back closes it); "Open in another app" stays in the menu and in the viewer
- Native: PdfViewer.java + Folder.viewOpen / viewPage / viewClose
## 0.1.0
- Multi-page scanning in the Scan tab: continuous camera (counter, flash), several images from the gallery, page grid
- Per page: edit (edges, filter, rotation), move left/right, delete; clear all; save everything as one PDF
- Native streaming save (saveOpen/saveWrite/saveClose/saveAbort) replaces single-shot save: big PDFs go in chunks
- Result screen returns {canvas, quad, filter, turns}; shared save.js (folder guard, file name, PDF save)
## 0.0.9
- Single-page scanner moved to the Quick scan tab; the Scan tab is a placeholder again
- Own camera screen: live preview, live auto document frame, draggable corners, shutter (system camera is the fallback)
- Result screen: filters (Color / Gray / B&W), rotate left/right, edge re-editing, retake, save as PDF
- Engine: filters.js (rotation, auto-levels, adaptive B&W), detect.js works at any preview size
- Removed www/js/scan/controller.js (replaced by quick.js)
- Camera effects row: Original (default, no filter), Contrast, Gray, B/W; live CSS preview, the chosen effect is applied to the scan by the engine and can be changed on the result screen
## 0.0.8
- Scanner (single shot): camera or gallery -> auto edge detection (own engine) -> manual corner editor -> perspective correction -> PDF saved into the selected folder
- If no folder is selected, the Scan tab offers to choose one
- Own modules: www/js/scan/{load,detect,warp,pdf,editor,controller}.js; native Folder.save; CAMERA permission
- System Back closes the edge editor
## 0.0.7
- Settings: selected folder is shown (name + path, derived from the URI if the provider gives no name); pick errors are reported
- Library: list/cards switch is pictograms only; folder name no longer shown
- CI: fails if android/debug.keystore is missing; prints the APK signing certificate SHA-256 (must stay identical between builds, otherwise Android refuses the update)
## 0.0.6
- Library: PDFs from the selected folder as list rows or cards (switch), first-page previews, add (copies into the folder), rename, delete, open
- Settings: PDF folder picker (Android Storage Access Framework); scans will be saved there
- Native plugin Folder (FolderPlugin.java, PdfPreview.java), registered in MainActivity
- System Back closes the action sheet first
## 0.0.5
- New app icon: document in scanner frame with scan beam (drawn from scratch, no character)
- Bottom bar: text labels under emoji (ru/en)
## 0.0.4
- Debug builds signed with a fixed open keystore (android/debug.keystore): new APK installs as an update, no reinstall
- Debug file name: datalaw_pdfscanner_<version>_debug.apk; artifact name includes version
## 0.0.3
- App icon (adaptive + legacy + Play 512): character cut from source, solid background, no text/progress elements
- Bottom bar and empty pages: emoji are bundled SVG images (Noto Emoji) instead of a font
- Removed emoji font (www/fonts, www/css/fonts.css)
## 0.0.2
- npm run dev: local preview server
## 0.0.1
- Capacitor Android skeleton, GitHub Actions build
- 4 swipeable tabs with emoji bar, narrow header
- Settings: theme (sand light / AMOLED dark), language (auto / ru / en)
- System Back -> Library, stays there
