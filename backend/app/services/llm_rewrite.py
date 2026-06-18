from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass

from app import storage
from app.storage import ProfileRecord, TermRecord
from app.services.pinyin_corrector import PinyinCandidate


@dataclass(frozen=True)
class LLMResult:
    text: str
    success: bool
    error: str = ""


class LLMRewriteTool:
    def rewrite(
        self,
        raw_text: str,
        fallback_text: str,
        profile: ProfileRecord,
        terms: list[TermRecord],
        candidates: list[PinyinCandidate],
    ) -> LLMResult:
        config = storage.get_llm_config()
        if not config.base_url or not config.api_key or not config.model:
            return LLMResult(text=fallback_text, success=False, error="LLM not configured")

        prompt = self._build_prompt(raw_text, fallback_text, profile, terms, candidates)
        result = self.call_chat_completion(
            base_url=config.base_url,
            api_key=config.api_key,
            model=config.model,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are a context-aware ASR correction agent. "
                        "Only fix obvious speech recognition mistakes, homophones, "
                        "and user-domain terms. Do not rewrite the meaning. "
                        "Return only the final corrected text."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            timeout=12,
        )
        if not result.success:
            return LLMResult(text=fallback_text, success=False, error=result.error)
        if not result.text:
            return LLMResult(text=fallback_text, success=False, error="LLM returned empty text")
        return result

    def call_chat_completion(
        self,
        base_url: str,
        api_key: str,
        model: str,
        messages: list[dict[str, str]],
        timeout: int = 12,
    ) -> LLMResult:
        body = {
            "model": model,
            "messages": messages,
            "temperature": 0.1,
        }
        try:
            request = urllib.request.Request(
                url=self._chat_completions_url(base_url),
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
            text = payload["choices"][0]["message"]["content"].strip()
            return LLMResult(text=text, success=bool(text))
        except (KeyError, TimeoutError, urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError) as error:
            return LLMResult(text="", success=False, error=str(error))

    def _build_prompt(
        self,
        raw_text: str,
        fallback_text: str,
        profile: ProfileRecord,
        terms: list[TermRecord],
        candidates: list[PinyinCandidate],
    ) -> str:
        term_text = ", ".join(term.term for term in terms)
        candidate_text = json.dumps(
            [candidate.to_dict() for candidate in candidates],
            ensure_ascii=False,
        )
        return (
            f"User profile: {profile.profile_text}\n"
            f"Raw ASR text: {raw_text}\n"
            f"Rule-based fallback correction: {fallback_text}\n"
            f"User domain terms: {term_text}\n"
            f"Pinyin candidates: {candidate_text}\n"
            "Constraints: fix only clear wrong characters, homophone ASR mistakes, "
            "or domain-term recognition mistakes. If uncertain, keep the fallback text. "
            "Output only the final text."
        )

    def _chat_completions_url(self, base_url: str) -> str:
        normalized = base_url.strip().rstrip("/")
        if normalized.endswith("/chat/completions"):
            return normalized
        return f"{normalized}/chat/completions"
