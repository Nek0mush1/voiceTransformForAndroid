# Original IME v1.0

Original IME 是 Voice Transform 的第一版 Android 输入法客户端，对应仓库 `main` 分支。它重点验证“语音识别结果进入后端 Agent 纠错，再由用户确认插入”的核心闭环。

## 适合谁使用

这个版本适合：

- 想快速理解项目最小闭环的人。
- 想演示语音识别纠错、用户画像、专业词库和 Agent trace 的人。
- 想基于较简单 Android `InputMethodService` 代码继续改造的人。

如果你想日常使用更完整的拼音键盘，建议安装 [Trime 完整输入法版本](version-trime-ime.md)。

## 下载

进入 GitHub Release：

```text
v1.0.0-original-ime
```

下载 APK：

```text
voice-transform-original-ime-v1.0.0.apk
```

如果 Release 页面还没有上传 APK，可以从 `main` 分支本地构建。

## 功能

- 注册为 Android 系统输入法：`Voice Transform IME`。
- 可在任意文本输入框中使用。
- 默认调用 Android 系统 `SpeechRecognizer` 获取原始语音识别文本。
- 将 raw text 发送到后端 `/api/v1/correct-text`。
- 展示原文和纠错文本，用户可选择：
  - 插入修正
  - 插入原文
  - 取消
- 支持后端地址、用户 ID、App 场景和语音模式配置。
- 支持在 App 内维护用户画像和专业词库。
- 支持查看纠错结果和 Agent trace。

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
adb install -r voice-transform-original-ime-v1.0.0.apk
```

或本地构建：

```powershell
git checkout main
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

### 3. 配置后端

打开手机上的 `Voice Transform` App，填写后端地址：

```text
模拟器: http://10.0.2.2:8000
真机:   http://电脑局域网 IP:8000
云端:   http://39.106.51.35:8000
```

推荐配置：

```text
Speech Mode: system
User ID: local_user
App Context: study
```

### 4. 启用输入法

在 Android 设置里启用：

```text
Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> Voice Transform IME
```

然后在任意输入框中切换到 `Voice Transform IME`。

### 5. 测试纠错

说：

```text
今天上午上了两节计组课
```

如果系统 ASR 得到：

```text
今天上午上了两节祭祖课
```

后端会结合专业词库和用户画像，返回：

```text
今天上午上了两节计组课
```

用户确认后再插入当前输入框。

## Agent 设计

Original IME 版本的重点不是键盘体验，而是验证 Agent 闭环：

```text
Android SpeechRecognizer
  -> raw_text
  -> FastAPI /api/v1/correct-text
  -> MemoryTool 读取用户画像和术语库
  -> PinyinCorrectorTool 生成同音/别名纠错候选
  -> LLMRewriteTool 可选约束式改写
  -> corrected_text + reason + trace
  -> 用户确认插入
```

它体现的 Agent 开发重点：

- 有明确输入：ASR 文本、用户 ID、App 场景。
- 有可检索记忆：用户画像和专业术语库。
- 有工具调用：记忆读取、拼音纠错、LLM 改写。
- 有行动结果：返回可插入文本。
- 有可解释 trace：记录为什么修改、命中了哪些词、LLM 是否成功。
- 有失败回退：LLM 不可用时仍返回规则纠错结果。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android | Java, Android SDK, `InputMethodService`, `SpeechRecognizer`, `HttpURLConnection` |
| Backend | Python, FastAPI, Pydantic, SQLite, Uvicorn |
| Agent | `ContextCorrectionAgent`, `MemoryTool`, `PinyinCorrectorTool`, `LLMRewriteTool` |
| ASR | Android system `SpeechRecognizer`，可选 Baidu ASR |
| LLM | OpenAI-compatible Chat Completions / Responses API |

## 局限

- 键盘能力较基础，不适合作为完整日常输入法。
- 拼音候选和键盘交互不如成熟输入法。
- ASR 准确率依赖 Android 系统语音识别。
- 更推荐用 Trime 版本体验完整输入法。
