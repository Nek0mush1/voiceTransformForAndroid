from fastapi import APIRouter, Query

from app import storage
from app.schemas.memory import TraceResponse


router = APIRouter(tags=["debug"])


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
