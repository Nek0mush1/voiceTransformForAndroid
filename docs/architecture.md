# 项目架构

## 目标

第一阶段验证“语音识别文本 -> 后端纠错 -> 稳定响应结构 -> 网页演示”的闭环。Android 端暂不实现，只保留目录骨架；后端和演示页面先跑通，方便后续客户端接入。

## 目录结构

```text
android/      # Android 客户端目录，第一步只保留骨架
backend/      # FastAPI 后端服务和网页演示页
docs/         # 架构说明和演示案例
```

## 后端模块

```text
backend/app/main.py                     # FastAPI 应用入口，挂载网页和 API
backend/app/api/v1/correct_text.py      # v1 文本纠错接口
backend/app/schemas/text_correction.py  # 请求和响应模型
backend/app/services/text_corrector.py  # MVP 纠错规则
backend/app/web/index.html              # 中英文切换网页演示页
```

## 接口约定

`POST /api/v1/correct-text`

请求字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string | 用户标识，MVP 阶段用于保留用户词库扩展入口 |
| `raw_text` | string | 待纠错文本 |
| `app_context` | string | 文本来源场景，例如 `chat`、`note`、`study` |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `raw_text` | string | 原始文本 |
| `corrected_text` | string | 纠错后文本 |
| `matched_terms` | string[] | 命中的专业术语 |
| `reason` | string | 纠错原因说明 |

## MVP 纠错策略

第一步使用确定性规则实现专业词纠错：

1. 接收语音识别后的文本。
2. 根据 `app_context` 匹配可用规则。
3. 将常见误识别文本替换为用户专业词库中的术语。
4. 返回固定响应结构，保证 Android 或网页前端后续可以直接按字段解析。

当前内置演示规则：

| 误识别 | 修正 | 命中术语 |
| --- | --- | --- |
| `祭祖课` | `计组课` | `计组` |

## 网页演示

当前 MVP 在 `/` 提供网页演示页：

```text
http://127.0.0.1:8000/
```

页面包含：

- 中文 / English 切换按钮。
- 原始文本输入。
- 用户 ID 和应用场景输入。
- 纠错结果、命中术语和原因展示。
- `/docs` API 文档入口。

## 前端约束

后续所有涉及自定义网页前端的步骤必须支持中英文切换：

- 页面顶部或主要操作区提供清晰的 `中文 / English` 切换按钮。
- 切换语言后，页面标题、按钮、表单标签、提示信息、结果说明等可见文案同步变化。
- 切换操作需要有明确效果，例如按钮激活态、文本即时刷新或轻量过渡反馈。
- 前端默认语言优先使用中文，同时保留英文文案，方便演示和后续国际化扩展。
- FastAPI 自动生成的 `/docs` 页面只作为接口调试工具，不作为最终产品前端。

## 后续扩展

- 将规则表迁移到用户词库存储，例如 SQLite、PostgreSQL 或本地 JSON。
- 根据 `user_id` 加载不同用户的专业词库。
- 引入拼音、编辑距离、上下文分类或大模型纠错。
- Android 端接入语音识别结果后调用 `/api/v1/correct-text`。
