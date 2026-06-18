# Voice Transform for Android

一个面向 Android 输入场景的上下文感知语音纠错输入法 MVP。

这个项目的目标不是做一个普通聊天机器人，而是解决手机语音输入里常见的专业词、课程名、技术词被识别成同音错字的问题。例如用户说“今天上午上了两节计组课”，普通语音输入可能识别成“祭祖课”。本项目希望在语音输入后，根据用户画像、个人词库、拼音/同音候选和可选 LLM，对识别文本进行二次纠错，再由用户确认后插入到当前 App 的输入框中。

## 项目用途

Voice Transform 可以作为一个“上下文感知纠错 Agent”：

- 在 Android 任意输入框中通过自定义输入法进行语音输入。
- 获取原始 ASR 文本后，发送到后端进行纠错。
- 后端结合用户画像、专业术语库、拼音/同音候选和 LLM 进行判断。
- 返回纠错文本、命中术语、纠错原因、调用链路和 LLM 调用状态。
- 用户在输入法面板中确认后，再插入纠错文本或保留原文。

适合的使用场景包括：

- 课程、专业术语较多的学习笔记和聊天输入。
- 计算机、AI、工程等领域的语音转文字纠错。
- 用作 Agent 项目的 MVP 展示：观察输入、检索记忆、调用工具、给出行动结果并保留 trace。

## 核心思路

普通 ASR 只根据声音做识别，缺少用户长期上下文。本项目把“语音识别结果”当作待纠错文本，再引入上下文信息进行二次判断。

后端纠错流程大致如下：

```text
原始语音识别文本
        |
        v
读取用户画像 + 个人术语库
        |
        v
拼音/同音/别名规则候选纠错
        |
        v
可选 LLM 约束式改写
        |
        v
返回纠错文本 + 纠错方法 + trace/debug 信息
```

LLM 不是强依赖。即使 LLM 未配置、调用失败或返回空结果，后端也会回退到规则/拼音纠错结果，保证输入法仍然可用。

## 当前功能

- Android 自定义输入法 `InputMethodService`
- Android App 配置页和测试页
- 系统语音识别模式：`SpeechRecognizer -> /api/v1/correct-text`
- 可选后端 ASR 上传模式：`/api/v1/correct-audio`
- FastAPI 后端纠错接口
- SQLite 用户画像和个人术语库
- 拼音、同音、别名 fallback 纠错
- 可配置 OpenAI-compatible LLM relay
- 支持 `chat_completions` 和 `responses` 两种 LLM wire API
- LLM 调用日志页面，最近 50 条
- 输入法和 App 内展示本次纠错方法：LLM、规则/拼音 fallback 或保留原文
- Agent trace/debug 接口，便于演示和排查

## 技术栈

- Android: Java, Android SDK, `InputMethodService`, `SpeechRecognizer`, `HttpURLConnection`
- Backend: Python, FastAPI, Pydantic, SQLite, Uvicorn
- Agent tools: MemoryTool, PinyinCorrectorTool, LLMRewriteTool
- Optional ASR: Baidu ASR
- Optional LLM: OpenAI-compatible relay

## 目录结构

```text
.
├── android/                 # Android App 和输入法客户端
├── backend/                 # FastAPI 后端
│   ├── app/api/v1/          # API 路由
│   ├── app/services/        # 纠错、LLM、ASR、Agent 编排
│   ├── app/schemas/         # Pydantic 模型
│   └── tests/               # 后端测试
├── docs/                    # 部署和演示文档
└── backend_update_cloud.zip # 云端后端更新包
```

## 快速运行后端

```bash
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

健康检查：

```bash
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/api/v1/debug/status
```

测试文本纠错：

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/correct-text" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"local_user\",\"raw_text\":\"今天上午上了两节祭祖课\",\"app_context\":\"study\"}"
```

## Android 使用方法

