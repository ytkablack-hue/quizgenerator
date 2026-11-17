from fastapi import APIRouter


router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check():
    """Базовая проверка работоспособности API."""
    return {"status": "ok"}