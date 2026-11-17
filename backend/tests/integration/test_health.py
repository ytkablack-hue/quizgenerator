import pytest


@pytest.mark.integration
def test_get_health(client):
    """Проверка GET /api/v1/health."""

    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}