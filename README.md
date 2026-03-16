# SmartBackup for Android

SmartBackup is an intelligent, AI-driven Android application designed to safely and efficiently back up your local files to Google Drive. It leverages user behavior tracking and TensorFlow Lite models to prioritize which files should be backed up first, ensuring your most important data is always secure.

## 🚀 Key Features

- **AI-Driven Prioritization**: Uses TensorFlow Lite (`TFLiteModelLoader`) and a `FilePriorityEngine` to determine the importance of files based on user behavior and file metadata.
- **Seamless Google Drive Integration**: Directly uploads your files to Google Drive using robust REST APIs (`DriveRestUploader`) and OAuth 2.0 authentication.
- **Reliable Background Uploads**: Utilizes an Android Foreground Service (`UploadForegroundService`) to ensure backups continue smoothly even when the app is minimized.
- **Comprehensive File Scanning**: Efficiently scans the device for media and documents using both `MediaStoreLoader` and custom file scanning logic.
- **Local Tracking**: Maintains a local Room Database (`BackupDatabase`) to track the synchronization status of every file and prevent redundant uploads.
- **Modern UI**: Built with a responsive and user-friendly interface using standard Android components, Material Design, and ViewBinding.

## 🛠️ Technology Stack

- **Language**: Java
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36
- **Architecture & UI**: ViewBinding, Material Components
- **Database**: Room Persistence Library & SQLite JDBC
- **Machine Learning**: TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.14.0`)
- **Networking**: OkHttp3
- **Authentication**: Firebase Auth & Google Play Services Auth
- **Image Loading**: Glide

## 📂 Project Structure

- `ai`: Contains the machine learning logic, behavior tracking, and file priority engine.
- `backup`: Handles the core backup decision engine, file uploading logic, and foreground services.
- `db`: Room database configuration, Data Access Objects (DAOs), and local entities for tracking backup statuses.
- `scanner`: Utilities for scanning the device storage and identifying files that need backing up.
- `ui`: Activities and UI controllers (e.g., `MainActivity`, `SmartBackupActivity`, `SettingsActivity`).
- `model`: Data structures representing files and system states.
- `adapter`: RecyclerView adapters for listing files and backup progress.
- `preferences`: User preference management.

## 🔒 Permissions Required

To function correctly, SmartBackup requires the following permissions:
- **Storage Access**: `MANAGE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` to locate and read files across the entire device storage (Note: `MANAGE_EXTERNAL_STORAGE` is intended for project/development use and has specific Play Store policies).
- **Network Access**: `INTERNET` and `ACCESS_NETWORK_STATE` to communicate with Google Drive and Firebase.
- **Background Execution**: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` to reliably upload files in the background.
- **Accounts**: `GET_ACCOUNTS` for Google Drive authentication.

## ⚙️ Setup and Installation

1. **Clone the repository.**
2. **Open the project in Android Studio.**
3. **Configure Firebase & Google Services**: Provide your own `google-services.json` file in the `app/` directory. Ensure you have enabled Firebase Auth and Google Drive API in your Google Cloud Console.
4. **Build and Run**: Compile the project and install the APK on a device running Android 7.0 or higher.
5. **Grant Permissions**: Upon first launch, grant the required physical storage and Google account permissions to start backing up your files.
