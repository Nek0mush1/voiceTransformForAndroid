# Deploy Backend to Public Server

Target server: `39.106.51.35`

## 1. Connect to the server

```bash
ssh root@39.106.51.35
```

If your server uses another user, replace `root`.

## 2. Install runtime

Ubuntu/Debian:

```bash
apt update
apt install -y python3 python3-venv python3-pip git unzip
```

CentOS/Alibaba Cloud Linux:

```bash
yum install -y python3 python3-pip git unzip
```

## 3. Upload the project

Run this command on your Windows computer from the repository root:

```powershell
scp -r .\backend root@39.106.51.35:/opt/voice-transform-backend
```

If `scp` is not available, compress the `backend` directory, upload it with the cloud console or an SFTP tool, then unzip it to `/opt/voice-transform-backend`.

## 4. Install Python dependencies on the server

```bash
cd /opt/voice-transform-backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 5. Start the backend

Temporary foreground start:

```bash
cd /opt/voice-transform-backend
source .venv/bin/activate
bash start_server_public.sh
```

The service should listen on `0.0.0.0:8000`.

## 6. Open port 8000

Cloud firewall/security group:

- Add inbound TCP rule: `8000`
- Source: `0.0.0.0/0` for testing

Server firewall, if enabled:

Ubuntu/Debian with ufw:

```bash
ufw allow 8000/tcp
```

CentOS/Alibaba Cloud Linux with firewalld:

```bash
firewall-cmd --permanent --add-port=8000/tcp
firewall-cmd --reload
```

## 7. Test from your computer

```powershell
Invoke-RestMethod http://39.106.51.35:8000/health
```

Expected result:

```json
{"status":"ok"}
```

Then test correction:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://39.106.51.35:8000/api/v1/correct-text `
  -ContentType "application/json" `
  -Body '{"user_id":"local_user","raw_text":"jin tian shang wu shang le liang jie ji zu ke","app_context":"chat"}'
```

## 8. Keep it running with systemd

Create a service file:

```bash
cat >/etc/systemd/system/voice-transform.service <<'EOF'
[Unit]
Description=Voice Transform Backend
After=network.target

[Service]
WorkingDirectory=/opt/voice-transform-backend
ExecStart=/opt/voice-transform-backend/.venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
```

Enable and start:

```bash
systemctl daemon-reload
systemctl enable --now voice-transform
systemctl status voice-transform
```

View logs:

```bash
journalctl -u voice-transform -f
```

## 9. Android backend URL

Use:

```text
http://39.106.51.35:8000
```

After reinstalling the Android app, this is now the default backend URL.

## 10. Update an Existing Server

From Windows, upload the generated update package:

```powershell
scp .\backend_update_cloud.zip root@39.106.51.35:/tmp/backend_update_cloud.zip
```

Then SSH into the server:

```bash
ssh root@39.106.51.35
```

Run:

```bash
cd /opt/voice-transform-backend
cp -a app "app.bak.$(date +%Y%m%d%H%M%S)"
unzip -o /tmp/backend_update_cloud.zip -d /opt/voice-transform-backend
source .venv/bin/activate
pip install -r requirements.txt
systemctl restart voice-transform || pkill -f "uvicorn app.main:app"
systemctl status voice-transform --no-pager || true
```

If `systemctl restart` stopped a foreground/manual service, start it again:

```bash
cd /opt/voice-transform-backend
source .venv/bin/activate
nohup bash start_server_public.sh >server.log 2>&1 &
```

Verify the updated backend:

```bash
curl http://39.106.51.35:8000/health
curl http://39.106.51.35:8000/api/v1/debug/status
```

`debug/status` should include:

```json
{"backend":"ok","asr":{"provider":"baidu","configured":true}}
```

## 11. ASR Notes

Baidu Cloud short speech recognition is ASR. In this project, backend speech mode records audio on Android, uploads it to:

```text
POST /api/v1/correct-audio
```

The server then calls Baidu short speech recognition using:

```bash
BAIDU_ASR_API_KEY
BAIDU_ASR_SECRET_KEY
BAIDU_ASR_DEV_PID
```

If `/api/v1/correct-audio` returns a Baidu error such as `audio trans failed`, the server has reached Baidu ASR and used the key. The remaining issue is usually audio format, audio content, sample rate, or Baidu ASR product/settings.
