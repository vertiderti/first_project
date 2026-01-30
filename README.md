# ASR (Android Speech Recognition) Project

This is an Android application for speech recognition with overlay functionality.

## Project Structure

- `app/src/main/java/com/example/asr/` - Main source code
- `app/src/main/res/` - Resources (layouts, drawables, values)
- `AndroidManifest.xml` - Application manifest

## Features

1. Speech recognition service
2. Overlay display for recognition results
3. Share entry point for audio files
4. Transparent activity for UI overlay

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API level 21 or higher
- Kotlin 1.8.0

### Building the Project

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project

### Required Dependencies

The project uses:
- AndroidX Core KTX
- AndroidX AppCompat
- Material Design Components
- ConstraintLayout

## Files

### Main Components

- `MainActivity.kt` - Main activity for demonstration
- `OverlayService.kt` - Service for overlay functionality
- `RecognitionService.kt` - Speech recognition service
- `ShareReceiverActivity.kt` - Activity for handling shared audio files
- `TransparentActivity.kt` - Transparent activity for UI overlay

### Layout Files

- `activity_share_receiver.xml` - Layout for share receiver
- `activity_transparent.xml` - Layout for transparent activity
- `overlay_popup.xml` - Layout for overlay popup

## Permissions

The app requires the following permissions:
- RECORD_AUDIO
- SYSTEM_ALERT_WINDOW
- READ_EXTERNAL_STORAGE
- FOREGROUND_SERVICE

## Note

This project is a template and may require additional implementation in feature directories.