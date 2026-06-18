# Voice Transform 输入法 MVP 使用文档

## 1. 当前完善程度

这个版本已经是一个可演示、可真实输入的 MVP，不是商业级输入法。

已经完成的闭环：

```text
Android 输入法
  -> 系统语音识别得到原始文本
  -> 后端 Agent 结合用户画像、专业词库、拼音候选、可选 LLM 纠错
  -> 输入法面板显示原文和修正文
  -> 用户确认后插入当前输入框
```

它适合用于项目展示、简历项目、课堂/面试演示、个人定制专业词纠错验证。

它暂时不等于市面上的成熟输入法，仍缺少完整键盘、候选词系统、离线 ASR、自动学习历史输入、大规模词库、云端账号体系和正式上架打包。

## 2. 功能总览

### Android 输入法功能

- 系统输入法注册：系统设置里可以看到 `Voice Transform IME`。
- 任意输入框可用：备忘录、聊天框、浏览器搜索框等。
- 语音输入：默认使用 Android 系统 `SpeechRecognizer`。
- 后端纠错：把识别到的 raw text 发给 `/api/v1/correct-text`。
- 确认插入：显示原文和修正文，可选择：
  - 插入修正
  - 插入原文
  - 取消
- 基础按键：
  - 删除
  - 空格
  - 回车
  - 切换输入法

### Android 设置页功能

打开桌面上的 `Voice Transform` App 可以配置：

- 后端地址
- 用户 ID
- 使用场景：`chat`、`note`、`study`
- 语音模式：
  - `system`：系统语音识别，推荐默认使用
  - `backend`：录音上传后端 ASR，需要配置 Baidu ASR key
- 用户画像：
  - 拉取画像
  - 保存画像
- 专业词库：
  - 新增词条
  - 刷新词库
  - 按 ID 删除词条
- 文本纠错测试：
  - 不切输入法时，也可以直接输入文本测试后端纠错效果

### 后端功能

- `POST /api/v1/correct-text`：文本纠错主接口。
- `GET /api/v1/profile/{user_id}`：读取用户画像。
- `PUT /api/v1/profile/{user_id}`：更新用户画像。
- `GET /api/v1/terms`：读取专业词库。
- `POST /api/v1/terms`：新增或更新词条。
- `DELETE /api/v1/terms/{term_id}`：删除词条。
- `GET /api/v1/debug/traces`：查看 Agent 工具调用 trace。
- `POST /api/v1/correct-audio`：可选后端 ASR + 纠错。
- `POST /api/v1/transcribe-correct`：同上，兼容清单命名。

### Agent 纠错能力

后端 Agent 流程：

```text
MemoryTool
  -> 读取用户画像和专业词库

PinyinCorrectorTool
  -> 根据别名、同音、近音、拼音候选做专业词纠错

LLMRewriteTool
  -> 如果配置了 LLM，则做约束改写
  -> 如果未配置或失败，则 fallback 到拼音纠错结果

Agent Trace
  -> 保存每次纠错为什么改、命中了什么词、LLM 是否成功
```

默认词库包含：

- 计组
- 计网
- 操作系统
- 数据结构
- 数据库
- Agent
- RAG
- Cache
- Transformer

## 3. 第一次运行

### 3.1 启动后端

在仓库根目录运行：

```powershell
.\backend\start_server.ps1
```

或者：

```powershell
cd backend
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

启动后访问：

```text
http://127.0.0.1:8000/
```

如果能打开网页演示页，说明后端正常。

### 3.2 安装 Android App

Debug APK 位置：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

可以用 Android Studio 直接运行，也可以用 adb 安装：

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

### 3.3 配置后端地址

打开手机上的 `Voice Transform` App。

模拟器填写：

```text
http://10.0.2.2:8000
```

真机填写电脑局域网 IP，例如：

```text
http://192.168.1.4:8000
```

真机和电脑必须在同一个网络下，后端必须保持运行。

推荐保持：

```text
Speech Mode = system
User ID = local_user
App Context = chat 或 study
```

## 4. 启用输入法

在 Android 系统设置里启用：

```text
Settings
  -> System
  -> Keyboard
  -> On-screen keyboard
  -> Manage keyboards
  -> Voice Transform IME
