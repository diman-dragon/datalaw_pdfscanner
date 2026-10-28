# DataLaw PDF Scanner (native)

Android PDF scanner: Kotlin, Jetpack Compose, CameraX, OpenCV. Rules of the project: [CONTRACT.md](CONTRACT.md).

## Build

GitHub Actions builds on every push to `main` (`.github/workflows/build.yml`). The APK / AAB are in the run artifacts.
Without the signing secrets a debug APK is built (signed with the committed `debug.keystore`, so builds update each other).
Secrets for a release build: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## VS Code (no Android Studio needed)

1. JDK 17 or 21 (`java -version`).
2. Android command-line tools; set `ANDROID_HOME` and install:
   `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"` (and accept licenses: `sdkmanager --licenses`).
3. Open the folder in VS Code, install the recommended extensions.
4. `Ctrl+Shift+B` = `assembleDebug`; the task list also has `installDebug`, `test`, `assembleRelease`, `adb logcat`.
5. A phone with USB debugging (or an emulator) for `installDebug`.

## Layout

`app/src/main/java/com/datalaw/pdfscanner/`

| package | what |
|---|---|
| `settings` | DataStore settings |
| `data` | Storage Access Framework folder, names |
| `pdf` | PDF reader (PdfRenderer) and the streaming PDF writer |
| `imaging` | OpenCV engine: detect, warp, enhance, sharpness, JPEG budget, A4 sheet composer, page renderer |
| `camera` | CameraX rig, frame overlay, gyroscope |
| `scan` | Quick / Scan sessions, scan types, saving |
| `library`, `docedit`, `settings` | screens |
| `ui` | theme, dialogs, navigation state |
