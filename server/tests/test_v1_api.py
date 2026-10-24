from fastapi.testclient import TestClient

from finunity_server.config import Settings
from finunity_server.main import create_app


def client() -> TestClient:
    return TestClient(
        create_app(
            Settings(
                database_path=":memory:",
                ocr_enabled=True,
                openai_api_key="test",
                session_secret="test-secret",
            )
        )
    )


def session(client: TestClient) -> dict[str, str]:
    response = client.post(
        "/api/v1/auth/session",
        json={
            "installation_id": "android-installation-uuid",
            "platform": "ANDROID",
            "app_version": "1.0.0",
            "version_code": 1,
            "device_locale": "zh-CN",
        },
    )
    assert response.status_code == 200
    return response.json()["data"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_access_token_is_required_and_refresh_token_rotates() -> None:
    test_client = client()
    tokens = session(test_client)
    denied = test_client.get("/api/v1/status")
    refreshed = test_client.post(
        "/api/v1/auth/session:refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    replayed = test_client.post(
        "/api/v1/auth/session:refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    assert denied.status_code == 401
    assert denied.json()["error"]["code"] == "UNAUTHORIZED"
    assert refreshed.status_code == 200
    assert refreshed.json()["data"]["refresh_token"] != tokens["refresh_token"]
    assert replayed.status_code == 401
    assert replayed.json()["error"]["code"] == "REFRESH_TOKEN_INVALID"


def test_resolve_preserves_client_ref_and_returns_stable_instrument() -> None:
    test_client = client()
    headers = auth(session(test_client)["access_token"])
    response = test_client.post(
        "/api/v1/instruments/resolve",
        headers=headers,
        json={"queries": [{"client_ref": "asset-1", "text": "600519.SS"}]},
    )
    item = response.json()["data"]["items"][0]
    assert response.status_code == 200
    assert item["client_ref"] == "asset-1"
    assert item["status"] == "RESOLVED"
    assert item["instrument"]["instrument_id"] == "XSHG:600519"


def test_market_sync_is_idempotent_and_keeps_partial_success() -> None:
    test_client = client()
    headers = {
        **auth(session(test_client)["access_token"]),
        "X-Request-ID": "req-contract-test",
    }
    request = {
        "client_request_id": "sync-idempotent-1",
        "base_currency": "CNY",
        "instruments": [
            {"client_ref": "ok", "instrument_id": "XNAS:AAPL", "asset_type": "STOCK"},
            {
                "client_ref": "missing",
                "instrument_id": "XNAS:MISSING",
                "asset_type": "STOCK",
            },
        ],
        "fx_pairs": [{"client_ref": "usd-cny", "base": "USD", "quote": "CNY"}],
    }
    first = test_client.post("/api/v1/market/sync", headers=headers, json=request)
    second = test_client.post("/api/v1/market/sync", headers=headers, json=request)
    assert first.status_code == 200
    assert first.json()["partial"] is True
    assert first.json()["data"] == second.json()["data"]
    assert first.json()["data"]["instruments"][0]["status"] == "OK"
    assert first.json()["data"]["instruments"][1]["status"] == "NOT_FOUND"
    assert first.headers["X-Request-ID"] == "req-contract-test"

    conflicting_request = {**request, "base_currency": "USD"}
    conflict = test_client.post("/api/v1/market/sync", headers=headers, json=conflicting_request)
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "IDEMPOTENCY_CONFLICT"


def test_missing_fx_rate_never_falls_back_to_one() -> None:
    test_client = client()
    headers = auth(session(test_client)["access_token"])
    response = test_client.post(
        "/api/v1/fx/rates/batch",
        headers=headers,
        json={
            "pairs": [{"client_ref": "eur-cny", "base": "EUR", "quote": "CNY"}],
            "rate_type": "MID",
        },
    )
    item = response.json()["data"]["items"][0]
    assert response.status_code == 200
    assert response.json()["partial"] is True
    assert item["rate"] is None
    assert item["error"]["code"] == "FX_RATE_NOT_FOUND"


def test_validation_error_uses_stable_error_envelope() -> None:
    test_client = client()
    headers = auth(session(test_client)["access_token"])
    response = test_client.post(
        "/api/v1/market/quotes/batch", headers=headers, json={"instrument_ids": []}
    )
    payload = response.json()
    assert response.status_code == 400
    assert payload["error"]["code"] == "VALIDATION_ERROR"
    assert payload["error"]["retryable"] is False
    assert payload["request_id"]
    assert payload["server_time"].endswith("Z")


def test_openapi_contains_only_new_health_and_ocr_routes() -> None:
    paths = client().get("/openapi.json").json()["paths"]
    assert "/api/v1/holdings/recognize" not in paths
    assert "/health" not in paths
    assert "/api/v1/ocr/holdings:parse" in paths
    assert "/health/live" in paths
    assert "/health/ready" in paths
