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
