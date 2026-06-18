from __future__ import annotations

from dataclasses import dataclass
from uuid import uuid4

from app import storage
from app.schemas.text_correction import TextCorrectionRequest
from app.services.llm_rewrite import LLMRewriteTool
from app.services.pinyin_corrector import PinyinCorrectorTool


@dataclass(frozen=True)
class CorrectionAgentResult:
    raw_text: str
    corrected_text: str
    matched_terms: list[str]
    reason: str
    correction_method: str
    llm_used: bool
    llm_error: str
    trace_id: str
    agent_trace: dict[str, object]


class ContextCorrectionAgent:
    def __init__(self) -> None:
        self.pinyin_tool = PinyinCorrectorTool()
        self.llm_tool = LLMRewriteTool()

    def correct(self, payload: TextCorrectionRequest) -> CorrectionAgentResult:
        trace_id = str(uuid4())
        profile = storage.get_profile(payload.user_id)
        terms = storage.list_terms(payload.user_id)

        pinyin_candidates = self.pinyin_tool.suggest(payload.raw_text, terms)
        pinyin_text, matched_terms = self.pinyin_tool.apply(payload.raw_text, pinyin_candidates)
        llm_result = self.llm_tool.rewrite(
            raw_text=payload.raw_text,
            fallback_text=pinyin_text,
            profile=profile,
            terms=terms,
            candidates=pinyin_candidates,
            trace_id=trace_id,
            user_id=payload.user_id,
        )
        corrected_text = llm_result.text
        reason = self._reason(matched_terms, llm_result.success)
        candidate_dicts = [candidate.to_dict() for candidate in pinyin_candidates]
        tools = [
            {
                "name": "MemoryTool",
                "success": True,
                "profile_summary": profile.profile_text,
                "terms_count": len(terms),
            },
            {
                "name": "PinyinCorrectorTool",
                "success": True,
                "candidates": candidate_dicts,
                "fallback_text": pinyin_text,
            },
            {
                "name": "LLMRewriteTool",
                "success": llm_result.success,
                "error": llm_result.error,
                "correction_method": llm_result.correction_method,
                "model": llm_result.model,
                "wire_api": llm_result.wire_api,
                "duration_ms": llm_result.duration_ms,
            },
        ]
        agent_trace = {
            "trace_id": trace_id,
            "profile_summary": profile.profile_text,
            "matched_terms": matched_terms,
            "pinyin_candidates": candidate_dicts,
            "llm_success": llm_result.success,
            "llm_error": llm_result.error,
            "correction_method": llm_result.correction_method,
            "llm_model": llm_result.model,
            "llm_wire_api": llm_result.wire_api,
            "llm_duration_ms": llm_result.duration_ms,
            "tools": tools,
        }

        storage.save_trace(
            trace_id=trace_id,
            user_id=payload.user_id,
            raw_text=payload.raw_text,
            corrected_text=corrected_text,
            profile_summary=profile.profile_text,
            matched_terms=matched_terms,
            pinyin_candidates=candidate_dicts,
            llm_success=llm_result.success,
            llm_error=llm_result.error,
            tools=tools,
        )

        return CorrectionAgentResult(
            raw_text=payload.raw_text,
            corrected_text=corrected_text,
            matched_terms=matched_terms,
            reason=reason,
            correction_method=llm_result.correction_method,
            llm_used=llm_result.success,
            llm_error=llm_result.error,
            trace_id=trace_id,
            agent_trace=agent_trace,
        )

    def _reason(self, matched_terms: list[str], llm_success: bool) -> str:
        if matched_terms and llm_success:
            return "根据用户画像、专业词库、拼音候选和 LLM 改写修正。"
        if matched_terms:
            return "根据用户专业词库和拼音候选修正；LLM 未配置或调用失败，已使用 fallback。"
        if llm_success:
            return "未命中拼音候选，使用 LLM 在约束下检查文本。"
        return "未命中用户专业词库，且 LLM 未配置或调用失败，保留原文。"
