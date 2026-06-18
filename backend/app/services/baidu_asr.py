import base64
import os
import uuid

import requests


class BaiduAsrError(RuntimeError):
    pass


class BaiduAsrClient:
    TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    ASR_URL = "https://vop.baidu.com/server_api"

    def __init__(self) -> None:
        self.api_key = os.getenv("BAIDU_ASR_API_KEY", "").strip()
        self.secret_key = os.getenv("BAIDU_ASR_SECRET_KEY", "").strip()
        self.cuid = os.getenv("BAIDU_ASR_CUID", f"voice-transform-{uuid.getnode()}").strip()
        self.dev_pid = int(os.getenv("BAIDU_ASR_DEV_PID", "1537"))

    def transcribe(self, audio_bytes: bytes, audio_format: str = "m4a", sample_rate: int = 16000) -> str:
        if not self.api_key or not self.secret_key:
            raise BaiduAsrError("BAIDU_ASR_API_KEY and BAIDU_ASR_SECRET_KEY are required")
        if not audio_bytes:
            raise BaiduAsrError("Audio file is empty")

        token = self._get_access_token()
        payload = {
            "format": audio_format,
            "rate": sample_rate,
            "channel": 1,
            "cuid": self.cuid,
            "token": token,
            "dev_pid": self.dev_pid,
            "len": len(audio_bytes),
            "speech": base64.b64encode(audio_bytes).decode("ascii"),
        }
        response = requests.post(self.ASR_URL, json=payload, timeout=30)
        response.raise_for_status()
        data = response.json()
        if data.get("err_no") != 0:
            raise BaiduAsrError(f"Baidu ASR failed: {data}")

        results = data.get("result") or []
        if not results:
            raise BaiduAsrError("Baidu ASR returned no text")
        return str(results[0]).strip()

    def _get_access_token(self) -> str:
        response = requests.post(
            self.TOKEN_URL,
            params={
                "grant_type": "client_credentials",
                "client_id": self.api_key,
                "client_secret": self.secret_key,
            },
            timeout=10,
        )
        response.raise_for_status()
        data = response.json()
        token = data.get("access_token")
        if not token:
            raise BaiduAsrError(f"Baidu token failed: {data}")
        return str(token)
