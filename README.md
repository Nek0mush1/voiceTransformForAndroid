# voiceTransformForAndroid

上下文语音纠错输入法 Agent MVP。目标是在 Android 任意输入框里切换到自定义输入法，说话得到原始识别文本，后端 Agent 结合用户画像、专业词库、拼音候选和可选 LLM 做纠错，用户确认后再插入当前输入框。

## 当前状态

- Step 1: FastAPI 文本纠错接口和网页演示。
- Step 2: SQLite 用户画像、专业词库、拼音候选纠错工具。
- Step 3: OpenAI-compatible LLM 改写工具、ContextCorrectionAgent 编排、Agent trace。
- Step 4: Android `InputMethodService` 输入法 MVP。
- Step 5: 确认插入、Android 端画像/词库设置、可选后端 ASR。
- Step 6: 后端核心测试、稳定性修复、演示文档。

默认语音路径现在是 Android 系统 `SpeechRecognizer -> /api/v1/correct-text -> 确认插入`。后端 ASR 仍保留为可选模式；没有配置 ASR key 时不要把它作为默认体验。

## 技术栈

- Backend: Python, FastAPI, Pydantic, SQLite, Uvicorn
- Agent: MemoryTool, PinyinCorrectorTool, LLMRewriteTool, Agent trace
- LLM: OpenAI-compatible Chat Completions API，可选
- ASR: Android SpeechRecognizer 默认；Baidu ASR 后端上传模式可选
- Android: Java, Android SDK, InputMethodService, SpeechRecognizer, HttpURLConnection

## Run Backend

```bash
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

Windows PowerShell can also run from the repository root:

```powershell
.\backend\start_server.ps1
```

The default SQLite database is created at:

```text
backend/data/voice_transform.db
```

You can override it:

```powershell
$env:VOICE_TRANSFORM_DB="..\.test-data\dev_voice_transform.db"
```

## Core Correction API

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/correct-text" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"local_user\",\"raw_text\":\"今天上午上了两节祭祖课\",\"app_context\":\"chat\"}"
```

Expected core fields:

```json
{
  "raw_text": "今天上午上了两节祭祖课",
  "corrected_text": "今天上午上了两节计组课",
  "matched_terms": ["计组"],
  "reason": "根据用户专业词库和拼音候选修正；LLM 未配置或调用失败，已使用 fallback。"
}
```

The response also includes `trace_id` and `agent_trace`.

## User Memory APIs

Profile:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/v1/profile/local_user

Invoke-RestMethod `
  -Method Put `
  -Uri http://127.0.0.1:8000/api/v1/profile/local_user `
  -ContentType "application/json" `
  -Body '{"profile_text":"计算机专业学生，正在学习计组、计网、操作系统、Agent 开发。"}'
```

Terms:

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/terms?user_id=local_user"

Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8000/api/v1/terms `
  -ContentType "application/json" `
  -Body '{"user_id":"local_user","term":"线程","category":"system","aliases":["现金"],"weight":1.0}'

Invoke-RestMethod -Method Delete http://127.0.0.1:8000/api/v1/terms/1
```

After adding `线程` with alias `现金`, `老师讲了现金调度` can be corrected to `老师讲了线程调度`.

## Optional LLM

LLM is optional. If it is not configured or fails, correction falls back to the pinyin/tool result.

```powershell
$env:LLM_BASE_URL="https://api.example.com/v1"
$env:LLM_API_KEY="your_api_key"
$env:LLM_MODEL="your_model"
```

The backend calls:

```text
POST {LLM_BASE_URL}/chat/completions
```

## Optional Backend ASR

The Android app defaults to system speech recognition. Backend ASR is available through:

```text
POST /api/v1/transcribe-correct
POST /api/v1/correct-audio
```

Baidu ASR environment variables:

```powershell
$env:BAIDU_ASR_API_KEY="..."
$env:BAIDU_ASR_SECRET_KEY="..."
$env:BAIDU_ASR_DEV_PID="1537"
```

## Agent Trace

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/debug/traces?limit=10"
```

Each trace records raw text, profile summary, matched terms, pinyin candidates, LLM status, and tool calls:

```text
MemoryTool -> PinyinCorrectorTool -> LLMRewriteTool
```

## Android Usage

Open `android/` in Android Studio after the backend is running.

Backend URL:

- Emulator: `http://10.0.2.2:8000`
- Physical phone: use the computer LAN address, for example `http://192.168.1.4:8000`

Enable and use the input method:

1. Run the app and grant microphone permission.
2. Set backend URL, user ID, app context, speech mode, profile, and terms.
3. Enable `Voice Transform IME` in Android keyboard settings.
4. Switch to the input method in Notes, WeChat, or a browser search field.
5. Tap voice, speak, review raw/corrected text, then insert corrected text or raw text.

## Tests

Stable backend test command:

```powershell
cd backend
python -m unittest discover -s tests -p 'test_correction_unittest.py' -v
```

Android debug build:

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

## Demo Cases

- `今天上午上了两节祭祖课` -> `今天上午上了两节计组课`
- `我在学真特开发` -> `我在学Agent开发`
- `老师讲了 cash 命中率` -> `老师讲了 Cache 命中率`
- `下节课是计网实验` should stay unchanged.
