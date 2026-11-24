# Kept (Android)

Snap a receipt, and Kept reminds you before the return window or warranty runs out.
Local-first: receipts stay on the phone, no account, no ads.

Current version: **0.2.1** (see [CHANGELOG.md](CHANGELOG.md)). The app scans, reads, checks and saves
receipts, reminds you before return windows close and warranties end, deletes receipts with Undo, and
updates itself. Export and backup come next.

## Open it

1. Install **Android Studio** (latest stable). It brings the Android SDK and JDK 17.
2. **File → Open** and pick this folder. Let Gradle sync (first time takes a few minutes).
3. Plug in an Android phone with **USB debugging** on (Settings → About phone → tap *Build number*
   7 times → Developer options → USB debugging). A real phone is faster than the emulator on most
   laptops, and the scanner needs Google Play services.
4. Press **Run ▶**.

If sync complains about a version, accept Android Studio's suggested upgrade; the versions in
`gradle/libs.versions.toml` are pinned to known-good stable releases.

## Cloud builds (no computer needed)

Every push to `main` runs `.github/workflows/ci.yml` on GitHub Actions:

1. runs the parser tests and builds the debug app;
2. writes the result to the **`ci-logs`** branch (`status.txt`, `summary.txt`, `tail.txt`,
   `build-log.txt`) so errors can be read without the Actions screen;
3. if the build passes, publishes a **Release** named "Kept <version> (test build N)" with the `.apk`
   attached, and the same build to the public **kish523/kept-releases** repo, where the app's updater
   finds it. The run number N is the build number (`versionCode`), so every build is newer than the last.
   Open the release on your phone, tap the `.apk`, and install it. Builds are signed with the
   committed `app/debug.keystore`, so each one installs over the last and keeps your data.

### In-app updates: one-time setup

This repo is private, so phones can't read its releases. Builds are also published to a small **public**
repo that holds only APKs and release notes, never source code:

1. On GitHub, create a new repository **kept-releases** under your account, set to **Public**, and tick
   **Add a README** (a release needs at least one commit to attach to).
2. Create a token for CI: GitHub › Settings › Developer settings › **Fine-grained tokens** › Generate new
   token. Repository access: **Only select repositories › kept-releases**. Permissions: **Contents: Read
   and write**. Pick an expiry you're comfortable with (it has to be renewed when it expires).
3. In **kept-android** › Settings › Secrets and variables › **Actions** › New repository secret:
   name `RELEASES_TOKEN`, value: the token.

Without the secret, builds still publish here as before, and the log shows a warning that the
public copy was skipped. The app checks `api.github.com/repos/kish523/kept-releases/releases` (set in
`app/build.gradle.kts` as `UPDATE_REPO`) at most every few hours and once a day in the background,
downloads a newer APK, checks it's Kept, newer, and signed with the same key, then installs it. On
Android 12+ that's silent after the first self-update; the first time, and on older phones, Android
shows one confirm dialog. "What's new" reads the bundled `CHANGELOG.md` after each update.

The Gradle wrapper jar isn't in the repository (it's binary). CI uses Gradle 8.14.3 directly, and
Android Studio reads `gradle/wrapper/gradle-wrapper.properties`. To add the wrapper scripts back on
a computer, run `gradle wrapper --gradle-version 8.14.3` once.

## What's here

```
core/parser/   Pure Kotlin. No Android. Tests run anywhere in seconds.
  parser/        ReceiptParser: OCR text -> store, date, total, tax, items, payment,
                 order and serial numbers, return/warranty hints, category.
                 Every field carries a confidence score; low scores get highlighted.
  deadlines/     DeadlineCalculator (return-by / warranty-until, shortest window wins)
                 ReminderPlanner (7/3/1 days before returns, 30/7 before warranties, 9:00 local)
app/           Android app (Kotlin, Jetpack Compose, Material 3)
  data/db/       Room database: receipts, items, attachments, reminders, tags, groups, search index
  data/files/    AttachmentStore: atomic, fsync'd file writes; PDF pages rendered to images
  capture/       OcrEngine (ML Kit) and ExtractionPipeline (draft -> OCR -> parse -> pre-fill)
  data/settings/ Light / dark / match phone; update and what's-new flags
  reminders/     AlarmReminderScheduler (inexact idle-safe alarms from reminder rows), notifications with
                 Keeping it / Returned it / Remind me tomorrow, ReminderReceiver (alarms, buttons, boot,
                 app update, clock and time-zone changes), DailyWorker (repair, purge, update check)
  update/        Updater (GitHub releases feed, download, verify, PackageInstaller), What's new parser
  ui/theme/      Kept palette (light + dark), Plus Jakarta Sans, motion curves, line icons
  ui/components/ Chips, pills, press feedback, entrance animations, glow, glass snackbar
  ui/home/       Home: Next up card, search, filter, Returns closing, Warranties ending, settings
                 (HomeModel.kt holds the sorting logic, unit-tested)
  ui/review/     Review card: photo, amber "Check this" fields, quick dates, return/warranty chips,
                 reminder preview, morphing Save
```

