import pytest
from fastapi.testclient import TestClient
from app.main import create_app


@pytest.fixture
def client():
    """Фикстура для тестового клиента"""
    app = create_app()
    return TestClient(app)
