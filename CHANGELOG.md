# Changelog
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
