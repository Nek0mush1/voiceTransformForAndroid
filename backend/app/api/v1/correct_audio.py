from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.schemas.text_correction import TextCorrectionRequest, TextCorrectionResponse
from app.services.baidu_asr import BaiduAsrClient, BaiduAsrError
from app.services.text_corrector import correct_text


router = APIRouter(tags=["audio-correction"])

MAX_AUDIO_BYTES = 10 * 1024 * 1024


@router.post("/correct-audio", response_model=TextCorrectionResponse)
async def correct_audio_endpoint(
    user_id: str = Form(...),
    app_context: str = Form("unknown"),
    audio: UploadFile = File(...),
) -> TextCorrectionResponse:
    audio_bytes = await audio.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Audio file is empty")
    if len(audio_bytes) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="Audio file is too large")

    audio_format = _guess_audio_format(audio.filename or "", audio.content_type or "")
    try:
        raw_text = BaiduAsrClient().transcribe(audio_bytes, audio_format=audio_format)
    except BaiduAsrError as exception:
        raise HTTPException(status_code=502, detail=str(exception)) from exception
    except Exception as exception:
        raise HTTPException(status_code=502, detail=f"ASR request failed: {exception}") from exception

    if not raw_text:
        raise HTTPException(status_code=422, detail="No speech text recognized")

    return correct_text(
        TextCorrectionRequest(
            user_id=user_id,
            raw_text=raw_text,
            app_context=app_context,
        )
    )


def _guess_audio_format(filename: str, content_type: str) -> str:
    lower_name = filename.lower()
    lower_type = content_type.lower()
    if lower_name.endswith(".wav") or "wav" in lower_type:
        return "wav"
    if lower_name.endswith(".amr") or "amr" in lower_type:
        return "amr"
    if lower_name.endswith(".pcm") or "pcm" in lower_type:
        return "pcm"
    return "m4a"
