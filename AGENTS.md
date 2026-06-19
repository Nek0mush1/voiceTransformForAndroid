# Project Guidance

## Must Read First

Before answering or changing this repository, read this file and keep its context active.

This project is meant to run as a client/server product:

- The Android app and input method are clients.
- The FastAPI backend is the main runtime for correction, memory, user profile, term storage, ASR integration, and LLM gateway calls.
- The default production backend target is a remote server, currently `http://39.106.51.35:8000`.
- When a change affects behavior used by the phone, assume the backend must be deployed or packaged for the server before the app can observe it.
- Do not assume local-only changes are enough unless the user explicitly says they are testing locally.

## Product Goal

The core problem is that phone speech-to-text input is often inaccurate because it lacks user context. Professional terms and domain words are commonly recognized as homophones or similar-sounding unrelated words. Example: a computer science student says "今天上午上了两节计组课", but normal speech input may produce words like "祭祖" or other wrong homophones instead of "计组".

The intended product is a context-aware voice input method or input-method plugin that improves speech-to-text results by using:

- the user's identity and long-term profile, such as "computer science undergraduate student";
- domain terms the user frequently types or says, such as 计组, 计网, 操作系统, 数据结构, Agent, RAG, Cache;
- app context, such as chat, notes, or study;
- pinyin or homophone candidates;
- an optional LLM relay/API selected by the user.

The most important user need is not a standalone chatbot. The key requirement is: whenever the user invokes voice input on the phone, the custom system should be available in that input flow and should return corrected text that can be inserted into the current app.

## Product Direction

This can be a valid Agent project if the system is framed as a context-aware correction agent rather than a generic chat app. The agent should:

1. Observe raw ASR output, current app context, user profile, term memory, and correction candidates.
2. Think through likely homophone/domain-term corrections under strict constraints.
3. Act by producing corrected text plus a short reason/trace.
4. Preserve user control by letting the user confirm before insertion.

The first practical MVP should prioritize:

- Android input method integration that can be used from arbitrary text fields.
- Backend correction API that accepts raw recognized text and app context.
- User profile and professional term storage.
- Rule/pinyin-based fallback that works without LLM.
- Optional LLM rewrite through user-configured relay APIs.
- Trace/debug output so the user can learn from the agent behavior and discuss it in interviews.

Avoid turning the project into only a chat interface. A chat UI can be used for debugging, but the product value is phone voice input correction at input time.

## Architecture Assumptions

- Android package lives under `android/`.
- Backend lives under `backend/`.
- The backend exposes routes under `/api/v1`.
- LLM configuration is stored on the backend and can be managed from the Android app.
- The backend should tolerate LLM failures and fall back to local/rule-based correction.
- API keys must never be logged or returned in full. Show only masked keys in UI/API responses.
- If LLM behavior changes, update both backend tests and Android configuration flow when relevant.

## LLM Relay Notes

The app supports configurable LLM relays. Some relays use OpenAI Chat Completions wire format and others use Responses wire format.

- `chat_completions` means `POST {base_url}/chat/completions`.
- `responses` means `POST {base_url}/responses`.
- For the user's current micuapi relay, use:
  - `base_url`: `https://www.micuapi.ai/v1`
  - `model`: `gpt-5.5`
  - `wire_api`: `responses`

The backend should send a normal browser-like User-Agent for LLM relay calls because some relay domains are protected by Cloudflare and may block Python's default request signature.

## Server Deployment Reminder

When backend code changes and the phone is using the default cloud backend, include deployment steps or regenerate the backend update package. The normal update flow is:

```powershell
cd D:\codeLibrary\voiceTransformForAndroid
scp .\backend_update_cloud.zip root@39.106.51.35:/tmp/backend_update_cloud.zip
ssh root@39.106.51.35
```

Then on the server:

```bash
cd /opt/voice-transform-backend
cp -a app "app.bak.$(date +%Y%m%d%H%M%S)"
unzip -o /tmp/backend_update_cloud.zip -d /opt/voice-transform-backend
source .venv/bin/activate
pip install -r requirements.txt
systemctl restart voice-transform
```

If systemd is unavailable:

```bash
pkill -f "uvicorn app.main:app" || true
nohup bash start_server_public.sh >server.log 2>&1 &
```

Verify:

```bash
curl http://39.106.51.35:8000/health
curl http://39.106.51.35:8000/api/v1/debug/status
```

Remote upload/deploy commands need explicit user approval when the assistant is asked to execute them directly.

## Engineering Preferences

- Prefer small, understandable changes that fit the existing codebase.
- Keep Android client and backend API models in sync.
- Add focused backend tests for correction, LLM request formats, error handling, and fallback behavior.
- Run backend tests after backend changes.
- Run Android debug build after Android changes when practical.
- Preserve existing user data and saved LLM config through migrations.
- Never commit secrets, API keys, local database files, or server credentials.

## Frontend Design Reference

The following frontend guidance is a style reference for future UI work. It is not a strict rulebook, but should inform Android and web interface changes.

你是一名资深前端产品设计师和前端工程师，目标是设计并实现具有 Apple 与 Google 产品气质的现代 Web 界面。

设计原则：
1. 界面要简约、干净、克制，不要有明显 AI 生成感。
2. 参考 Apple 的留白、层次、细腻质感、清晰排版，以及 Google Material Design 的结构化布局、可用性和一致性。
3. 优先保证可读性、可用性、信息层级和交互效率，不做无意义装饰。
4. 页面要像真实产品，而不是模板、落地页或 AI 生成的演示稿。

视觉要求：
1. 使用低饱和、中性色为主，少量强调色。
2. 背景干净，可以是白色、浅灰、极淡的中性色，不使用夸张渐变。
3. 避免大面积紫色、蓝紫渐变、玻璃拟态、发光边框、漂浮圆球、赛博风背景。
4. 圆角要克制，卡片圆角建议 8px-16px，不要过度圆润。
5. 阴影要非常轻，只用于建立层级，不要厚重阴影。
6. 图标使用统一风格，线性图标优先。

排版要求：
1. 字体层级清晰，标题、正文、辅助信息大小区分明确。
2. 不要使用过大的标题，除非是首页 Hero 区。
3. 行高、字距、段落间距要舒适。
4. 文案简洁，不写空洞营销语。
5. 中文界面要避免英文模板直译感。

布局要求：
1. 使用明确的网格、对齐和间距系统。
2. 页面结构要符合真实产品使用习惯。
3. 不要堆砌卡片，不要为了好看放很多无意义模块。
4. 移动端和桌面端都要适配，内容不能溢出或重叠。
5. 优先让用户快速完成任务，而不是只追求视觉效果。

组件要求：
1. 按真实产品标准设计按钮、输入框、导航、表格、卡片、弹窗、空状态、加载状态和错误状态。
2. 按钮要有明确主次关系。
3. 表单要有清晰标签、提示、校验反馈。
4. 列表和卡片要便于扫描，不要信息密度失控。
5. 交互状态要完整，包括 hover、active、focus、disabled。

禁止事项：
1. 不要做 AI 味很重的紫蓝渐变背景。
2. 不要使用漂浮光球、玻璃卡片、过度发光、复杂噪点背景。
3. 不要做像模板站一样的大 Hero + 三个功能卡片，除非确实是官网落地页。
4. 不要堆砌“智能、赋能、下一代、极致体验”等空泛文案。
5. 不要使用过多 emoji。
6. 不要只给静态页面，要考虑真实使用流程。

