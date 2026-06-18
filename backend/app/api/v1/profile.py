from fastapi import APIRouter

from app import storage
from app.schemas.memory import ProfileResponse, ProfileUpdateRequest


router = APIRouter(tags=["profile"])


@router.get("/profile/{user_id}", response_model=ProfileResponse)
def get_profile(user_id: str) -> ProfileResponse:
    record = storage.get_profile(user_id)
    return ProfileResponse(
        user_id=record.user_id,
        profile_text=record.profile_text,
        updated_at=record.updated_at,
    )


@router.put("/profile/{user_id}", response_model=ProfileResponse)
def update_profile(user_id: str, payload: ProfileUpdateRequest) -> ProfileResponse:
    record = storage.update_profile(user_id, payload.profile_text)
    return ProfileResponse(
        user_id=record.user_id,
        profile_text=record.profile_text,
        updated_at=record.updated_at,
    )
