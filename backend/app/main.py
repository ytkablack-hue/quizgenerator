from fastapi import FastAPI

from app.core.config import settings
from app.api.v1.routes.health import router


def create_app() -> FastAPI:
    """Создаёт экземпляр приложения FastAPI."""

    app = FastAPI(
        title="QuizGenerator API",
        version="1.0.0",
        docs_url="/docs" if settings.app_env == "development" else None,
        redoc_url="/redoc" if settings.app_env == "development" else None,
    )

    app.include_router(router, prefix="/api/v1")

    return app


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        create_app(),
        host=settings.app_host,
        port=settings.app_port,
    )