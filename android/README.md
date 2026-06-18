# Android MVP

This is Step 2 of the project: a native Android client that connects to the FastAPI correction backend.

## Tech Stack

- Java + Android SDK: native Activity, form controls, speech recognition Intent, and UI state handling.
- HttpURLConnection + org.json: lightweight HTTP POST client and JSON parsing without third-party Android dependencies.
- FastAPI backend: reuses `POST /api/v1/correct-text` from Step 1.
- Android Studio / Gradle: project build, emulator install, and debugging.

## Features

- Chinese / English UI toggle with immediate label and button updates.
- Text input for `user_id`, `app_context`, and raw recognized text.
- System speech recognition entry point that fills the raw text field.
- Backend correction request to `/api/v1/correct-text`.
- Displays corrected text, matched terms, and correction reason.
- Backend URL is editable in the app. Use `http://10.0.2.2:8000` for an emulator, or the computer LAN address such as `http://192.168.1.4:8000` for a physical phone.

## Run

1. Start the backend from the repository root:

   ```powershell
   .\backend\start_server.ps1
   ```

2. Open the `android/` folder in Android Studio.
3. Let Android Studio sync Gradle.
4. Run the `app` configuration on an emulator.
5. Keep the backend terminal open while testing.

For a physical Android device, enter the computer LAN address in the app, for example `http://192.168.1.4:8000`, and make sure the phone and computer are on the same network.
