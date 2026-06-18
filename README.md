# voiceTransformForAndroid

Step 1 MVP: FastAPI backend with a bilingual web demo for context-aware text correction.

## Run Backend

```bash
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

On Windows PowerShell, you can also run:

```powershell
.\backend\start_server.ps1
```

Keep this terminal open while testing.

## Web Demo

Open:

```text
http://127.0.0.1:8000/
```

The demo page includes:

- Chinese / English language toggle
- Raw text input
- User ID and app context fields
- Correction result, matched terms, and reason
- Link to FastAPI API docs

## Test Correction API

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/correct-text" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"local_user\",\"raw_text\":\"今天上午上了两节祭祖课\",\"app_context\":\"chat\"}"
```

Expected response:

```json
{
  "raw_text": "今天上午上了两节祭祖课",
  "corrected_text": "今天上午上了两节计组课",
  "matched_terms": ["计组"],
  "reason": "根据用户专业词库和同音匹配修正。"
}
```

Or run the PowerShell test script after the backend is started:

```powershell
.\backend\test_correct_text.ps1
```

## Frontend Requirement

Any custom web frontend must include a Chinese / English language toggle. The toggle should visibly switch active state and update page labels, buttons, hints, and result text immediately.
