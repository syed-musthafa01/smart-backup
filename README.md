# Smart Backup

An Android app that scans your device's files, ranks them by importance using an on-device TensorFlow Lite model, and lets you back up the selected files to Google Drive.

---

## How it works

```
[ App opens ]
     │
     ▼
[ FileScanner ] ──scans device storage recursively──▶ List<FileModel>
     │
     ▼
[ FilePriorityEngine ] ──runs each file through a TFLite model──▶ assigns priority (0/1/2)
     │
     ▼
[ FileAdapter / RecyclerView ] ──displays files, lets user select some──▶
     │
     ▼  (user taps "Backup")
[ Google Sign-In ] ──OAuth, requests drive.file scope──▶
     │
     ▼
[ DriveRestUploader ] ──uploads each selected file via Drive REST API──▶ Google Drive
```

1. **On launch**, `MainActivity` requests storage permission, then triggers a scan.
2. **`FileScanner`** recursively walks the device's external storage, building a list of `FileModel` objects (name, path, size, type — image/video/audio/document/other based on file extension).
3. **`FilePriorityEngine`** loads a bundled TensorFlow Lite model (`priority_model.tflite`) and, for each file, extracts numeric features (`FeatureExtractor`) and runs them through the model to assign a priority class (3 classes: low/medium/high — exact meaning depends on how the model was trained).
4. **`FileAdapter`** binds the file list to a `RecyclerView`, letting the user browse and select which files to back up.
5. On tapping **Backup**, the app launches **Google Sign-In** (scoped to `drive.file`, so it can only access files it creates — not the user's whole Drive).
6. **`DriveRestUploader`** takes the selected files, fetches an OAuth access token, and uploads each file directly to the Google Drive REST API (`multipart` upload) on a background thread.

---

## Project structure

```
smart-backup/
├── app/
│   ├── build.gradle.kts                  # App module config, dependencies, SDK versions
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/                   # Bundled TFLite model (priority_model.tflite)
│       │   ├── java/com/example/gptbackup/
│       │   │   ├── ui/
│       │   │   │   └── MainActivity.java         # Entry point — permissions, sign-in, orchestration
│       │   │   ├── scanner/
│       │   │   │   └── FileScanner.java          # Recursively scans device storage
│       │   │   ├── model/
│       │   │   │   └── FileModel.java            # Data class: name, path, size, type, priority
│       │   │   ├── ai/
│       │   │   │   ├── TFLiteModelLoader.java     # Loads the .tflite model from assets
│       │   │   │   ├── FeatureExtractor.java      # Converts a FileModel into model input features
│       │   │   │   └── FilePriorityEngine.java    # Runs inference, assigns priority per file
│       │   │   ├── adapter/
│       │   │   │   └── FileAdapter.java          # RecyclerView adapter, tracks user selection
│       │   │   └── backup/
│       │   │       ├── SystemStatusChecker.java   # (system/device status checks)
│       │   │       ├── SmartBackupManager.java    # Coordinates the backup flow
│       │   │       └── DriveRestUploader.java     # Uploads files to Google Drive via REST API
│       │   └── res/                      # Layouts, strings, drawables
│       ├── test/java/                    # Unit tests
│       └── androidTest/java/             # Instrumented (on-device) tests
├── gradle/
│   ├── libs.versions.toml                # Centralized dependency version catalog
│   └── wrapper/
├── build.gradle.kts                      # Project-level Gradle config
└── settings.gradle.kts
```

> Note: the package name is `com.example.gptbackup` — a holdover from early development, harmless but worth renaming before any release/publish step.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 11 |
| Platform | Android (minSdk 24, targetSdk 36, compileSdk 36) |
| UI | AppCompat, Material Components, ConstraintLayout, RecyclerView |
| On-device ML | TensorFlow Lite 2.14.0 |
| Auth | Google Sign-In (`play-services-auth`), Firebase Auth |
| Networking | OkHttp 4.12.0 (manual multipart upload to Drive REST API) |
| Cloud storage | Google Drive REST API v3 (`drive.file` scope) |
| Build | Gradle (Kotlin DSL), version catalog (`libs.versions.toml`) |

---

## Setup & running locally

### 1. Prerequisites
- Android Studio (recent version, supports compileSdk 36)
- A Google Cloud project with the **Drive API** enabled
- An OAuth client configured for the app's package name + SHA-1 signing certificate

### 2. Open the project
Open the `smart-backup/` folder directly in Android Studio — it will sync Gradle automatically using the wrapper (`gradlew`).

### 3. Configure Google Sign-In
The app requests the `https://www.googleapis.com/auth/drive.file` scope. For Google Sign-In to work:
- Register the app's SHA-1 fingerprint and package name (`com.example.gptbackup`) in your Google Cloud / Firebase project
- Ensure the Drive API is enabled for that project

### 4. Provide the TFLite model
`FilePriorityEngine` expects a file named `priority_model.tflite` bundled in `app/src/main/assets/`. Confirm this file is present and matches the feature shape expected by `FeatureExtractor`.

### 5. Build & run
```bash
./gradlew assembleDebug
```
Or just hit **Run** in Android Studio on a connected device/emulator (API 24+).

### 6. Grant permissions
On first launch, grant storage read permission when prompted — the scan won't run without it.

---

## Permissions used

- `READ_EXTERNAL_STORAGE` — to scan files on device
- Google `drive.file` OAuth scope — to upload backed-up files (app can only see/manage files it creates, not the user's full Drive)

---

## Known limitations / next steps

- **`READ_EXTERNAL_STORAGE` is deprecated on Android 13+** (API 33+) in favor of scoped/granular media permissions — since `targetSdk` is 36, this likely needs updating to the modern permission model for the scan to work reliably on newer devices.
- **Package name `com.example.gptbackup`** is a placeholder and should be changed before any real release.
- **No retry/resume logic** for failed Drive uploads — `DriveRestUploader` logs failures but doesn't retry.
- **No visible upload progress** — uploads happen on a background thread with only a single "Backup started" toast; no per-file progress UI.
- **Priority model behavior is opaque** — the meaning of the 3 priority classes depends entirely on how `priority_model.tflite` was trained; this isn't documented in-app.

---

## Credits

Built by [syed-musthafa01](https://github.com/syed-musthafa01) with assistance from AI coding tools and AI models for implementation.
