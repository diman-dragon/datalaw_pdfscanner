# Changelog
## 0.1.11
- PDF quality in Settings: 150 / 300 / 600 dpi. The PDF is always made from the ORIGINAL photo (frame -> straighten -> Auto), never from an already compressed picture; each page is compressed to a size budget (about 300 KB at 150 dpi, 800 KB at 300, 2.2 MB at 600); pages are never enlarged beyond what the camera captured, so 600 dpi needs a camera that really has the pixels
- "As if shot parallel": the straightened page now gets its real proportions (the focal length is recovered from the sheet's perspective, a typical phone lens is assumed when that is impossible) - the error of the page proportions drops from up to 35% to about 3% in the worst tilt
- Warnings: in the camera - "tilted, hold the phone parallel" (from the frame) and "hold steadier" (gyroscope; if the tap shakes the phone the shutter waits up to 0.25 s for calm); in the editor - "the shot may be blurry" and "strong tilt N%"
- Editing existing PDFs renders pages at the chosen PDF quality instead of a fixed large size
## 0.1.10
- Fix "could not save the scan": the clean-up of a full-size page needed several hundred MB; it now works in strips (about 3x faster, low memory); if the device still runs out of memory a smaller render is tried, then the plain crop - saving no longer fails because of it; the real error text is shown in the message
- After the shot only two looks: Original and Auto (Auto fixes shadows, fold shading, creases, uneven light and sharpens); all sliders / extra filters removed
- The frame is right on the editor screen: drag the corners, double-tap = automatic frame; no separate "Edges" button / screen; a small live preview of the result (tap it to see the result large, tap again to return)
- Batch editor: "Next / Save" is always on the left, "Back" on the right; swipe left = next page, swipe right = previous (a swipe never saves on the last page)
- Viewer (paged mode) has its own gestures: swipe left / right flips the page, pinch zoom, drag when zoomed, double-tap zoom
- Camera: the "max resolution" sensor mode and its setting are removed (the shot is always the exact frame at the moment of the press)
## 0.1.9
- App icon: the same look (dark background, scanner corners, beam) but the page is now a scroll with rolled ends; all launcher / round / adaptive / splash assets regenerated
- Page editor redesigned: filter cards with live thumbnails of the current page (Original, Auto, Clean, Gray, B/W), an Adjust tab (shadows, folds & creases, brightness, contrast, sharpness), rotate, edges, Reset, hold "Original" to compare
- New enhancement engine (scan/enhance.js): removes shadows, fold shading, uneven light, paper grain and pale creases (flat-field + white point), soft B/W, unsharp mask
- Camera, WYSIWYG: the shot is the exact video frame at the moment of the press (no waiting, nothing is shot later), at the highest stream resolution; the stillness wait of 0.1.8 is removed; Settings > Shot mode: Instant / Max resolution (full sensor photo, slower)
- Camera, smoothness: edge detection runs on a small reused canvas and slows down once the frame is steady; the frame is eased and ignores small jitter; torch button where supported; continuous focus
- Working size 3600 px (300 dpi on A4) for faster saving
## 0.1.8
- Camera, steadiness: the on-screen frame no longer jumps - small detection jitter (< ~1.2% of the diagonal) is ignored, bigger moves are eased frame by frame
- Camera, sharpness: continuous focus / exposure / white balance where the camera supports them; the shutter waits until the picture is still (~0.2 s, up to 1.2 s) so the shake of the tap does not blur the shot; the full-size photo settings are awaited before the shot; flash is off; the frame is taken at the moment of the shot
- After a successful save (Quick and Scan) the app switches to the Library tab
## 0.1.7
- Scan tab: after Done the pages are edited exactly like a single scan - frame, filter, rotation per page, Back / Next, the caption "Page i of n" at the bottom, the last button saves the PDF; the filter chosen on a page carries over to the next one; tap a page in the overview to adjust only it
- One shared page editor (scan/pagesedit.js) is used by the Scan tab, the Library editor and the viewer
- Viewer: page buttons (previous / next) in the bottom bar for both modes, all touch listeners passive, no nested scrolling blocks in paged mode; Edit this page (pencil button and menu), Edit the document (page grid: edit all, reorder, delete, save / replace)
- Viewer / Library editor: pages are rendered only when needed; untouched pages keep their JPEG as it is; after saving the viewer reopens the new file
- Fix: the page preview in the result and edge screens no longer runs under the right edge (stage padding)
- Result screen can delete a page (trash icon) in sequence mode
## 0.1.6
- Camera fix (black screen): the preview opens exactly as in the working 0.1.0 (one 3840x2160 stream, frame and overlay set up at once); ImageCapture (full-size photo) is attached in the background and can never block or restart the preview; no more re-opening of the stream; takePhoto has an 8 s timeout and falls back to the full video frame

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
