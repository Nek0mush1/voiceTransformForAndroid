# GitHub Release 发布指南

本项目建议用 GitHub Releases 发布两个可安装版本：

| Release | 来源分支 | 上传文件 | 说明 |
| --- | --- | --- | --- |
| `v1.0.0-original-ime` | `main` | `voice-transform-original-ime-v1.0.0.apk` | 原始语音纠错输入法 |
| `v1.5.0-trime-ime` | `combine-with-Trime` | `voice-transform-trime-v1.5.0-universal.apk` | Trime/Rime 完整输入法 |

GitHub 的 Release 页面不是靠 Markdown 文件单独生成的，而是靠 tag + release notes + 上传附件生成。Markdown 的作用是给 Release 描述、仓库 README 和版本文档提供内容。

## 推荐仓库结构

```text
README.md                         # 仓库首页，说明项目目的、版本选择、技术栈
docs/version-original-ime.md      # Original IME 单独说明
docs/version-trime-ime.md         # Trime IME 单独说明
docs/release-guide.md             # 发布操作说明
```

## 发布 Original IME v1.0

### 1. 切到 main 分支

```powershell
git checkout main
git pull origin main
```

### 2. 构建 APK

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

建议重命名为：

```text
voice-transform-original-ime-v1.0.0.apk
```

### 3. 创建 tag

```powershell
git tag -a v1.0.0-original-ime -m "Release Original IME v1.0.0"
git push origin v1.0.0-original-ime
```

### 4. 在 GitHub 创建 Release

进入仓库页面：

```text
Releases -> Draft a new release
```

填写：

```text
Tag: v1.0.0-original-ime
Title: Original IME v1.0.0 - 语音纠错输入法 MVP
```

上传：

```text
voice-transform-original-ime-v1.0.0.apk
```

Release notes 可以复制本文下方的模板。

## 发布 Trime IME v1.5

### 1. 切到 combine-with-Trime 分支

```powershell
git checkout combine-with-Trime
git pull origin combine-with-Trime
```

### 2. 构建 APK

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :trime:assembleDebug
```

产物目录：

```text
android/trime/build/outputs/apk/debug/
```

推荐上传 universal APK，并重命名为：

```text
voice-transform-trime-v1.5.0-universal.apk
```

### 3. 创建 tag

```powershell
git tag -a v1.5.0-trime-ime -m "Release Trime IME v1.5.0"
git push origin v1.5.0-trime-ime
```

### 4. 在 GitHub 创建 Release

填写：

```text
Tag: v1.5.0-trime-ime
Title: Trime IME v1.5.0 - 完整输入法 + 语音纠错 Agent
```

上传：

```text
voice-transform-trime-v1.5.0-universal.apk
```

如果需要，也可以上传 ABI-specific APK：

```text
voice-transform-trime-v1.5.0-arm64-v8a.apk
voice-transform-trime-v1.5.0-armeabi-v7a.apk
voice-transform-trime-v1.5.0-x86_64.apk
```

## Release Notes 模板：Original IME v1.0

```markdown
## Original IME v1.0.0

这是 Voice Transform 的第一版可用 MVP，重点验证“Android 语音输入 -> 后端上下文纠错 Agent -> 用户确认插入”的完整链路。

### 适合使用

- 想快速体验语音识别纠错闭环。
- 想查看 Agent trace、用户画像和专业词库如何影响纠错。
- 想基于较简单的 Android InputMethodService 代码继续开发。

### 功能

- Android 自定义输入法 `Voice Transform IME`。
- 系统 `SpeechRecognizer` 获取原始识别文本。
- 后端 `/api/v1/correct-text` 进行上下文纠错。
- 支持用户画像、专业词库、拼音/同音/别名 fallback。
- 可选 OpenAI-compatible LLM relay。
- LLM 失败时自动 fallback。
- 用户确认后插入修正文或原文。

### 安装

1. 下载 `voice-transform-original-ime-v1.0.0.apk`。
2. 安装到 Android 手机。
3. 启用 `Voice Transform IME`。
4. 在 App 中配置后端地址。

常用后端地址：

- 模拟器：`http://10.0.2.2:8000`
- 真机局域网：`http://你的电脑IP:8000`
- 默认云端：`http://39.106.51.35:8000`

### 技术栈

- Android: Java, Android SDK, InputMethodService, SpeechRecognizer
- Backend: Python, FastAPI, Pydantic, SQLite
- Agent: MemoryTool, PinyinCorrectorTool, LLMRewriteTool
```

## Release Notes 模板：Trime IME v1.5

```markdown
## Trime IME v1.5.0

这是 Voice Transform 的完整输入法版本，在 Trime/Rime 开源输入法基础上接入语音纠错 Agent。相比 Original IME，它拥有更完整的拼音输入和键盘体验，更适合真实使用和项目展示。

### 推荐下载

- `voice-transform-trime-v1.5.0-universal.apk`

大多数 Android 手机可直接安装 universal APK。

### 功能

- Android 系统输入法 `Voice Transform Trime`。
- Trime/Rime 负责完整拼音输入体验。
- 内置 Voice Transform 专业词典。
- 长按空格触发语音输入和后端纠错。
- 后端 Agent 结合用户画像、专业词库、拼音候选和可选 LLM 输出修正文。
- 支持纠错确认弹窗：插入修正、插入原文、取消。
- 保留 Agent trace 和 LLM 调用日志，方便调试和展示。

### 使用

1. 下载并安装 `voice-transform-trime-v1.5.0-universal.apk`。
2. 在系统设置中启用 `Voice Transform Trime`。
3. 切换到该输入法。
4. 测试拼音：
   - `jizu` -> `计组`
   - `jiwang` -> `计网`
   - `shujujiegou` -> `数据结构`
5. 长按空格进行语音输入，松手后确认纠错结果。

### 技术栈

- Android IME: Trime, Rime, Kotlin/Java
- Backend: Python, FastAPI, SQLite
- Agent: ContextCorrectionAgent, MemoryTool, PinyinCorrectorTool, LLMRewriteTool
- LLM: OpenAI-compatible Chat Completions / Responses API

### 开源说明

本版本基于 Trime/Rime 生态做工程整合，保留相关开源许可和致谢信息。
```

## README 中的版本链接写法

仓库首页可以放这个表格：

```markdown
| 版本 | 分支 | 下载 | 文档 |
| --- | --- | --- | --- |
| Original IME v1.0 | `main` | [Release](https://github.com/Nek0mush1/voiceTransformForAndroid/releases/tag/v1.0.0-original-ime) | [说明](docs/version-original-ime.md) |
| Trime IME v1.5 | `combine-with-Trime` | [Release](https://github.com/Nek0mush1/voiceTransformForAndroid/releases/tag/v1.5.0-trime-ime) | [说明](docs/version-trime-ime.md) |
```

这样用户打开 GitHub 首页时，会先看到应该下载哪个版本，而不是被分支结构绕晕。

## 注意事项

- GitHub 只有一个默认分支首页。建议默认分支 README 也放两个版本入口。
- 如果 `main` 只保留 v1.0 代码，可以把 README 更新同步到 `main`，或把默认分支改成 `combine-with-Trime`。
- Release tag 应该固定到当时可构建的 commit，后续改代码不要移动旧 tag。
- APK 文件建议重命名成可读版本名，不要直接上传 `app-debug.apk`。
- 如果后端也变更，建议同一个 Release 上传对应的 `backend_update_cloud.zip`。
- 不要把 API key、服务器密码、本地数据库打进 Release 附件。
