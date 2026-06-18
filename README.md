# voiceTransformForAndroid

上下文语音纠错输入法 Agent MVP。

当前按 6 步清单已补到：

- Step 1: FastAPI 文本纠错接口和网页演示。
- Step 2: SQLite 用户画像、专业词库、拼音候选纠错工具。
- Step 3: OpenAI-compatible LLM 改写工具、ContextCorrectionAgent 编排、Agent trace。

Android 目录目前仍是普通客户端雏形，不是 Step 4 的系统输入法 `InputMethodService`。

## 技术栈

- Backend: Python, FastAPI, Pydantic, SQLite, Uvicorn
- Agent: MemoryTool, PinyinCorrectorTool, LLMRewriteTool, Agent trace
- LLM: OpenAI-compatible Chat Completions API
- Web demo: HTML, CSS, JavaScript
- Android prototype: Java, Android SDK, HttpURLConnection, org.json

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
$env:VOICE_TRANSFORM_DB="backend\data\dev_voice_transform.db"
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

## User Profile API

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/v1/profile/local_user
```

Update profile:

```powershell
Invoke-RestMethod `
  -Method Put `
  -Uri http://127.0.0.1:8000/api/v1/profile/local_user `
  -ContentType "application/json" `
  -Body '{"profile_text":"计算机专业学生，正在学习计组、计网、操作系统、Agent 开发。"}'
```

## Terms API

List terms:

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/terms?user_id=local_user"
```

Add a term:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8000/api/v1/terms `
  -ContentType "application/json" `
  -Body '{"user_id":"local_user","term":"线程","category":"system","aliases":["现金"],"weight":1.0}'
```

After adding this term, `老师讲了现金调度` can be corrected to `老师讲了线程调度`.

Delete a term:

```powershell
Invoke-RestMethod -Method Delete http://127.0.0.1:8000/api/v1/terms/1
```

## LLM Configuration

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

## Agent Trace

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/debug/traces?limit=10"
```

Each trace records:

- raw text
- user profile summary
- matched terms
- pinyin candidates
- whether LLM succeeded
- tool calls: `MemoryTool -> PinyinCorrectorTool -> LLMRewriteTool`

## Web Demo

Open:

```text
http://127.0.0.1:8000/
```

The page is now an Agent console with four tabs:

- Correct: test `POST /api/v1/correct-text`
- Profile: load and save user profile
- Terms: add, refresh, and delete professional terms
- Agent Trace: inspect recent tool calls and fallback status

## Android Prototype

Open the `android/` folder in Android Studio after the backend is running.

The emulator uses:

```text
http://10.0.2.2:8000
```

Current Android work includes the Step 4 system IME MVP. Android can register `Voice Transform IME`, switch to it from normal input fields, recognize speech with `SpeechRecognizer`, call the backend correction API, and insert `corrected_text` with `InputConnection.commitText()`.
