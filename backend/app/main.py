from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import HTMLResponse

from app import storage
from app.api.v1.correct_audio import router as correct_audio_router
from app.api.v1.correct_text import router as correct_text_router
from app.api.v1.debug import router as debug_router
from app.api.v1.llm_config import router as llm_config_router
from app.api.v1.profile import router as profile_router
from app.api.v1.terms import router as terms_router


app = FastAPI(
    title="Voice Transform Backend",
    description="Backend MVP for context-aware text correction.",
    version="0.1.0",
)

app.include_router(correct_audio_router, prefix="/api/v1")
app.include_router(correct_text_router, prefix="/api/v1")
app.include_router(profile_router, prefix="/api/v1")
app.include_router(terms_router, prefix="/api/v1")
app.include_router(llm_config_router, prefix="/api/v1")
app.include_router(debug_router, prefix="/api/v1")


@app.on_event("startup")
def startup() -> None:
    storage.init_db()


@app.get("/", response_class=HTMLResponse)
def demo_page() -> HTMLResponse:
    html_path = Path(__file__).parent / "web" / "index.html"
    return HTMLResponse(html_path.read_text(encoding="utf-8"))


@app.get("/health")
def health_check() -> dict[str, str]:
    return {"status": "ok"}
