# 演示案例

## 案例 1：默认词库纠错

请求：

```json
{
  "user_id": "local_user",
  "raw_text": "今天上午上了两节祭祖课",
  "app_context": "chat"
}
```

响应核心字段：

```json
{
  "raw_text": "今天上午上了两节祭祖课",
  "corrected_text": "今天上午上了两节计组课",
  "matched_terms": ["计组"],
  "reason": "根据用户专业词库和拼音候选修正；LLM 未配置或调用失败，已使用 fallback。"
}
```

说明：`祭祖课 -> 计组课` 是同音误识别，`计组` 来自默认专业词库。

## 案例 2：新增词库后参与纠错

新增术语：

```json
{
  "user_id": "local_user",
  "term": "线程",
  "category": "system",
  "aliases": ["现金"],
  "weight": 1.0
}
```

之后请求：

```json
{
  "user_id": "local_user",
  "raw_text": "老师讲了现金调度",
  "app_context": "study"
}
```

响应核心字段：

```json
{
  "corrected_text": "老师讲了线程调度",
  "matched_terms": ["线程"]
}
```

## 案例 3：LLM 未配置时 fallback

如果没有设置：

```text
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
```

`POST /api/v1/correct-text` 仍然返回结果，`agent_trace.llm_success` 为 `false`，并使用 `PinyinCorrectorTool` 的修正文本。

## 案例 4：Android 输入法确认插入

1. 启动后端。
2. Android 设置页保持语音模式为 `system`。
3. 切换到 `Voice Transform IME`。
4. 在备忘录、聊天框或浏览器搜索框中点击语音按钮。
5. 说：`今天上午上了两节祭祖课`。
6. 键盘面板会显示：
   - Raw: `今天上午上了两节祭祖课`
   - Corrected: `今天上午上了两节计组课`
7. 点击“插入修正”后，上屏 `今天上午上了两节计组课`。

如果后端请求失败，键盘仍会保留 raw text，可点击“插入原文”或“取消”。

## 案例 5：手机端新增词后参与纠错

在 Android 设置页新增：

```text
term: 线程
aliases: 现金
category: system
weight: 1.0
```

刷新词库后，在输入法里说或在测试框里提交：

```text
老师讲了现金调度
```

期望结果：

```text
老师讲了线程调度
```

## 常用命令

启动后端：

```powershell
.\backend\start_server.ps1
```

查看用户画像：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/v1/profile/local_user
```

查看词库：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/terms?user_id=local_user"
```

查看 trace：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/debug/traces?limit=10"
```

网页演示：

```text
http://127.0.0.1:8000/
```

后端测试：

```powershell
cd backend
python -m unittest discover -s tests -p 'test_correction_unittest.py' -v
```
