# Trime IME v1.5

Trime IME 是 Voice Transform 的完整输入法版本，对应仓库 `combine-with-Trime` 分支。它在 Trime/Rime 开源输入法基础上，接入 Voice Transform 的语音输入纠错 Agent，让项目从“语音纠错输入法 MVP”进一步接近真实可用的 Android 输入法。

## 适合谁使用

这个版本适合：

- 想体验完整键盘和拼音输入的人。
- 想展示“开源输入法 + Agent 语音纠错”结合方案的人。
- 想在简历、答辩或面试中讲清楚工程整合能力的人。
- 想继续基于 Trime/Rime 做输入法功能扩展的人。

## 下载

进入 GitHub Release：

```text
v1.5.0-trime-ime
```

推荐下载 universal APK：

```text
voice-transform-trime-v1.5.0-universal.apk
```

如果需要更小体积，可以按手机 ABI 下载对应 APK。大多数现代 Android 手机使用：

```text
arm64-v8a
```

## 功能

- 注册为 Android 系统输入法：`Voice Transform Trime`。
- 中文拼音输入由 Trime/Rime 引擎处理。
- 默认 schema 为 `voice_transform_pinyin`。
- 内置 Voice Transform 专业词典，例如：
  - `jizu` -> `计组`
  - `jiwang` -> `计网`
  - `shujujiegou` -> `数据结构`
  - `caozuoxitong` -> `操作系统`
- 长按空格键开始语音输入，松手停止并进入纠错流程。
- 语音纠错结果弹窗支持：
  - 插入修正
  - 插入原文
  - 取消
- 后端继续使用用户画像、专业词库、拼音/同音候选和可选 LLM。
- 保留 Agent trace 和 LLM 调用日志，方便调试与演示。

## 使用教程

### 1. 启动后端

```powershell
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

验证：

```powershell
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/api/v1/debug/status
```

### 2. 安装 APK

使用 Release 下载的 APK：

```powershell
adb install -r voice-transform-trime-v1.5.0-universal.apk
```

或本地构建：

```powershell
git checkout combine-with-Trime
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :trime:assembleDebug
```

构建产物一般在：

```text
android/trime/build/outputs/apk/debug/
```

安装 universal debug APK：

```powershell
adb install -r .\trime\build\outputs\apk\debug\com.example.voicetransform.trime-95d8a4b-universal-debug.apk
```

### 3. 启用输入法

在 Android 设置中启用：

```text
Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> Voice Transform Trime
```

然后在任意文本输入框中切换到：

```text
Voice Transform Trime
```

如果看到旧版圆角自定义键盘，说明当前切换到的是 `Voice Transform IME`，不是 Trime 版本。

### 4. 测试拼音输入

在输入框中输入：

```text
jizu
```

候选词应包含：

```text
计组
```

也可以测试：

```text
jiwang -> 计网
shujujiegou -> 数据结构
caozuoxitong -> 操作系统
```

### 5. 测试语音纠错

长按空格键开始录音，说：

```text
今天上午上了两节计组课
```

松手后，输入法会把音频或识别文本送到后端纠错。确认弹窗中会展示原文和纠错文本，用户确认后再插入当前输入框。

## Agent 设计

Trime 版本的 Agent 逻辑与原始版本一致，但产品入口更完整：

```text
Trime/Rime IME
  -> 拼音输入或语音输入
  -> voice key / space long press
  -> backend correction API
  -> MemoryTool 读取用户画像和专业术语
  -> PinyinCorrectorTool 生成候选纠错
  -> LLMRewriteTool 可选约束式改写
  -> trace/debug logs
  -> 用户确认上屏
```

这个版本更适合强调 Agent 开发价值：

- Agent 不是聊天窗口，而是嵌入真实输入法工作流。
- Agent 的观察对象来自真实输入行为。
- Memory 不只是 prompt 文案，而是可维护的用户画像和术语库。
- Tool calling 有明确边界：记忆检索、规则纠错、LLM 改写。
- Act 的结果不是回答问题，而是产出可上屏文本。
- Trace 能解释每一次纠错，适合调试、演示和面试说明。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 输入法框架 | Trime, Rime, Android IME |
| Android 集成 | Kotlin/Java, Gradle, Android SDK |
| Backend | Python, FastAPI, Pydantic, SQLite, Uvicorn |
| Agent | `ContextCorrectionAgent`, `MemoryTool`, `PinyinCorrectorTool`, `LLMRewriteTool` |
| 语音 | Android 系统语音能力，可选后端 ASR |
| LLM | OpenAI-compatible Chat Completions / Responses API |
| Debug | Agent trace, LLM call logs, `/api/v1/debug/*` |

## 与 Original IME 的区别

| 对比项 | Original IME | Trime IME |
| --- | --- | --- |
| 分支 | `main` | `combine-with-Trime` |
| 定位 | MVP 闭环验证 | 完整输入法体验 |
| 键盘 | 自定义基础键盘 | Trime/Rime 完整键盘 |
| 拼音输入 | 基础候选 | Rime 拼音方案 |
| 语音纠错 | 支持 | 支持 |
| 推荐程度 | 适合理解原理 | 推荐安装体验 |

## 开源说明

本版本基于 Trime/Rime 生态做工程整合。发布和展示时应保留 Trime/Rime 的开源许可和致谢信息，相关依赖许可文件位于：

```text
android/trime/licenses/
```
