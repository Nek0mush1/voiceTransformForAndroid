# Android IME MVP

This Android module is now the Step 4 MVP: a real system input method backed by the FastAPI correction service.

## What It Does

- Registers `Voice Transform IME` as an Android input method through `InputMethodService`.
- Shows a compact keyboard view with voice, delete, space, enter, and input-method switch buttons.
- Uses Android `SpeechRecognizer` to get raw Chinese speech text.
- Sends recognized text to `POST /api/v1/correct-text`.
- Inserts the backend `corrected_text` into the current focused input field with `InputConnection.commitText()`.
- Keeps the original Activity as a configuration and permission screen for backend URL, user ID, and app context.

## Run

1. Start the backend from the repository root:

   ```powershell
   .\backend\start_server.ps1
   ```

2. Open `android/` in Android Studio.
3. Run the `app` configuration on an emulator or phone.
4. Open the Voice Transform app once, grant microphone permission, and set the backend URL.
5. Enable the input method in Android system settings:

   ```text
   Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> Voice Transform IME
   ```

6. Tap any text field in Notes, WeChat, or a browser, switch to `Voice Transform IME`, then use the voice button.

## Backend URL

- Android emulator: `http://10.0.2.2:8000`
- Physical phone: use the computer LAN address, for example `http://192.168.1.4:8000`

The phone and computer must be on the same network, and the backend must keep running.

## Manual Test

- Space inserts one blank character in the current field.
- Enter sends an enter key event.
- Delete removes one character before the cursor.
- Switch opens the system input-method picker.
- Voice recognition of the demo phrase "jin tian shang wu shang le liang jie ji zu ke" should eventually insert the corrected backend result, expected "ji zu ke -> ji zu ke with the project term correction for computer organization" when the backend terms are initialized. In Chinese, this is the known demo case from the root docs.

## Notes

The first Step 4 version deliberately uses Android `SpeechRecognizer`; it does not upload audio to the backend. Backend ASR and confirmation-before-insert belong to the next step.