1. 启动后端，或使用已经部署好的云端后端。
2. 用 Android Studio 打开 `android/`。
3. 连接手机，安装 debug APK。
4. 打开 App，设置后端地址、用户 ID、场景、语音识别模式。
5. 在 App 中维护用户画像和专业词库。
6. 可选：配置 LLM relay 的 `base_url`、`api_key`、`model` 和 `wire_api`。
7. 到系统输入法设置中启用 `Voice Transform IME`。
8. 在微信、备忘录、浏览器等任意输入框中切换到该输入法。
9. 点击语音按钮，说话后等待纠错结果。
10. 查看原文、纠错文本和本次纠错方法，确认后插入。

常用后端地址：

- Android 模拟器访问本机后端：`http://10.0.2.2:8000`
- 真机访问电脑后端：使用电脑局域网 IP，例如 `http://192.168.1.4:8000`
- 默认云端后端：`http://39.106.51.35:8000`

## LLM 配置说明

LLM 是可选能力。后端会保存 LLM 配置，Android App 可以管理配置并测试连接。

支持两种接口格式：

```text
chat_completions -> POST {base_url}/chat/completions
responses        -> POST {base_url}/responses
```

如果使用 micuapi relay，可参考：

```text
base_url: https://www.micuapi.ai/v1
model: gpt-5.5
wire_api: responses
```

为了排查“到底有没有调用 LLM”，App 内提供了 LLM 调用日志页。后端仅保留最近 50 条纠错阶段日志，包括：

- 原始文本
- 规则 fallback 文本
- LLM/最终输出文本
- 是否调用成功
- 失败错误信息
- 使用模型和接口格式
- trace_id

API：

```bash
curl "http://127.0.0.1:8000/api/v1/debug/llm-calls?limit=50"
```

## 调试接口

```bash
curl http://127.0.0.1:8000/api/v1/debug/status
curl "http://127.0.0.1:8000/api/v1/debug/traces?limit=10"
curl "http://127.0.0.1:8000/api/v1/debug/llm-calls?limit=50"
```

`debug/traces` 会记录 Agent 工具链路：

```text
MemoryTool -> PinyinCorrectorTool -> LLMRewriteTool
```

## 构建 Android APK

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```

生成文件一般在：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到手机：

```powershell
adb install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

## 后端测试

```powershell
cd backend
python -m unittest discover -s tests
```

## 云端部署提示

如果手机使用默认云端后端，则修改后端代码后需要重新部署到服务器，手机才能看到新行为。

本项目常用更新包：

```text
backend_update_cloud.zip
```

上传和部署大致流程：

```powershell
scp .\backend_update_cloud.zip root@39.106.51.35:/tmp/backend_update_cloud.zip
ssh root@39.106.51.35
```

服务器上执行：

```bash
cd /opt/voice-transform-backend
cp -a app "app.bak.$(date +%Y%m%d%H%M%S)"
unzip -o /tmp/backend_update_cloud.zip -d /opt/voice-transform-backend
source .venv/bin/activate
pip install -r requirements.txt
systemctl restart voice-transform
```

验证：

```bash
curl http://39.106.51.35:8000/health
curl http://39.106.51.35:8000/api/v1/debug/status
```

## 安全说明

- 不要提交 API key、服务器密码、本地数据库或私有配置。
- LLM API key 不会在 API 响应中完整返回，只显示 masked key。
- LLM 调用日志不记录 API key。
- LLM 失败时后端会 fallback，不会因为模型不可用导致输入法完全不可用。

## 项目状态

这是一个可用但仍偏 MVP 的项目。当前重点是证明“上下文感知语音输入纠错”这条链路可行：手机输入法可用、后端纠错可用、个人词库可维护、LLM 可选接入、纠错过程可解释可调试。

后续可以继续改进：

- 更完善的中文拼音/同音纠错算法
- 更好的输入法 UI 和候选词交互
- 多用户认证和云端配置隔离
- 更细粒度的 App 场景上下文
- 更完整的端到端测试和发布流程
