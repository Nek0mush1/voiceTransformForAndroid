# Android IME

This Android project contains two input method clients:

- `:app`: the original Voice Transform IME and settings/debug app.
- `:trime`: a Voice Transform build of the full Trime/Rime Android input method UI, with the existing voice correction flow wired into Trime's voice key.

The backend target defaults to `http://39.106.51.35:8000`, matching the production server described in `AGENTS.md`.

## Trime Build

Use JDK 17. If the shell defaults to another Java version, point Gradle at Android Studio's JBR:

```powershell
cd D:\codeLibrary\voiceTransformForAndroid\android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :trime:assembleDebug
```

The easiest APK to install on a phone is the universal debug build:

```text
D:\codeLibrary\voiceTransformForAndroid\android\trime\build\outputs\apk\debug\com.example.voicetransform.trime-95d8a4b-universal-debug.apk
```

ABI-specific APKs are also generated in the same directory. Most modern phones use `arm64-v8a`.

## Trime Install

With a connected phone and USB debugging enabled:

```powershell
C:\Users\arinoay4\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r .\trime\build\outputs\apk\debug\com.example.voicetransform.trime-95d8a4b-universal-debug.apk
```

Then enable and select the input method on the phone:

```text
Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> Voice Transform Trime
```

Tap any text field and switch to `Voice Transform Trime`. If you still see the old rounded custom keyboard with `Rime did not handle...`, you are using `Voice Transform IME`, not the Trime input method.

## Trime Behavior

- Chinese pinyin input is handled by the Trime/Rime engine, not by the old Java candidate picker.
- The default schema is `voice_transform_pinyin`, a simplified Chinese pinyin schema with the Voice Transform dictionary bundled under `assets/shared`.
- `jizu` should offer `计组`.
- `jiwang` should offer `计网`.
- `shujujiegou` should offer `数据结构`.
- `caozuoxitong` should offer `操作系统`.
- Long press the space key to start voice recording; release to stop recording and upload to `POST /api/v1/correct-audio`.
- The correction dialog lets the user insert corrected text, insert raw text, or cancel.

## Original App Build

The original IME can still be built separately:

```powershell
cd D:\codeLibrary\voiceTransformForAndroid\android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

Its debug APK is written to:

```text
D:\codeLibrary\voiceTransformForAndroid\android\app\build\outputs\apk\debug\app-debug.apk
```

Use this module only when testing the old custom keyboard. For Trime UI testing, install `:trime`.
