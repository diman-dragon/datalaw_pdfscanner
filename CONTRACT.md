# Contract
- Maximum atomicity, no paranoia: one file = one responsibility; one change = one purpose.
- Every change: check atomicity, fix immediately if violated.
- Every edit bumps the version in package.json (0.0.1 -> 0.0.2 ...). 0.1.0 only on owner's command.
- After a bump run `npm run sync` (writes www/js/version.js and android versionName/versionCode).
- CI artifact name: datalaw_pdfscanner_<version>.apk / .aab
