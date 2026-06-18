from pydantic import BaseModel, Field


class LLMConfigRequest(BaseModel):
    base_url: str = Field(default="", max_length=500)
    api_key: str = Field(default="", max_length=2000)
    model: str = Field(default="", max_length=200)
    wire_api: str = Field(default="chat_completions", max_length=50)


class LLMConfigResponse(BaseModel):
    base_url: str
    model: str
    wire_api: str
    configured: bool
    api_key_masked: str
    updated_at: str | None = None


class LLMConfigTestResponse(BaseModel):
    success: bool
    message: str
    sample_output: str = ""
