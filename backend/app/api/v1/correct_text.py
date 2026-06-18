from fastapi import APIRouter

from app.schemas.text_correction import TextCorrectionRequest, TextCorrectionResponse
from app.services.text_corrector import correct_text


router = APIRouter(tags=["text-correction"])


@router.post("/correct-text", response_model=TextCorrectionResponse)
def correct_text_endpoint(payload: TextCorrectionRequest) -> TextCorrectionResponse:
    return correct_text(payload)
