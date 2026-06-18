from fastapi import APIRouter, HTTPException, Query, status

from app import storage
from app.schemas.memory import TermCreateRequest, TermResponse


router = APIRouter(tags=["terms"])


def to_term_response(record: storage.TermRecord) -> TermResponse:
    return TermResponse(
        id=record.id,
        user_id=record.user_id,
        term=record.term,
        category=record.category,
        aliases=record.aliases,
        weight=record.weight,
        created_at=record.created_at,
    )


@router.get("/terms", response_model=list[TermResponse])
def list_terms(user_id: str | None = Query(default=None)) -> list[TermResponse]:
    return [to_term_response(record) for record in storage.list_terms(user_id)]


@router.post("/terms", response_model=TermResponse, status_code=status.HTTP_201_CREATED)
def create_term(payload: TermCreateRequest) -> TermResponse:
    record = storage.create_term(
        user_id=payload.user_id,
        term=payload.term,
        category=payload.category,
        aliases=payload.aliases,
        weight=payload.weight,
    )
    return to_term_response(record)


@router.delete("/terms/{term_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_term(term_id: int) -> None:
    if not storage.delete_term(term_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Term not found")
