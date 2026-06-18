from pydantic import BaseModel, Field


class TextCorrectionRequest(BaseModel):
    user_id: str = Field(..., min_length=1)
    raw_text: str = Field(..., min_length=1)
    app_context: str = Field(default="unknown", min_length=1)


class TextCorrectionResponse(BaseModel):
    raw_text: str
    corrected_text: str
    matched_terms: list[str]
    reason: str
    trace_id: str | None = None
    agent_trace: dict[str, object] | None = None
