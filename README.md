# voiceTransformForAndroid

Context-aware speech text correction MVP.

- Step 1: FastAPI backend with a bilingual web demo.
- Step 2: Native Android client with typed input, system speech recognition, and backend correction.
- Step 3: Local end-to-end verification for backend, web demo, and Android emulator.

## Run Backend

```bash
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

On Windows PowerShell, you can also run from the repository root:

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

If your local PowerShell execution policy blocks `.ps1` files, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\test_correct_text.ps1
```

## Android Client

Open the `android/` folder in Android Studio after the backend is running.

Main capabilities:

- Chinese / English UI toggle
- User ID, app context, backend URL, and raw text input
- Android system speech recognition entry point
- Backend request to `POST /api/v1/correct-text`
- Result display for corrected text, matched terms, and reason

The emulator uses `http://10.0.2.2:8000` to access the backend running on the development machine. For a physical Android device, enter the computer LAN address in the app, such as `http://192.168.1.4:8000`.

See `android/README.md` for Android-specific usage and the technology stack.

## Step 3 Verification Checklist

1. Start the backend and confirm `http://127.0.0.1:8000/health` returns `{"status":"ok"}`.
2. Run `.\backend\test_correct_text.ps1` and confirm `祭祖课` is corrected to `计组课`.
3. Open the web demo, switch Chinese / English, and click Correct.
4. Run the Android app on an emulator, keep backend URL as `http://10.0.2.2:8000`, and tap Correct.
