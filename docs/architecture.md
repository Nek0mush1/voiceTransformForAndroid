# 项目架构

## 目标

当前 MVP 验证“语音识别文本 -> 后端纠错 -> 稳定响应结构 -> 网页 / Android 展示”的闭环。

- Step 1: 后端提供纠错 API 和网页演示。
- Step 2: Android 原生客户端接入后端 API。
- Step 3: 完成本地端到端联调和运行说明。

## 目录结构

```text
android/      # Android 原生客户端
backend/      # FastAPI 后端服务和网页演示
docs/         # 架构说明和演示案例
```

## 后端模块

```text
backend/app/main.py                     # FastAPI 应用入口，挂载网页和 API
backend/app/api/v1/correct_text.py      # v1 文本纠错接口
backend/app/schemas/text_correction.py  # 请求和响应模型
backend/app/services/text_corrector.py  # MVP 纠错规则
backend/app/web/index.html              # 中英文切换网页演示
```

## Android 模块

```text
android/app/src/main/java/com/example/voicetransform/MainActivity.java
android/app/src/main/java/com/example/voicetransform/api/CorrectionApiClient.java
android/app/src/main/java/com/example/voicetransform/model/
android/app/src/main/res/layout/activity_main.xml
```

Android 端使用 Java + Android SDK 实现。网络请求使用 `HttpURLConnection` 和 `org.json`，避免引入第三方 Android 依赖。

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

第一版使用确定性规则实现专业词纠错：

1. 接收语音识别后的文本。
2. 根据 `app_context` 匹配可用规则。
3. 将常见误识别文本替换为用户专业词库中的术语。
4. 返回固定响应结构，保证 Android 和网页前端可以直接解析。

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

- 中文 / English 切换按钮
- 原始文本输入
- 用户 ID 和应用场景输入
- 纠错结果、命中术语和原因展示
- `/docs` API 文档入口

## Android 联调

模拟器访问开发机后端时使用：

```text
http://10.0.2.2:8000
```

真机访问时需要把应用里的后端地址改成电脑的局域网 IP，例如：

```text
http://192.168.1.4:8000
```

## 后续扩展

- 将规则表迁移到用户词库存储，例如 SQLite、PostgreSQL 或本地 JSON。
- 根据 `user_id` 加载不同用户的专业词库。
- 引入拼音、编辑距离、上下文分类或大模型纠错。
- 增加 Android 端自动化测试和后端单元测试。
