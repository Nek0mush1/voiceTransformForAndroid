# Voice Transform for Android

Voice Transform 是一个面向 Android 输入场景的上下文感知语音纠错输入法项目。它的目标不是做一个普通聊天机器人，而是在用户真正使用手机输入文字时，修正语音识别中常见的专业词、课程名、技术词同音误识别问题。

例如用户说：

```text
今天上午上了两节计组课
```

普通语音输入可能得到：

```text
今天上午上了两节祭祖课
```

Voice Transform 会把 ASR 原始文本交给后端 Agent，结合用户画像、个人术语库、拼音/同音候选和可选 LLM 做二次纠错，再由用户确认后插入当前 App 的输入框。

## 版本选择

本仓库目前保留两个可用版本。推荐普通体验和展示使用完整输入法版本。

| 版本 | 分支 | 适合场景 | 下载入口 | 文档 |
| --- | --- | --- | --- | --- |
| Original IME v1.0 | `main` | 仅体验语音识别 + 后端纠错闭环，键盘能力较基础 | [v1.0.0-original-ime](https://github.com/Nek0mush1/voiceTransformForAndroid/releases/tag/v1.0.0-original-ime) | [原始输入法版本说明](docs/version-original-ime.md) |
| Trime IME v1.5 | `combine-with-Trime` | 完整输入法体验，集成 Trime/Rime 拼音输入和语音纠错 | [v1.5.0-trime-ime](https://github.com/Nek0mush1/voiceTransformForAndroid/releases/tag/v1.5.0-trime-ime) | [Trime 完整输入法版本说明](docs/version-trime-ime.md) |

如果只是安装使用，请优先进入 [Releases](https://github.com/Nek0mush1/voiceTransformForAndroid/releases) 下载 APK，不需要自己切分支构建。

## 产品目的

手机语音输入通常只根据声音和通用语言模型识别文本，缺少用户的长期上下文。对于学生、程序员、工程师或特定行业用户，很多词会被识别成同音或近音错字，例如：

- `计组` -> `祭祖`
- `计网` -> `几晚`
- `线程` -> `现金`
- `Cache` -> `cash`
- `Agent` -> `智能体` 或其他近音误识别

本项目希望做的是一个“上下文感知纠错 Agent”：它不是替代 ASR，而是在 ASR 得到初始文本后，利用用户记忆和专业词库判断哪些词更可能是用户真正想输入的内容。

## 核心思路

后端把一次语音输入看作一个 Agent 任务：

```text
Observe
  接收原始 ASR 文本、当前 App 场景、用户 ID

Remember
  读取用户画像和个人专业术语库

Reason
  使用拼音、同音、别名、术语权重生成纠错候选

Act
  可选调用 LLM 做约束式改写，输出纠错文本

Trace
  保存命中词、工具调用链路、LLM 状态和纠错原因
```

实际工具链路：

```text
MemoryTool -> PinyinCorrectorTool -> LLMRewriteTool -> Agent Trace
```

LLM 不是强依赖。未配置 LLM、LLM 调用失败或网络异常时，后端会 fallback 到本地规则和拼音纠错结果，保证输入法仍然可用。

## 当前功能

- Android 系统输入法接入，可在任意输入框中使用。
- Android 设置页，支持配置后端地址、用户 ID、使用场景和语音模式。
- 后端文本纠错 API：`POST /api/v1/correct-text`。
- 可选音频上传纠错 API：`POST /api/v1/correct-audio`。
- SQLite 存储用户画像、专业词库、Agent trace 和 LLM 调用日志。
- 拼音、同音、别名和专业词库 fallback 纠错。
- 可选 OpenAI-compatible LLM relay。
- 支持 `chat_completions` 和 `responses` 两种 LLM wire API。
- 输入法侧展示原文、纠错文本和纠错方式，用户确认后再上屏。
- Debug 页面和接口可查看 Agent trace，适合项目演示和面试讲解。
- Trime 版本集成 Rime 拼音输入能力，保留完整键盘体验。

## 技术栈

| 层 | 技术 |
| --- | --- |
| Android 原始输入法 | Java, Android SDK, `InputMethodService`, `SpeechRecognizer`, `HttpURLConnection` |
| Android 完整输入法 | Trime, Rime, Kotlin/Java, Android IME |
| Backend | Python, FastAPI, Pydantic, SQLite, Uvicorn |
| Agent 编排 | `ContextCorrectionAgent`, `MemoryTool`, `PinyinCorrectorTool`, `LLMRewriteTool` |
| ASR | Android 系统 `SpeechRecognizer`，可选 Baidu ASR |
| LLM Relay | OpenAI-compatible Chat Completions / Responses API |
| 调试观测 | Agent trace, LLM call logs, backend debug APIs |

## 架构

```text
Android IME / Trime IME
        |
        | raw text or audio
        v
FastAPI backend
        |
        | MemoryTool
        v
User profile + term memory
        |
        | PinyinCorrectorTool
        v
Rule / pinyin correction candidates
        |
        | optional LLMRewriteTool
        v
Corrected text + reason + trace
        |
        v
User confirms insertion in current input field
```

## 快速开始

### 1. 启动后端

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

测试纠错：

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/correct-text" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"local_user\",\"raw_text\":\"今天上午上了两节祭祖课\",\"app_context\":\"study\"}"
```

### 2. 安装 Android APK

如果使用发布包，请从 [Releases](https://github.com/Nek0mush1/voiceTransformForAndroid/releases) 下载对应 APK。

如果本地构建：

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

# 原始输入法版本
.\gradlew.bat :app:assembleDebug

# Trime 完整输入法版本
.\gradlew.bat :trime:assembleDebug
```

### 3. 配置后端地址

常用地址：

- Android 模拟器访问本机后端：`http://10.0.2.2:8000`
- 真机访问电脑后端：使用电脑局域网 IP，例如 `http://192.168.1.4:8000`
- 默认云端后端：`http://39.106.51.35:8000`

### 4. 启用输入法

在 Android 系统设置中启用对应输入法：

```text
Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards
```

然后在任意输入框中切换到：

- `Voice Transform IME`
- 或 `Voice Transform Trime`

## LLM 配置

LLM 是可选能力。后端支持两种 OpenAI-compatible wire API：

```text
chat_completions -> POST {base_url}/chat/completions
responses        -> POST {base_url}/responses
```

配置项可以通过 Android App 或后端配置接口维护。API key 不会完整返回到客户端，也不会写入 LLM 调用日志。

如果使用 micuapi relay，可参考：

```text
base_url: https://www.micuapi.ai/v1
model: gpt-5.5
wire_api: responses
```

## 文档

- [原始输入法版本说明](docs/version-original-ime.md)
- [Trime 完整输入法版本说明](docs/version-trime-ime.md)
- [GitHub Release 发布指南](docs/release-guide.md)
- [项目架构](docs/architecture.md)
- [使用文档](docs/user_guide.md)
- [部署服务器](docs/deploy_server.md)
- [演示案例](docs/demo_cases.md)

## 后端测试

```powershell
cd backend
python -m unittest discover -s tests
```

## 发布方式

本项目建议使用 GitHub Releases 发布可安装 APK：

- `v1.0.0-original-ime`：从 `main` 分支打 tag，上传原始输入法 APK。
- `v1.5.0-trime-ime`：从 `combine-with-Trime` 分支打 tag，上传 Trime 完整输入法 APK。

具体步骤见 [GitHub Release 发布指南](docs/release-guide.md)。

## 安全说明

- 不要提交 API key、服务器密码、本地数据库或私有配置。
- LLM API key 在 API 响应中只显示 masked key。
- LLM 调用日志不记录 API key。
- LLM 失败时后端会 fallback，不会导致输入法完全不可用。

## 项目状态

这是一个可运行、可演示的 MVP。当前重点是证明“输入法入口 + 上下文记忆 + 专业词纠错 + 可选 LLM + 可解释 trace”这条链路可行。

后续可以继续改进：

- 更完整的端到端测试和发布流水线。
- 更强的中文拼音/近音纠错算法。
- 更细粒度的 App 场景上下文。
- 多用户认证与云端配置隔离。
- 更成熟的输入法 UI、候选词排序和用户词频学习。
