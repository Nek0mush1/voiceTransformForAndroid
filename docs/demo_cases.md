# 演示案例

## 案例 1：专业课名称纠错

请求：

```json
{
  "user_id": "local_user",
  "raw_text": "今天上午上了两节祭祖课",
  "app_context": "chat"
}
```

响应：

```json
{
  "raw_text": "今天上午上了两节祭祖课",
  "corrected_text": "今天上午上了两节计组课",
  "matched_terms": ["计组"],
  "reason": "根据用户专业词库和同音匹配修正。"
}
```

说明：`祭祖课 -> 计组课` 是语音识别中的同音误识别，后端根据专业词库命中 `计组` 并修正。

## 本地运行

在 `backend/` 目录安装依赖并启动服务：

```bash
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

Windows PowerShell 也可以在项目根目录运行：

```powershell
.\backend\start_server.ps1
```

## 网页演示

打开：

```text
http://127.0.0.1:8000/
```

网页默认展示中文界面，点击 `English` 后会切换为英文界面；再次点击 `中文` 会切回中文。切换时按钮激活态和页面文案会立即变化。

当前页面覆盖以下文案：

| 中文 | English |
| --- | --- |
| 文本纠错演示 | Text Correction Demo |
| 原始文本 | Raw Text |
| 纠错结果 | Corrected Text |
| 开始纠错 | Correct |
| 命中术语 | Matched Terms |
| 原因 | Reason |

## 接口调用

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/correct-text" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"local_user\",\"raw_text\":\"今天上午上了两节祭祖课\",\"app_context\":\"chat\"}"
```
