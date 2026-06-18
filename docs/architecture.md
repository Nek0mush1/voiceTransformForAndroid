# 项目架构

## 目标

目标是做一个上下文语音纠错输入法 Agent MVP。用户说出语音识别文本后，后端根据用户画像、专业词库、拼音候选和可选 LLM 改写，返回更符合上下文的文本。

当前完成范围：

- Step 1: 后端文本纠错 API 和网页演示。
- Step 2: SQLite 用户画像、专业词库、拼音候选纠错工具。
- Step 3: LLMRewriteTool、ContextCorrectionAgent、Agent trace。

## 目录结构

```text
android/      # Android 原型客户端，Step 4 将改造成输入法
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
backend/app/schemas/                    # Pydantic 请求和响应模型
backend/app/services/context_agent.py   # ContextCorrectionAgent 编排
backend/app/services/pinyin_corrector.py # PinyinCorrectorTool
backend/app/services/llm_rewrite.py     # LLMRewriteTool
backend/app/web/index.html              # 网页演示
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

当前 Android 目录是普通客户端原型，能够输入文本、调用后端并展示结果。它还不是系统输入法。

Step 4 需要继续做：

- Kotlin Android 项目或迁移
- `InputMethodService`
- 输入法声明 XML
- 语音按钮、删除、空格、回车、切换输入法按钮
- 使用 `InputConnection.commitText()` 把修正文本插入当前输入框
