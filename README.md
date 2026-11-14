# Screen Translator

An Android screen translator built with Kotlin. Start a MediaProjection session, switch to another app, and view recognized and translated text in a movable floating panel. Screen OCR and ML Kit translation run on-device; the first use of a translation language may download its model.

## Use

1. Choose the source language (or automatic detection) and target language.
2. Tap Iniciar traducción de pantalla and allow display-over-other-apps access.
3. Approve Android's screen-capture prompt and choose an app or the whole screen.
4. Switch to the app you want to translate. Drag the floating panel by its header; tap DETENER to end capture.

Screen capture is user-initiated and indicated by Android while active. Captured frames are processed in memory and are not saved by this app.
