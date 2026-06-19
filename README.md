# Voice Transform for Android

Voice Transform 是一个 Android 语音输入纠错项目。它关注的不是聊天，而是输入法场景：用户在手机上说完一句话后，系统根据个人背景、专业词库和上下文，把语音识别结果中明显不合适的同音词修正掉，再由用户确认是否上屏。

项目的典型问题是：

```text
今天上午上了两节祭祖课
```

在计算机专业学生的使用场景下，这句话更可能是：

```text
今天上午上了两节计组课
```

## 下载和使用

已发布的 APK 在 GitHub Releases：

[https://github.com/Nek0mush1/voiceTransformForAndroid/releases](https://github.com/Nek0mush1/voiceTransformForAndroid/releases)

| 版本              | 分支                 | 说明                                           |
| ----------------- | -------------------- | ---------------------------------------------- |
| Original IME v1.0 | `main`               | 第一版输入法，重点验证语音识别和后端纠错闭环   |
| Trime IME v1.5    | `combine-with-Trime` | 结合 Trime/Rime 的完整输入法版本，推荐安装体验 |

Release APK 默认连接已经部署好的云端后端：

```text
http://39.106.51.35:8000
```

因此，普通使用者下载 APK 后不需要自己部署后端。只要云端服务在线，输入法就可以把识别结果发送到后端做纠错。

需要注意的是，后端 ASR 依赖服务器侧配置。如果使用的是“录音上传到后端识别”的模式，云端服务器必须已经配置好 ASR 服务；如果服务器没有配置 ASR，语音录音上传模式不能正常转文字。Original IME 也支持使用手机系统语音识别，再把识别出的文本交给后端纠错，这种模式不依赖服务器 ASR。

## 设计初衷

通用语音识别通常不了解用户是谁，也不知道用户最近在学什么、常用哪些专业词。对计算机、工程、医学、法律等领域用户来说，很多词听起来相近，但含义完全不同。

这个项目希望把语音输入拆成两步：

1. 先用 ASR 得到原始文本。
2. 再用用户画像、个人词库、拼音/同音规则和可选 LLM 做二次纠错。

这样输入法不需要替代底层语音识别，也能在专业词和个人常用词上补一层上下文判断。

## 主要功能

- Android 输入法客户端，可在任意文本输入框中使用。
- Trime/Rime 版本支持更完整的中文拼音输入体验。
- 后端提供文本纠错接口：`POST /api/v1/correct-text`。
- 支持用户画像和个人专业词库。
- 支持拼音、同音、别名规则纠错。
- 可选接入 OpenAI-compatible LLM relay。
- LLM 不可用时回退到本地规则纠错。
- 每次纠错保留 trace，方便查看命中的词、纠错原因和工具调用过程。

## Agent 思路

这里的 Agent 不是一个独立聊天窗口，而是嵌在输入法流程里的纠错模块。

```text
语音识别文本
  -> 读取用户画像和专业词库
  -> 根据拼音、同音和别名生成候选修正
  -> 可选调用 LLM 做约束式改写
  -> 返回修正文本、原因和 trace
  -> 用户确认后插入当前输入框
```

后端工具链路：

```text
MemoryTool -> PinyinCorrectorTool -> LLMRewriteTool
```

其中：

- `MemoryTool` 读取用户画像和词库。
- `PinyinCorrectorTool` 处理专业词、同音词和别名匹配。
- `LLMRewriteTool` 在配置 LLM 时做保守改写，只修正明显错误。

## 技术栈

| 模块               | 技术                                                        |
| ------------------ | ----------------------------------------------------------- |
| Android 原始输入法 | Java, Android SDK, `InputMethodService`, `SpeechRecognizer` |
| Android 完整输入法 | Trime, Rime, Kotlin/Java                                    |
| 后端               | Python, FastAPI, Pydantic, SQLite, Uvicorn                  |
| 纠错 Agent         | MemoryTool, PinyinCorrectorTool, LLMRewriteTool             |
| ASR                | Android 系统语音识别，可选 Baidu ASR                        |
| LLM                | OpenAI-compatible Chat Completions / Responses API          |

## 项目结构

```text
android/   Android App、原始输入法和 Trime 输入法
backend/   FastAPI 后端、纠错 Agent、数据存储和调试页面
docs/      使用、架构、部署和版本说明
```

## Android 使用

安装 APK 后，在系统设置中启用输入法：

```text
Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards
```

默认后端地址为：

```text
http://39.106.51.35:8000
```

如果只是体验 Release 版本，一般不需要修改这个地址。

如果想连接自己的后端，可以在 App 设置页改成自己的服务器地址，例如：

```text
http://your-server-ip:8000
```

## 开发和自部署

下面的步骤只面向开发者或想自己部署后端的人。普通 Release 用户不需要执行。

```bash
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

检查服务：

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

本地调试 Android 时常用后端地址：

- Android 模拟器访问本机：`http://10.0.2.2:8000`
- 真机访问电脑：使用电脑局域网 IP，例如 `http://192.168.1.4:8000`
- 云端后端：`http://39.106.51.35:8000`

## 本地构建

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

# Original IME
.\gradlew.bat :app:assembleDebug

# Trime IME
.\gradlew.bat :trime:assembleDebug
```

## 文档

- [Original IME 版本说明](docs/version-original-ime.md)
- [Trime IME 版本说明](docs/version-trime-ime.md)
- [Release 发布指南](docs/release-guide.md)
- [项目架构](docs/architecture.md)
- [使用文档](docs/user_guide.md)
- [部署服务器](docs/deploy_server.md)

## 当前状态

这是一个可运行的项目原型。当前重点是把“输入法入口、上下文记忆、专业词纠错、可选 LLM、纠错 trace”这条链路跑通。后续可以继续完善输入法交互、候选词排序、多用户隔离、端到端测试和正式发布流程。

## 安全说明

- 不要提交 API key、服务器密码、本地数据库或私有配置。
- LLM API key 在接口返回中只显示 masked key。
- LLM 调用日志不记录 API key。
- LLM 失败时后端会回退到规则纠错。
