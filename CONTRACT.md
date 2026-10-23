# Contract
- Maximum atomicity, no paranoia: one file = one responsibility; one change = one purpose.
- Every change: check atomicity, fix immediately if violated.
- Every edit bumps the version in `version.properties` (0.0.1 -> 0.0.2 ...). 0.1.0 only on owner's command.
- `versionName` and `versionCode` are derived from `version.properties` by Gradle (code = major*10000 + minor*100 + patch). No sync step.
- CI artifact name: datalaw_pdfscanner_<version>.apk / .aab (debug build without secrets: datalaw_pdfscanner_<version>_debug.apk).
- Every edit adds a line to CHANGELOG.md under the new version.
- Build only through Gradle (`./gradlew`), locally from VS Code tasks or on GitHub Actions. No Android Studio project files are committed.
