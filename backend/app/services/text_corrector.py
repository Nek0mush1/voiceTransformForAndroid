from app.schemas.text_correction import TextCorrectionRequest, TextCorrectionResponse
from app.services.context_agent import ContextCorrectionAgent


def correct_text(payload: TextCorrectionRequest) -> TextCorrectionResponse:
    result = ContextCorrectionAgent().correct(payload)
    return TextCorrectionResponse(
        raw_text=result.raw_text,
        corrected_text=result.corrected_text,
        matched_terms=result.matched_terms,
        reason=result.reason,
        correction_method=result.correction_method,
        llm_used=result.llm_used,
        llm_error=result.llm_error,
        trace_id=result.trace_id,
        agent_trace=result.agent_trace,
    )
