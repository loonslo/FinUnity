"""Health endpoints: live is a pulse, ready proves the database works, version names the build."""

import inspect
import sqlite3

from fastapi.testclient import TestClient

from finunity_server import v1_api
from finunity_server.config import Settings
from finunity_server.main import create_app


def make_app(**overrides):
    return create_app(Settings(database_path=":memory:", **overrides))


def test_live_is_a_constant_pulse():
    with TestClient(make_app()) as client:
        assert client.get("/health/live").json() == {"status": "ok"}


def test_ready_reports_a_working_database():
    with TestClient(make_app()) as client:
        response = client.get("/health/ready")
        assert response.status_code == 200
        assert response.json() == {
            "status": "ready",
            "checks": {"application": "ok", "database": "ok"},
        }


def test_ready_is_503_when_the_database_cannot_be_used(monkeypatch):
    app = make_app()
    with TestClient(app) as client:

        def broken():
            raise sqlite3.OperationalError("disk I/O error: /secret/path/finunity.db")

        monkeypatch.setattr(app.state.store, "connection", broken)
        response = client.get("/health/ready")
        assert response.status_code == 503
        body = response.json()
        assert body["status"] == "not_ready" and body["checks"]["database"] == "error"
        assert "secret" not in response.text  # driver messages and paths are not exposed


def test_ready_is_degraded_not_down_when_ocr_credentials_are_missing():
    app = make_app(ocr_enabled=True, ocr_provider="qwen", qwen_api_key=None)
    with TestClient(app) as client:
        response = client.get("/health/ready")
        assert response.status_code == 200
        assert response.json()["status"] == "degraded"
        assert response.json()["checks"]["ocr"] == "not_configured"


def test_ready_never_calls_the_paid_ocr_provider():
    class Forbidden:
        async def recognize(self, **kwargs):
            raise AssertionError("the readiness probe must not spend OCR budget")

    app = make_app(ocr_enabled=True, ocr_provider="qwen", qwen_api_key="configured-key")
    app.state.recognizer = Forbidden()
    with TestClient(app) as client:
        body = client.get("/health/ready").json()
        assert body["status"] == "ready" and body["checks"]["ocr"] == "configured"


def test_version_reports_the_injected_build(monkeypatch):
    monkeypatch.setenv("GIT_SHA", "abc1234")
    monkeypatch.setenv("BUILD_TIME", "2026-10-05T00:00:00Z")
    app = make_app()
    with TestClient(app) as client:
        assert client.get("/health/version").json() == {
            "service": "finunity-server",
            "version": app.version,
            "git_sha": "abc1234",
            "build_time": "2026-10-05T00:00:00Z",
        }


def test_version_without_build_info_says_unknown(monkeypatch):
    monkeypatch.delenv("GIT_SHA", raising=False)
    monkeypatch.delenv("BUILD_TIME", raising=False)
    with TestClient(make_app()) as client:
        body = client.get("/health/version").json()
        assert body["git_sha"] == "unknown" and body["build_time"] == "unknown"


def test_database_probe_does_not_run_on_the_event_loop():
    assert not inspect.iscoroutinefunction(v1_api.ready)
