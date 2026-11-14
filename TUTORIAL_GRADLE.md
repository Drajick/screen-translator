# Screen Translator (Kotlin + Gradle)

This repository uses a standard Android Gradle project layout. Screen capture uses Android MediaProjection, the floating panel uses the overlay permission, and text is recognized and translated on-device with ML Kit.

## Build an APK

1. Open Actions → Build APK → Run workflow.
2. Wait for the build to finish, then download the screen-translator-apk artifact.
3. Extract the ZIP and install app-debug.apk on an Android device.

The first use of a translation language may require an internet connection to download its translation model. The OCR models are bundled in the APK.

## Project layout

- app/src/main/java/com/noveltranslator/MainActivity.kt — language selection and permission flow.
- app/src/main/java/com/noveltranslator/ScreenCaptureService.kt — screen capture, OCR, translation, and floating panel.
- app/src/main/AndroidManifest.xml — permissions and foreground service declaration.
- app/build.gradle.kts — Android module and ML Kit dependencies.
