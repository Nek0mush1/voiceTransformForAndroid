from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from app import storage
from app.storage import ProfileRecord, TermRecord
from app.services.pinyin_corrector import PinyinCandidate


DEFAULT_LLM_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/126.0.0.0 Safari/537.36"
)
DEFAULT_LLM_TIMEOUT_SECONDS = 30
MAX_LLM_TIMEOUT_SECONDS = 90


@dataclass(frozen=True)
class LLMResult:
    text: str
    success: bool
    error: str = ""
    correction_method: str = "llm"
    base_url: str = ""
    model: str = ""
    wire_api: str = ""
    duration_ms: int = 0


class LLMRewriteTool:
    def rewrite(
        self,
        raw_text: str,
        fallback_text: str,
        profile: ProfileRecord,
        terms: list[TermRecord],
        candidates: list[PinyinCandidate],
        trace_id: str = "",
        user_id: str = "",
    ) -> LLMResult:
        config = storage.get_llm_config()
        normalized_wire_api = storage.normalize_llm_wire_api(config.wire_api)
        if not config.base_url or not config.api_key or not config.model:
            result = LLMResult(
                text=fallback_text,
                success=False,
                error="LLM not configured",
                correction_method="rule_pinyin_fallback" if fallback_text != raw_text else "raw_text",
                base_url=config.base_url,
                model=config.model,
                wire_api=normalized_wire_api,
            )
            self._save_correction_log(trace_id, user_id, raw_text, fallback_text, result)
            return result

        prompt = self._build_prompt(raw_text, fallback_text, profile, terms, candidates)
        timeout = self._timeout_for_text(raw_text)
        started_at = time.perf_counter()
        result = self.call_model(
            base_url=config.base_url,
            api_key=config.api_key,
            model=config.model,
            wire_api=normalized_wire_api,
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
            timeout=timeout,
        )
        duration_ms = int((time.perf_counter() - started_at) * 1000)
        if not result.success:
            fallback_result = LLMResult(
                text=fallback_text,
                success=False,
                error=result.error or "LLM returned empty text",
                correction_method="rule_pinyin_fallback" if fallback_text != raw_text else "raw_text",
                base_url=config.base_url,
                model=config.model,
                wire_api=normalized_wire_api,
                duration_ms=duration_ms,
            )
            self._save_correction_log(trace_id, user_id, raw_text, fallback_text, fallback_result)
            return fallback_result
        if not result.text:
            empty_result = LLMResult(
                text=fallback_text,
                success=False,
                error="LLM returned empty text",
                correction_method="rule_pinyin_fallback" if fallback_text != raw_text else "raw_text",
                base_url=config.base_url,
                model=config.model,
                wire_api=normalized_wire_api,
                duration_ms=duration_ms,
            )
            self._save_correction_log(trace_id, user_id, raw_text, fallback_text, empty_result)
            return empty_result

        final_result = LLMResult(
            text=result.text,
            success=True,
            correction_method="llm",
            base_url=config.base_url,
            model=config.model,
            wire_api=normalized_wire_api,
            duration_ms=duration_ms,
        )
        self._save_correction_log(trace_id, user_id, raw_text, fallback_text, final_result)
        return final_result

    def call_model(
        self,
        base_url: str,
        api_key: str,
        model: str,
        wire_api: str,
        messages: list[dict[str, str]],
        timeout: int = 12,
    ) -> LLMResult:
        normalized_wire_api = storage.normalize_llm_wire_api(wire_api)
        if normalized_wire_api == storage.LLM_WIRE_API_RESPONSES:
            return self.call_response(
                base_url=base_url,
                api_key=api_key,
                model=model,
                messages=messages,
                timeout=timeout,
            )
        return self.call_chat_completion(
            base_url=base_url,
            api_key=api_key,
            model=model,
            messages=messages,
            timeout=timeout,
        )

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
                headers=self._headers(api_key),
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
            text = self._extract_chat_completion_text(payload)
            return LLMResult(text=text, success=bool(text))
        except urllib.error.HTTPError as error:
            return LLMResult(text="", success=False, error=self._format_http_error(error))
        except (KeyError, TimeoutError, urllib.error.URLError, json.JSONDecodeError) as error:
            return LLMResult(text="", success=False, error=str(error))

    def call_response(
        self,
        base_url: str,
        api_key: str,
        model: str,
        messages: list[dict[str, str]],
        timeout: int = 12,
    ) -> LLMResult:
        instructions, input_text = self._messages_to_response_parts(messages)
        body = {
            "model": model,
            "input": input_text,
        }
        if instructions:
            body["instructions"] = instructions
        try:
            request = urllib.request.Request(
                url=self._responses_url(base_url),
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers=self._headers(api_key),
                method="POST",
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
            text = self._extract_response_text(payload)
            return LLMResult(text=text, success=bool(text))
        except urllib.error.HTTPError as error:
            return LLMResult(text="", success=False, error=self._format_http_error(error))
        except (KeyError, TimeoutError, urllib.error.URLError, json.JSONDecodeError) as error:
            return LLMResult(text="", success=False, error=str(error))

    def _save_correction_log(
        self,
        trace_id: str,
        user_id: str,
        raw_text: str,
        fallback_text: str,
        result: LLMResult,
    ) -> None:
        if not trace_id or not user_id:
            return
        storage.save_llm_call_log(
            trace_id=trace_id,
            user_id=user_id,
            raw_text=raw_text,
            fallback_text=fallback_text,
            output_text=result.text,
            success=result.success,
            error=result.error,
            correction_method=result.correction_method,
            base_url=result.base_url,
            model=result.model,
            wire_api=result.wire_api,
            duration_ms=result.duration_ms,
        )

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

    def _timeout_for_text(self, raw_text: str) -> int:
        configured_timeout = os.getenv("LLM_TIMEOUT_SECONDS", "").strip()
        if configured_timeout:
            try:
                return max(5, min(MAX_LLM_TIMEOUT_SECONDS, int(configured_timeout)))
            except ValueError:
                pass

        extra_seconds = len(raw_text) // 80 * 15
        return min(MAX_LLM_TIMEOUT_SECONDS, DEFAULT_LLM_TIMEOUT_SECONDS + extra_seconds)

    def _chat_completions_url(self, base_url: str) -> str:
        normalized = base_url.strip().rstrip("/")
        if normalized.endswith("/chat/completions"):
            return normalized
        return f"{normalized}/chat/completions"

    def _responses_url(self, base_url: str) -> str:
        normalized = base_url.strip().rstrip("/")
        if normalized.endswith("/responses"):
            return normalized
        return f"{normalized}/responses"

    def _headers(self, api_key: str) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": os.getenv("LLM_USER_AGENT", DEFAULT_LLM_USER_AGENT).strip()
            or DEFAULT_LLM_USER_AGENT,
        }

    def _messages_to_response_parts(self, messages: list[dict[str, str]]) -> tuple[str, str]:
        instructions = "\n\n".join(
            message.get("content", "")
            for message in messages
            if message.get("role") == "system" and message.get("content")
        )
        input_text = "\n\n".join(
            f"{message.get('role', 'user')}: {message.get('content', '')}"
            for message in messages
            if message.get("role") != "system" and message.get("content")
        )
        return instructions, input_text

    def _extract_chat_completion_text(self, payload: dict[str, Any]) -> str:
        content = payload["choices"][0]["message"]["content"]
        if isinstance(content, str):
            return content.strip()
        if isinstance(content, list):
            return self._extract_text_from_content_list(content)
        return ""

    def _extract_response_text(self, payload: dict[str, Any]) -> str:
        output_text = payload.get("output_text")
        if isinstance(output_text, str):
            return output_text.strip()

        pieces: list[str] = []
        for item in payload.get("output", []):
            if not isinstance(item, dict):
                continue
            content = item.get("content", [])
            if isinstance(content, list):
                text = self._extract_text_from_content_list(content)
                if text:
                    pieces.append(text)
        return "\n".join(pieces).strip()

    def _extract_text_from_content_list(self, content: list[Any]) -> str:
        pieces: list[str] = []
        for part in content:
            if not isinstance(part, dict):
                continue
            text = part.get("text")
            if isinstance(text, str):
                pieces.append(text)
                continue
            nested_text = part.get("content")
            if isinstance(nested_text, str):
                pieces.append(nested_text)
        return "\n".join(pieces).strip()

    def _format_http_error(self, error: urllib.error.HTTPError) -> str:
        details = ""
        try:
            details = error.read().decode("utf-8", errors="replace").strip()
        except Exception:
            details = ""
        message = f"HTTP {error.code} {error.reason}".strip()
        if error.url:
            message = f"{message} at {error.url}"
        if details:
            message = f"{message}: {details}"
        return message
