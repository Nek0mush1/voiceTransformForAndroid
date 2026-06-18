# Android IME MVP

This module is a real Android input method backed by the FastAPI correction service.

## What It Does

- Registers `Voice Transform IME` through `InputMethodService`.
- Shows voice, delete, space, and enter buttons. Use the phone system keyboard switch key to leave this IME.
- Uses Android `SpeechRecognizer` by default to get raw Chinese speech text.
- Sends recognized text to `POST /api/v1/correct-text`.
- Shows raw text and corrected text before insertion.
- Lets the user insert corrected text, insert raw text, or cancel.
- Provides an app settings screen for backend URL, user ID, app context, speech mode, user profile, and professional terms.
- Keeps optional backend ASR mode for `POST /api/v1/correct-audio` when ASR credentials are configured.

## Run

1. Start the backend from the repository root:

   ```powershell
   .\backend\start_server.ps1
   ```

2. Open `android/` in Android Studio.
3. Run the `app` configuration on an emulator or phone.
4. Open the Voice Transform app once and grant microphone permission.
5. Configure the backend URL:

   ```text
   Emulator: http://10.0.2.2:8000
   Phone:    http://<computer-lan-ip>:8000
   ```

6. Keep speech mode as `system` unless backend ASR keys are configured.
7. Enable the input method:

   ```text
   Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> Voice Transform IME
   ```

8. Tap any text field, switch to `Voice Transform IME`, then use the voice button.

## App Home Sections

The launcher Activity is split into sections so the home screen is not one long form:

- Test: voice/text correction test console.
- Settings: backend URL, user ID, app context, and speech mode.
- Profile: load/save the user profile.
- Terms: add, refresh, and delete professional terms.
- LLM: configure and test an OpenAI-compatible LLM gateway.

Backend URL, user ID, app context, and speech mode are saved locally.

## Settings Details

- Speech mode:
  - `system`: Android SpeechRecognizer, then backend text correction. This is the default.
  - `backend`: record audio and upload to backend ASR. This requires Baidu ASR environment variables on the backend.
- Profile buttons load/save `GET/PUT /api/v1/profile/{user_id}`.
- Term buttons add, refresh, and delete `GET/POST/DELETE /api/v1/terms`.
- LLM buttons load/save/test `GET/PUT/POST /api/v1/llm-config`.

Adding a term on the phone affects the next IME correction because the input method reads the same saved backend URL and user ID.

For an API relay, set the LLM URL to the relay's OpenAI-compatible `/v1` base URL, for example:

```text
https://api.example.com/v1
```

The backend will call:

```text
POST {LLM URL}/chat/completions
```

Leave the API key field empty when saving if you only want to change the URL or model and keep the saved key.

## Manual Test

- Space inserts one blank character in the current field.
- Enter sends an enter key event.
- Delete removes one character before the cursor.
- Use the phone system keyboard switch key or navigation bar keyboard selector to leave this IME.
- Voice in `system` mode:
  - Speak `今天上午上了两节祭祖课`.
  - Confirm that the keyboard shows raw and corrected text.
  - Tap insert corrected.
  - Expected insertion: `今天上午上了两节计组课`.
- Add a term in the app:
  - term: `线程`
  - aliases: `现金`
  - category: `system`
  - weight: `1.0`
  - Then `老师讲了现金调度` should correct to `老师讲了线程调度`.

## Build

If the shell defaults to Java 8, point Gradle at Android Studio's JBR:

```powershell
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```
