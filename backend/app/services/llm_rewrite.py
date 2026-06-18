from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass

from app.storage import ProfileRecord, TermRecord
from app.services.pinyin_corrector import PinyinCandidate


@dataclass(frozen=True)
class LLMResult:
    text: str
    success: bool
    error: str = ""


class LLMRewriteTool:
    def __init__(self) -> None:
        self.base_url = os.getenv("LLM_BASE_URL", "").rstrip("/")
        self.api_key = os.getenv("LLM_API_KEY", "")
        self.model = os.getenv("LLM_MODEL", "")

    def rewrite(
        self,
        raw_text: str,
        fallback_text: str,
        profile: ProfileRecord,
        terms: list[TermRecord],
        candidates: list[PinyinCandidate],
    ) -> LLMResult:
        if not self.base_url or not self.api_key or not self.model:
            return LLMResult(text=fallback_text, success=False, error="LLM not configured")

        prompt = self._build_prompt(raw_text, fallback_text, profile, terms, candidates)
        body = {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "你是上下文语音纠错 Agent。只修正明显的 ASR 误识别，"
                        "不要改变原意，不要扩写，不要解释，只返回修正后的文本。"
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.1,
        }

        try:
            request = urllib.request.Request(
                url=f"{self.base_url}/chat/completions",
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=12) as response:
                payload = json.loads(response.read().decode("utf-8"))
            text = payload["choices"][0]["message"]["content"].strip()
            if not text:
                return LLMResult(text=fallback_text, success=False, error="LLM returned empty text")
            return LLMResult(text=text, success=True)
        except (KeyError, TimeoutError, urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError) as error:
            return LLMResult(text=fallback_text, success=False, error=str(error))

    def _build_prompt(
        self,
        raw_text: str,
        fallback_text: str,
        profile: ProfileRecord,
        terms: list[TermRecord],
        candidates: list[PinyinCandidate],
    ) -> str:
        term_text = "、".join(term.term for term in terms)
        candidate_text = json.dumps(
            [candidate.to_dict() for candidate in candidates],
            ensure_ascii=False,
        )
        return (
            f"用户画像：{profile.profile_text}\n"
            f"原始 ASR 文本：{raw_text}\n"
            f"拼音工具修正结果：{fallback_text}\n"
            f"用户专业词库：{term_text}\n"
            f"拼音匹配候选：{candidate_text}\n"
            "约束：只修正明显错字、同音误识别或专业词误识别；"
            "如果不确定，保留拼音工具修正结果。只输出最终文本。"
        )
