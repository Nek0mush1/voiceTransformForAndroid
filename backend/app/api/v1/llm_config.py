from fastapi import APIRouter

from app import storage
from app.schemas.llm_config import LLMConfigRequest, LLMConfigResponse, LLMConfigTestResponse
from app.services.llm_rewrite import LLMRewriteTool


router = APIRouter(tags=["llm-config"])


@router.get("/llm-config", response_model=LLMConfigResponse)
def get_llm_config() -> LLMConfigResponse:
    return to_response(storage.get_llm_config())


@router.put("/llm-config", response_model=LLMConfigResponse)
def update_llm_config(payload: LLMConfigRequest) -> LLMConfigResponse:
    api_key = payload.api_key if payload.api_key.strip() else None
    return to_response(
        storage.update_llm_config(
            base_url=payload.base_url,
            api_key=api_key,
            model=payload.model,
        )
    )


@router.post("/llm-config/test", response_model=LLMConfigTestResponse)
def test_llm_config() -> LLMConfigTestResponse:
    config = storage.get_llm_config()
    if not config.base_url or not config.api_key or not config.model:
        return LLMConfigTestResponse(success=False, message="LLM not configured")

    result = LLMRewriteTool().call_chat_completion(
        base_url=config.base_url,
        api_key=config.api_key,
        model=config.model,
        messages=[
            {
                "role": "system",
                "content": "Return a short plain text health check response.",
            },
            {
                "role": "user",
                "content": "Say: LLM config works",
            },
        ],
        timeout=15,
    )
    if result.success:
        return LLMConfigTestResponse(
            success=True,
            message="LLM config works",
            sample_output=result.text,
        )
    return LLMConfigTestResponse(success=False, message=result.error or "LLM request failed")


def to_response(record: storage.LLMConfigRecord) -> LLMConfigResponse:
    return LLMConfigResponse(
        base_url=record.base_url,
        model=record.model,
        configured=bool(record.base_url and record.api_key and record.model),
        api_key_masked=mask_api_key(record.api_key),
        updated_at=record.updated_at,
    )


def mask_api_key(api_key: str) -> str:
    if not api_key:
        return ""
    if len(api_key) <= 8:
        return "****"
    return f"{api_key[:4]}...{api_key[-4:]}"
