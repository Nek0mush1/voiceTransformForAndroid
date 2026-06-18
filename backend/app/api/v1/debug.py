import os

from fastapi import APIRouter, Query

from app import storage
from app.schemas.memory import LLMCallLogResponse, TraceResponse


router = APIRouter(tags=["debug"])


@router.get("/debug/status")
def debug_status() -> dict[str, object]:
    llm_config = storage.get_llm_config()
    return {
        "backend": "ok",
        "asr": {
            "provider": "baidu",
            "configured": bool(os.getenv("BAIDU_ASR_API_KEY", "").strip())
            and bool(os.getenv("BAIDU_ASR_SECRET_KEY", "").strip()),
            "dev_pid": os.getenv("BAIDU_ASR_DEV_PID", "1537"),
        },
        "llm": {
            "configured": bool(llm_config.base_url and llm_config.api_key and llm_config.model),
            "base_url": llm_config.base_url,
            "model": llm_config.model,
            "wire_api": llm_config.wire_api,
        },
    }


@router.get("/debug/traces", response_model=list[TraceResponse])
def list_debug_traces(limit: int = Query(default=20, ge=1, le=100)) -> list[TraceResponse]:
    return [
        TraceResponse(
            id=record.id,
            user_id=record.user_id,
            raw_text=record.raw_text,
            corrected_text=record.corrected_text,
            profile_summary=record.profile_summary,
            matched_terms=record.matched_terms,
            pinyin_candidates=record.pinyin_candidates,
            llm_success=record.llm_success,
            llm_error=record.llm_error,
            tools=record.tools,
            created_at=record.created_at,
        )
        for record in storage.list_traces(limit)
    ]


@router.get("/debug/llm-calls", response_model=list[LLMCallLogResponse])
def list_llm_call_logs(limit: int = Query(default=50, ge=1, le=50)) -> list[LLMCallLogResponse]:
    return [
        LLMCallLogResponse(
            id=record.id,
            trace_id=record.trace_id,
            user_id=record.user_id,
            raw_text=record.raw_text,
            fallback_text=record.fallback_text,
            output_text=record.output_text,
            success=record.success,
            error=record.error,
            correction_method=record.correction_method,
            base_url=record.base_url,
            model=record.model,
            wire_api=record.wire_api,
            duration_ms=record.duration_ms,
            created_at=record.created_at,
        )
        for record in storage.list_llm_call_logs(limit)
    ]
