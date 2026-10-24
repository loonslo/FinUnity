import sqlite3
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI
from fastapi.testclient import TestClient

from finunity_server.config import Settings
from finunity_server.main import create_app


def make_app(**overrides) -> FastAPI:
    base = dict(
        database_path=":memory:",
        expose_verification_code=True,
        email_auth_enabled=True,
        ocr_enabled=True,
        session_secret="test-session-secret",
        email_code_pepper="test-email-pepper",
        password_pepper="test-password-pepper",
    )
    base.update(overrides)
    return create_app(Settings(**base))


def issue_code(client: TestClient, email: str, purpose: str) -> str:
    response = client.post(
        "/api/v1/auth/email/code",
        json={"email": email, "purpose": purpose, "locale": "zh-CN"},
    )
    assert response.status_code == 200
    return response.json()["data"]["dev_code"]


def password_login(client: TestClient, email: str, password: str):
    return client.post(
        "/api/v1/auth/password/login",
        json={"email": email, "password": password, "client_request_id": "pw-login-1"},
    )


def test_register_with_password_then_password_login() -> None:
    app = make_app()
    with TestClient(app) as client:
        code = issue_code(client, "pw@example.com", "REGISTER")
        registered = client.post(
            "/api/v1/auth/email/register",
            json={
                "email": "pw@example.com",
                "code": code,
                "display_name": "Password User",
                "client_request_id": "register-pw",
                "password": "s3cure-pass-9",
            },
        )
        assert registered.status_code == 200
        assert "password_hash" not in str(registered.json())

        login = password_login(client, "pw@example.com", "s3cure-pass-9")
        assert login.status_code == 200
        token = login.json()["data"]["access_token"]
        me = client.get("/api/v1/me", headers={"Authorization": f"Bearer {token}"})
        assert me.status_code == 200
        assert me.json()["data"]["email"] == "pw@example.com"
        assert "password_hash" not in str(me.json())


def test_password_login_rejected_when_user_has_no_password() -> None:
    app = make_app()
    with TestClient(app) as client:
        code = issue_code(client, "codeonly@example.com", "REGISTER")
        registered = client.post(
            "/api/v1/auth/email/register",
            json={
                "email": "codeonly@example.com",
                "code": code,
                "client_request_id": "register-1",
            },
        )
        assert registered.status_code == 200

        wrong = password_login(client, "codeonly@example.com", "s3cure-pass-9")
        assert wrong.status_code == 401
        assert wrong.json()["error"]["code"] == "INVALID_CREDENTIALS"


def test_password_login_unknown_email_uses_same_error() -> None:
    app = make_app()
    with TestClient(app) as client:
        response = password_login(client, "ghost@example.com", "s3cure-pass-9")
        assert response.status_code == 401
        assert response.json()["error"]["code"] == "INVALID_CREDENTIALS"


def test_password_login_lockout_after_repeated_failures() -> None:
    app = make_app(password_max_attempts=3, password_lockout_seconds=120)
    with TestClient(app) as client:
        code = issue_code(client, "lock@example.com", "REGISTER")
        client.post(
            "/api/v1/auth/email/register",
            json={
                "email": "lock@example.com",
                "code": code,
                "client_request_id": "register-1",
                "password": "s3cure-pass-9",
            },
        )
        for _ in range(3):
            assert password_login(client, "lock@example.com", "bad-pass-000").status_code == 401
        locked = password_login(client, "lock@example.com", "s3cure-pass-9")
        assert locked.status_code == 429
        assert locked.json()["error"]["code"] == "TOO_MANY_ATTEMPTS"
        assert locked.headers.get("Retry-After") is not None
        assert locked.json()["error"]["retry_after_seconds"] > 0

        # 锁定期内即使密码正确也拒绝
        again = password_login(client, "lock@example.com", "s3cure-pass-9")
        assert again.status_code == 429


def test_password_reset_flow_via_email_code() -> None:
    app = make_app()
    with TestClient(app) as client:
        code = issue_code(client, "reset@example.com", "REGISTER")
        client.post(
            "/api/v1/auth/email/register",
            json={
                "email": "reset@example.com",
                "code": code,
                "client_request_id": "register-1",
                "password": "first-pass-1",
            },
        )
        assert password_login(client, "reset@example.com", "first-pass-1").status_code == 200

        reset_code = issue_code(client, "reset@example.com", "PASSWORD_RESET")
        set_response = client.post(
            "/api/v1/auth/password/set",
            json={
                "email": "reset@example.com",
                "code": reset_code,
                "password": "second-pass-2",
                "client_request_id": "reset-1",
            },
        )
        assert set_response.status_code == 200
        assert set_response.json()["data"]["user"]["email"] == "reset@example.com"

        assert password_login(client, "reset@example.com", "second-pass-2").status_code == 200
        assert password_login(client, "reset@example.com", "first-pass-1").status_code == 401

        # 验证码单次有效，重复使用被拒绝
        replay = client.post(
            "/api/v1/auth/password/set",
            json={
                "email": "reset@example.com",
                "code": reset_code,
                "password": "third-pass-3",
                "client_request_id": "reset-2",
            },
        )
        assert replay.status_code == 400
        assert replay.json()["error"]["code"] == "VERIFICATION_CODE_INVALID"


def test_password_reset_requires_registered_email() -> None:
    app = make_app()
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/auth/email/code",
            json={
                "email": "nobody@example.com",
                "purpose": "PASSWORD_RESET",
                "locale": "zh-CN",
            },
        )
        assert response.status_code == 404


def test_users_table_migration_adds_password_hash_column() -> None:
    db_file = Path("data") / f"legacy-migration-test-{uuid4().hex}.db"
    try:
        legacy = sqlite3.connect(db_file)
        legacy.executescript(
            """
            CREATE TABLE users (
              id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, display_name TEXT,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
            );
            """
        )
        legacy.execute(
            "INSERT INTO users VALUES('usr_legacy','legacy@example.com','Legacy',"
            "'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z',NULL)"
        )
        legacy.commit()
        legacy.close()

        app = make_app(database_path=str(db_file))
        with TestClient(app) as client:
            store = app.state.store
            store.set_user_password("usr_legacy", "migrated-pass-1")
            assert store.verify_user_password("legacy@example.com", "migrated-pass-1")
            assert not store.verify_user_password("legacy@example.com", "not-the-password")

            # 迁移后注册与密码登录照常工作
            code = issue_code(client, "new@example.com", "REGISTER")
            registered = client.post(
                "/api/v1/auth/email/register",
                json={
                    "email": "new@example.com",
                    "code": code,
                    "client_request_id": "register-1",
                    "password": "s3cure-pass-9",
                },
            )
            assert registered.status_code == 200
            assert password_login(client, "new@example.com", "s3cure-pass-9").status_code == 200
    finally:
        for suffix in ("", "-wal", "-shm"):
            path = Path(f"{db_file}{suffix}")
            if path.exists():
                path.unlink()
