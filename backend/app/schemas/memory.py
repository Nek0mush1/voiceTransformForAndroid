from typing import Any

from pydantic import BaseModel, Field


class ProfileResponse(BaseModel):
    user_id: str
    profile_text: str
    updated_at: str


class ProfileUpdateRequest(BaseModel):
    profile_text: str = Field(..., min_length=1)


class TermCreateRequest(BaseModel):
    user_id: str = Field(default="local_user", min_length=1)
    term: str = Field(..., min_length=1)
    category: str = ""
    aliases: list[str] = Field(default_factory=list)
    weight: float = Field(default=1.0, ge=0.0, le=10.0)


class TermResponse(BaseModel):
    id: int
    user_id: str
    term: str
    category: str
    aliases: list[str]
    weight: float
    created_at: str


class TraceResponse(BaseModel):
    id: str
    user_id: str
    raw_text: str
    corrected_text: str
    profile_summary: str
    matched_terms: list[str]
    pinyin_candidates: list[dict[str, Any]]
    llm_success: bool
    llm_error: str
    tools: list[dict[str, Any]]
    created_at: str