```

不同手机路径可能略有差异，可以在系统设置中搜索“键盘”或“输入法”。

启用后，在任意输入框长按或点击键盘切换按钮，切换到：

```text
Voice Transform IME
```

## 5. 核心使用流程

### 5.1 语音纠错输入

1. 打开备忘录、聊天框或浏览器搜索框。
2. 切换到 `Voice Transform IME`。
3. 点击“语音”。
4. 说一句话，例如：

   ```text
   今天上午上了两节祭祖课
   ```

5. 输入法面板会显示：

   ```text
   Raw: 今天上午上了两节祭祖课
   Corrected: 今天上午上了两节计组课
   ```

6. 选择：
   - `插入修正`：插入纠错后的文本。
   - `插入原文`：插入系统识别原文。
   - `取消`：不插入。

### 5.2 基础按键

输入法面板上的按钮：

- `删除`：删除光标前一个字符。
- `空格`：插入空格。
- `回车`：发送 Enter。
- `切换`：打开系统输入法选择器。

## 6. 维护用户画像

用户画像会影响 LLM prompt，也会作为 Agent trace 的上下文。

打开 `Voice Transform` App，在“用户画像”输入框中填写，例如：

```text
计算机专业大二学生，正在学习计组、计网、操作系统、数据结构、Agent 开发。
```

点击：

```text
保存画像
```

之后再纠错时，后端会使用这段画像作为用户上下文。

## 7. 维护专业词库

### 7.1 新增词条

在 App 设置页填写：

```text
词: 线程
别名: 现金
分类: system
权重: 1.0
```

点击：

```text
新增词条
```

然后点击：

```text
刷新词库
```

下次输入：

```text
老师讲了现金调度
```

期望纠错：

```text
老师讲了线程调度
```

### 7.2 别名怎么填

别名用于告诉系统“哪些错误识别应该被纠正到这个词”。

示例：

```text
词: Cache
别名: cash,快取
```

```text
词: Agent
别名: 真特,智能体
```

```text
词: 计组
别名: 祭祖,祭祖课
```

多个别名用英文逗号或中文逗号分隔。

### 7.3 删除词条

先点击：

```text
刷新词库
```

词库列表里会显示类似：

```text
#12 线程 [system] aliases: 现金
```

在删除 ID 输入框里填：

```text
12
```

点击：

```text
删除 ID
```

## 8. 测试后端纠错

不用手机也可以直接测试后端：

```powershell
cd backend
python -m unittest discover -s tests -p 'test_correction_unittest.py' -v
```

或者用接口测试：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:8000/api/v1/correct-text `
  -ContentType "application/json" `
  -Body '{"user_id":"local_user","raw_text":"今天上午上了两节祭祖课","app_context":"study"}'
```

## 9. 查看 Agent Trace

打开：

```text
http://127.0.0.1:8000/
```

进入 Agent Trace 区域，或直接请求：

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/api/v1/debug/traces?limit=10"
```

Trace 可以看到：

- 原始文本
- 修正文本
- 用户画像
- 命中的专业词
- 拼音候选
- LLM 是否成功
- 每个工具的调用结果

## 10. 可选 LLM 配置

不配置 LLM 时，系统仍然能工作，但只依赖词库和拼音纠错。

配置 OpenAI-compatible API：

```powershell
$env:LLM_BASE_URL="https://api.example.com/v1"
$env:LLM_API_KEY="your_api_key"
$env:LLM_MODEL="your_model"
```

然后重启后端。

注意：LLM 只做约束改写。Prompt 已限制它只修明显错误，不应该扩写或改变原意。

## 11. 可选后端 ASR 模式

默认不要用后端 ASR，除非你已经配置好 Baidu ASR。

后端 ASR 环境变量：

```powershell
$env:BAIDU_ASR_API_KEY="..."
$env:BAIDU_ASR_SECRET_KEY="..."
$env:BAIDU_ASR_DEV_PID="1537"
```

然后重启后端，在 Android 设置页把：

```text
Speech Mode = backend
```

这时输入法会录音上传到后端，由后端 ASR 转文字后再纠错。

如果没有配置 ASR key，`backend` 模式会失败。推荐日常使用 `system` 模式。

## 12. 常见问题

### 语音识别不准

当前默认使用 Android 系统语音识别。它的准确率取决于手机系统、网络、麦克风和说话环境。

后端 Agent 只能纠正“已经识别出来的文本”，不能完全弥补 ASR 把整句话识别错的问题。

### 为什么不如商业输入法

商业输入法有大规模 ASR、海量词库、用户长期输入历史、候选词排序和端云融合模型。当前项目是 MVP，重点展示：

- Android 输入法落地
- 个人记忆
- 专业词库
- 拼音纠错
- LLM 后处理
- Agent trace

### 后端请求失败

检查：

- 后端是否正在运行。
- Android 后端地址是否正确。
- 真机和电脑是否在同一局域网。
- Windows 防火墙是否允许 8000 端口。
- 模拟器是否使用 `http://10.0.2.2:8000`。

### 手机端新增词后没生效

检查：

- 新增词条的 `user_id` 是否和输入法设置页的 User ID 一致。
- 新增后是否点击了刷新词库。
- 输入法是否保存了同一个后端地址和 User ID。
- 该词是否有合适的别名，例如 `计组` 的别名可以填 `祭祖,祭祖课`。

## 13. 推荐演示脚本

1. 打开后端网页，展示默认纠错：

   ```text
   今天上午上了两节祭祖课 -> 今天上午上了两节计组课
   ```

2. 打开 Android App，展示用户画像和词库。

3. 新增词条：

   ```text
   线程 / 现金
   ```

4. 在 App 测试框提交：

   ```text
   老师讲了现金调度
   ```

5. 切到系统输入法，在备忘录里用语音输入同一句。

6. 展示输入法确认插入。

7. 回到网页或接口展示 Agent Trace，说明每一步工具调用。

