# 项目架构

## 目标

目标是做一个上下文语音纠错输入法 Agent MVP。用户说出语音识别文本后，后端根据用户画像、专业词库、拼音候选和可选 LLM 改写，返回更符合上下文的文本。

当前完成范围：

- Step 1: 后端文本纠错 API 和网页演示。
- Step 2: SQLite 用户画像、专业词库、拼音候选纠错工具。
- Step 3: LLMRewriteTool、ContextCorrectionAgent、Agent trace。
- Step 4: Android `InputMethodService` 输入法 MVP。
- Step 5: 确认插入、Android 端画像/词库设置、可选后端 ASR。
- Step 6: 后端核心测试和项目展示文档。

## 目录结构

```text
android/      # Android 输入法和设置页
backend/      # FastAPI 后端、Agent 工具和网页演示
docs/         # 架构说明和演示案例
```

## 后端模块

```text
backend/app/main.py                     # FastAPI 应用入口
backend/app/storage.py                  # SQLite 初始化、profile/terms/traces 读写
backend/app/api/v1/correct_text.py      # 文本纠错接口
backend/app/api/v1/profile.py           # 用户画像接口
backend/app/api/v1/terms.py             # 专业词库接口
backend/app/api/v1/debug.py             # Agent trace 查询接口
backend/app/api/v1/correct_audio.py     # 可选后端 ASR + 纠错接口
backend/app/schemas/                    # Pydantic 请求和响应模型
backend/app/services/context_agent.py   # ContextCorrectionAgent 编排
backend/app/services/pinyin_corrector.py # PinyinCorrectorTool
backend/app/services/llm_rewrite.py     # LLMRewriteTool
backend/app/services/baidu_asr.py       # 可选 Baidu ASR Provider
backend/app/web/index.html              # 网页演示
backend/tests/                          # 后端核心回归测试
```

## Agent 流程

```text
raw_text
  -> MemoryTool: 读取用户画像和专业词库
  -> PinyinCorrectorTool: 根据 aliases / 同音候选生成修正候选
  -> LLMRewriteTool: 可选调用 OpenAI-compatible API 做约束改写
  -> corrected_text
  -> trace 保存到 SQLite
```

LLM 未配置或调用失败时，接口不会失败，会 fallback 到拼音纠错结果。

## 数据存储

使用 Python 标准库 `sqlite3`。默认数据库路径：

```text
backend/data/voice_transform.db
```

可通过环境变量覆盖：

```text
VOICE_TRANSFORM_DB
```

默认初始化：

- user: `local_user`
- profile: `计算机专业大二学生，正在学习计组、计网、操作系统、数据结构、Agent 开发。`
- terms: `计组`、`计网`、`操作系统`、`数据结构`、`数据库`、`Agent`、`RAG`、`Cache`、`Transformer`

## 接口

### Correct Text

`POST /api/v1/correct-text`

请求字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string | 用户标识 |
| `raw_text` | string | 待纠错文本 |
| `app_context` | string | 文本来源场景，例如 `chat`、`note`、`study` |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `raw_text` | string | 原始文本 |
| `corrected_text` | string | 纠错后文本 |
| `matched_terms` | string[] | 命中的专业术语 |
| `reason` | string | 纠错原因 |
| `trace_id` | string | trace ID |
| `agent_trace` | object | 本次 Agent 工具调用摘要 |

### Profile

```http
GET /api/v1/profile/local_user
PUT /api/v1/profile/local_user
```

### Terms

```http
GET /api/v1/terms
POST /api/v1/terms
DELETE /api/v1/terms/{term_id}
```

### Debug Trace

```http
GET /api/v1/debug/traces
```

### Optional Audio

```http
POST /api/v1/transcribe-correct
POST /api/v1/correct-audio
```

这两个接口等价，接收 multipart 音频并调用后端 ASR。第一版输入法默认不依赖它，而是使用 Android 系统 `SpeechRecognizer` 获取 raw text，再调用 `/correct-text`。

## LLM 配置

支持 OpenAI-compatible Chat Completions API：

```text
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
```

Prompt 包含：

- 用户画像
- 原始 ASR 文本
- 拼音工具修正结果
- 用户专业词库
- 拼音匹配候选
- 约束：只修正明显错误，不改变原意

## Android 当前状态

Android 目录已经是系统输入法 MVP：

- `VoiceInputMethodService` 注册为 `Voice Transform IME`。
- 键盘面板包含语音、删除、空格、回车、切换输入法按钮。
- 默认语音路径：Android `SpeechRecognizer` -> `/api/v1/correct-text` -> 展示 raw/corrected -> 用户确认插入。
- 失败时不直接上屏，用户可以插入原文或取消。
- 设置页可以保存后端地址、用户 ID、app context、语音模式。
- 设置页可以读写用户画像，新增/刷新/删除专业词条。
- 可选后端 ASR 模式会录音上传到 `/api/v1/correct-audio`，需要后端配置 ASR key。

## 测试

后端核心测试：

```powershell
cd backend
python -m unittest discover -s tests -p 'test_correction_unittest.py' -v
```

Android debug 构建：

```powershell
cd android
$env:JAVA_HOME='D:\Softs\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```