### The capture path

1. **Scan** (Google's ML Kit document scanner: edge detection, multi-page, no camera permission),
   or **share** an image / PDF into Kept from Gmail, Files, Photos or Drive.
2. Files are copied into app storage and a **DRAFT** row is written *before* any reading,
   so a crash or a dead battery never loses a capture.
3. **ML Kit text recognition** runs on the phone. Full card numbers are redacted immediately.
4. The **rules parser** reads the fields and scores them.
5. The **review card** shows the photo and the fields; unsure ones are amber with "Check this".
6. **Save** writes items, reminder rows and the search index in one transaction.

## Tests

```
./gradlew :core:parser:test :app:testDebugUnitTest
```

- `:core:parser:test`: the parser and deadline rules.
- `:app:testDebugUnitTest`: Home's sorting logic, the database migration test, the reminder flow
  against real alarms and notifications, deleting and clean-up, the update feed and the changelog
  (Robolectric where Android is needed).
- Add `-Pscreenshots` to also render Home and the review card, light and dark, to
  `app/build/screenshots/`. They're for looking at, not pixel-compared.

`GoldenSetTest` runs the parser over every receipt in
`core/parser/src/test/resources/golden/` and prints accuracy per field. The 8 receipts there now
were written by hand to cover common layouts (US, UK, EU, Sri Lanka, India GST, an order email,
a faded receipt with no total line). **They are not a real accuracy measure.** Replace and extend
them with real receipts:

- `name.txt`: the recognised text (copy it from the app's OCR output or type it out)
- `name.expected`: the correct answers, one per line, for example

```
config=LKR,DMY
merchant=Lanka Home Centre
date=2026-11-18
total=227032.00
return=none            # or days:15, or date:2026-12-24
warranty=months:24
items=2
```

The test fails below 90%, which is the roadmap's gate for the extraction phase.

## Next steps (from the spec)

- [x] **Reminders** (0.2.1): alarms, notification actions, re-arm on boot/update/clock change, daily repair.
- [x] **Delete** (0.2.1): swipe or bin with Undo; clean up finished receipts.
- [x] **Self-update** (0.2.1) from kish523/kept-releases, with What's new.
- [ ] Calendar mirror (insert intent, no permission).
- [x] Redesign: theme, icon, Home, review card, motion (0.2.0).
- [x] "Keeping it" / "Returned it" with undo (from the Next up card; notification actions come with reminders).
- [ ] Item detail screen (tapping a Home row currently reopens the review card for editing).
- [ ] Export (ZIP / CSV / PDF), encrypted `.keptbackup`, delete-everything.
- [ ] App Lock (BiometricPrompt), vault encryption (SQLCipher), secure window flag.
- [ ] Gemini Nano tier via the ML Kit Prompt API on supported phones, checked by the rules parser.
- [x] App icon (adaptive, with a themed-icon layer: `res/drawable/ic_launcher_*.xml`).
- [ ] Launch screen.
- [ ] Real merchant return policies in `app/src/main/assets/merchant_policies.json`
      (the two entries there are made-up examples).

## Notes

- Minimum Android 8.0 (API 26), target API 36.
- Money is always integer minor units + ISO currency code. Deadlines are plain local dates.
- Never add `fallbackToDestructiveMigration()` to the database: it deletes users' receipts.
  Every schema change gets a numbered migration and a test against the exported schema in
  `app/schemas/` (see `MigrationTest`). Bump `versionCode` with every release so phones upgrade in place.
- Fonts: Plus Jakarta Sans, SIL Open Font License (`third_party/PlusJakartaSans-OFL.txt`).
