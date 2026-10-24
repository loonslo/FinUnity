import base64
import io
import json
import sqlite3
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from pydantic import ValidationError

from finunity_server.cache import TTLCache
from finunity_server.config import Settings
from finunity_server.main import create_app
from finunity_server.models import ModelRecognitionResult, RecognizedHolding
from finunity_server.store import SQLiteStore

PASSWORD = "secure-test-pass-9"


def make_app(**overrides):
    return create_app(Settings(database_path=":memory:", **overrides))


def register(client, username="tester", password=PASSWORD):
    return client.post(
        "/api/v1/auth/register",
        json={
            "username": username,
            "password": password,
            "password_confirmation": password,
            "accept_privacy": True,
            "accept_terms": True,
        },
    )


def headers(auth):
    return {"Authorization": f"Bearer {auth['access_token']}"}


def installation(client, installation_id="installation-test-1"):
    return client.post(
        "/api/v1/auth/session",
        json={
            "installation_id": installation_id,
            "platform": "ANDROID",
            "app_version": "1.0.0",
            "version_code": 1,
        },
    ).json()["data"]


def login(client, username="tester", password=PASSWORD):
    return client.post(
        "/api/v1/auth/password/login",
        json={
            "username": username,
            "password": password,
            "client_request_id": "test-login",
        },
    )


class FakeRecognizer:
    calls = 0

    async def recognize(self, **kwargs):
        self.calls += 1
        return ModelRecognitionResult(
            holdings=[
                RecognizedHolding(
                    name="Apple",
                    security_code="AAPL",
                    asset_type="STOCK",
                    quantity=2,
                    current_price=10,
                    market_value=20,
                    currency="USD",
                    confidence=0.9,
                    cost_source="NOT_SHOWN",
                    needs_review=True,
                )
            ]
        )


def parse(client, auth, request_id="ocr-one", color="white", content=None):
    image = io.BytesIO()
    Image.new("RGB", (600, 600), color).save(image, format="PNG")
    return client.post(
        "/api/v1/ocr/holdings:parse",
        headers=headers(auth),
        files={"image": ("holding.png", content or image.getvalue(), "image/png")},
        data={"client_request_id": request_id},
    )


def test_invalid_tokens_and_typed_installation_cannot_impersonate_user():
    app = make_app()
    with TestClient(app) as client:
        auth = register(client).json()["data"]
        encoded, signature = auth["access_token"].split(".")
        claims = app.state.sessions.claims(auth["access_token"])
        expired = app.state.sessions._sign({**claims, "exp": int(time.time()) - 1})
        altered = base64.urlsafe_b64encode(json.dumps({**claims, "sub": "usr_fake"}).encode())
        for token in (
            encoded + ".AAAA",
            altered.decode().rstrip("=") + "." + signature,
            expired,
            "malformed",
            "!!.@@",
            app.state.sessions._sign(["bad"]),
        ):
            for path in ("/api/v1/app/config?platform=ANDROID&version_code=1", "/api/v1/me"):
                assert (
                    client.get(path, headers={"Authorization": f"Bearer {token}"}).status_code
                    == 401
                )
        anonymous = installation(client, auth["user"]["user_id"])
        assert app.state.sessions.claims(anonymous["access_token"])["type"] == "INSTALLATION"
        for path in ("/api/v1/me", "/api/v1/holdings", "/api/v1/transactions"):
            assert client.get(path, headers=headers(anonymous)).status_code == 401
        assert (
            client.get("/api/v1/instruments/search?q=AAPL", headers=headers(anonymous)).status_code
            == 200
        )
        refreshed = client.post(
            "/api/v1/auth/session:refresh", json={"refresh_token": anonymous["refresh_token"]}
        ).json()["data"]
        assert client.get("/api/v1/me", headers=headers(refreshed)).status_code == 401


@pytest.mark.parametrize(
    "changes",
    [
        {"session_secret": "development-only-change-me"},
        {"session_secret": "short"},
        {"password_pepper": "development-only-change-me"},
        {"password_pepper": "short"},
        {"privacy_policy_url": "https://example.com/privacy"},
        {"email_auth_enabled": True},
        {
            "email_auth_enabled": True,
            "smtp_host": "smtp.test",
            "smtp_from_email": "test@example.com",
        },
        {
            "email_auth_enabled": True,
            "smtp_host": "smtp.test",
            "smtp_from_email": "test@example.com",
            "email_code_pepper": "e" * 40,
            "expose_verification_code": True,
        },
    ],
)
def test_production_rejects_insecure_settings(changes):
    values = {"app_env": "production", "session_secret": "s" * 40, "password_pepper": "p" * 40}
    with pytest.raises(ValidationError, match="Production configuration rejected"):
        Settings(**{**values, **changes})


def test_production_disables_docs_and_email_routes_even_with_invalid_payload():
    with TestClient(
        make_app(app_env="production", session_secret="s" * 40, password_pepper="p" * 40)
    ) as client:
        for path in ("/docs", "/redoc", "/openapi.json"):
            assert client.get(path).status_code == 404
        for path in (
            "/auth/email/code",
            "/auth/email/register",
            "/auth/login",
            "/auth/password/set",
        ):
            assert client.post("/api/v1" + path, json={}).status_code == 404


@pytest.mark.parametrize("username", ["ab", "a" * 33, "中文用户", "a.b", "ADMIN", "root"])
def test_username_rules(username):
    with TestClient(make_app()) as client:
        assert register(client, username).status_code == 400


def test_confirmation_consent_case_insensitive_uniqueness_and_hash_only_recovery():
    app = make_app()
    with TestClient(app) as client:
        body = {
            "username": "abcdefgh",
            "password": PASSWORD,
            "password_confirmation": "different-pass",
            "accept_privacy": True,
            "accept_terms": True,
        }
        assert client.post("/api/v1/auth/register", json=body).status_code == 400
        assert (
            client.post(
                "/api/v1/auth/register",
                json={**body, "password_confirmation": PASSWORD, "accept_terms": False},
            ).status_code
            == 400
        )
        assert register(client, "abcdefgh", "abcdefgh").status_code == 400
        auth = register(client, "TeStEr").json()["data"]
        assert register(client, "TESTER").status_code == 409
        assert login(client, "TESTER").status_code == 200
        assert auth["user"]["email"] is None
        assert auth["recovery_code"] not in str(app.state.store.user(auth["user"]["user_id"]))
        assert "recovery_code" not in client.get("/api/v1/me", headers=headers(auth)).text


def test_recovery_is_single_use_and_revokes_all_old_access_and_refresh():
    app = make_app()
    with TestClient(app) as client:
        old = register(client).json()["data"]
        other = login(client).json()["data"]
        body = {
            "username": "tester",
            "recovery_code": old["recovery_code"],
            "password": "new-password-123",
            "password_confirmation": "new-password-123",
        }
        new = client.post("/api/v1/auth/recovery/reset", json=body).json()["data"]
        assert new["recovery_code"] != old["recovery_code"]
        for auth in (old, other):
            assert client.get("/api/v1/me", headers=headers(auth)).status_code == 401
            assert (
                client.post(
                    "/api/v1/auth/session:refresh", json={"refresh_token": auth["refresh_token"]}
                ).status_code
                == 401
            )
        assert client.post("/api/v1/auth/recovery/reset", json=body).status_code == 401
        assert login(client, password="new-password-123").status_code == 200
        changed = client.post(
            "/api/v1/me/password/change",
            headers=headers(new),
            json={
                "old_password": "new-password-123",
                "password": PASSWORD,
                "password_confirmation": PASSWORD,
            },
        )
        assert changed.status_code == 200
        assert client.get("/api/v1/me", headers=headers(new)).status_code == 401
        active = changed.json()["data"]
        assert client.get("/api/v1/me", headers=headers(active)).status_code == 200
        regenerated = client.post(
            "/api/v1/me/recovery/regenerate", headers=headers(active), json={"password": PASSWORD}
        )
        assert regenerated.status_code == 200
        assert regenerated.json()["data"]["recovery_code"] != new["recovery_code"]


def test_refresh_cannot_resurrect_a_session_during_password_reset(monkeypatch):
    app = make_app()
    with TestClient(app) as client:
        auth = register(client).json()["data"]
        manager, db = app.state.sessions, app.state.store
        original = manager._valid_identity

        def reset_after_validation(subject, session_type, version):
            valid = original(subject, session_type, version)
            if valid and session_type == "USER":
                db.replace_credentials(db.user(subject), password="replacement-pass")
            return valid

        monkeypatch.setattr(manager, "_valid_identity", reset_after_validation)
        refreshed = client.post(
            "/api/v1/auth/session:refresh", json={"refresh_token": auth["refresh_token"]}
        )
        assert refreshed.status_code == 401
        with db.connection() as conn:
            assert (
                conn.execute(
                    "SELECT count(*) FROM refresh_sessions WHERE subject=?",
                    (auth["user"]["user_id"],),
                ).fetchone()[0]
                == 0
            )


@pytest.mark.parametrize("operation", ["login", "delete"])
def test_verified_old_password_cannot_authorize_after_concurrent_reset(monkeypatch, operation):
    app = make_app()
    with TestClient(app) as client:
        auth = register(client).json()["data"]
        db = app.state.store
        original = db.verify_password

        def reset_after_verification(user, password):
            valid = original(user, password)
            if valid:
                db.replace_credentials(user, password="replacement-pass")
            return valid

        monkeypatch.setattr(db, "verify_password", reset_after_verification)
        if operation == "login":
            assert login(client).status_code == 401
        else:
            response = client.post(
                "/api/v1/me:delete", headers=headers(auth), json={"password": PASSWORD}
            )
            assert response.status_code == 409
        assert db.user(auth["user"]["user_id"])


def test_auth_lockout_ip_rate_registration_and_retry_headers(monkeypatch, caplog):
    # Both requests must stay in one rate-limit window, even across a wall-clock minute.
    fixed_time = time.time()
    monkeypatch.setattr("finunity_server.store.time.time", lambda: fixed_time)
    app = make_app(registration_hourly_limit=1, registration_alert_threshold=1)
    with TestClient(app) as client:
        assert register(client).status_code == 200
        assert "REGISTRATION_DAILY_ALERT" in caplog.text
        denied = register(client, "other")
        assert denied.status_code == 429 and int(denied.headers["Retry-After"]) > 0
        for _ in range(5):
            assert login(client, password="incorrect-pass").status_code == 401
        assert login(client).status_code == 429
    with TestClient(make_app(auth_ip_minute_limit=1)) as client:
        assert login(client, "unknown").status_code == 401
        assert login(client, "other").status_code == 429


def test_limits_are_persistent_atomic_and_expire(tmp_path, monkeypatch):
    settings = Settings(database_path=str(tmp_path / "quota.db"))
    db = SQLiteStore(settings)
    monkeypatch.setattr("finunity_server.store.time.time", lambda: 1000)
    with ThreadPoolExecutor(max_workers=4) as executor:
        values = list(
            executor.map(lambda _: db.consume_limits([("test", "same", 3, 86400)]), range(8))
        )
    assert values.count(None) == 3
    assert SQLiteStore(settings).consume_limits([("test", "same", 3, 86400)]) is not None
    monkeypatch.setattr("finunity_server.store.time.time", lambda: 86401)
    assert db.consume_limits([("test", "same", 3, 86400)]) is None


def test_lockout_expires_and_ip_failures_block_other_usernames(monkeypatch):
    app = make_app()
    with TestClient(app) as client:
        register(client)
        for _ in range(5):
            assert login(client, password="incorrect-pass").status_code == 401
        assert login(client).status_code == 429

        class FutureDateTime(datetime):
            @classmethod
            def now(cls, tz=None):
                return datetime.now(tz) + timedelta(seconds=901)

        monkeypatch.setattr("finunity_server.store.datetime", FutureDateTime)
        assert login(client).status_code == 200
        monkeypatch.undo()
    with TestClient(make_app(auth_ip_failure_limit=2)) as client:
        assert login(client, "unknown_a").status_code == 401
        assert login(client, "unknown_b").status_code == 401
        assert login(client, "unknown_c").status_code == 429


def test_daily_registration_limit_is_independent_of_hourly_limit():
    with TestClient(make_app(registration_hourly_limit=100, registration_daily_limit=1)) as client:
        assert register(client).status_code == 200
        assert register(client, "another").status_code == 429


@pytest.mark.parametrize(
    "trusted,expected", [("172.17.0.1", "203.0.113.9"), ("127.0.0.1", "172.17.0.1")]
)
def test_forwarded_ip_only_accepted_from_trusted_proxy(trusted, expected):
    from uvicorn.middleware.proxy_headers import ProxyHeadersMiddleware

    app = make_app()
    proxy = ProxyHeadersMiddleware(app, trusted_hosts=trusted)
    with TestClient(proxy, client=("172.17.0.1", 50000)) as client:
        client.post(
            "/api/v1/auth/password/login",
            headers={"X-Forwarded-For": "203.0.113.9"},
            json={"username": "unknown", "password": PASSWORD, "client_request_id": "proxy-test"},
        )
    with app.state.store.connection() as db:
        row = db.execute("SELECT identity FROM usage_counters WHERE scope='auth-cpu'").fetchone()
        assert row["identity"] == expected


def test_ocr_upload_remains_in_memory_and_oversize_is_rejected(monkeypatch):
    import tempfile

    def disk_rollover_forbidden(self):
        raise AssertionError("OCR original must not spill to disk")

    monkeypatch.setattr(tempfile.SpooledTemporaryFile, "rollover", disk_rollover_forbidden)
    app = make_app(ocr_enabled=True, max_image_bytes=2 * 1024 * 1024)
    app.state.recognizer = FakeRecognizer()
    # Valid incompressible PNG exceeds Starlette's usual 1 MB spool threshold.
    import os

    data = io.BytesIO()
    Image.frombytes("RGB", (650, 650), os.urandom(650 * 650 * 3)).save(data, format="PNG")
    assert len(data.getvalue()) > 1024 * 1024
    with TestClient(app) as client:
        auth = installation(client)
        assert parse(client, auth, content=data.getvalue()).status_code == 200
        assert parse(client, auth, content=b"x" * (3 * 1024 * 1024)).status_code == 413


def test_backup_restore_preserves_database_and_refuses_overwrite(tmp_path):
    import importlib.util
    from pathlib import Path

    spec = importlib.util.spec_from_file_location(
        "database_ops", Path(__file__).resolve().parents[1] / "deploy" / "database.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    path = tmp_path / "live.db"
    db = SQLiteStore(Settings(database_path=str(path)))
    user, _ = db.create_username_user("backup_user", PASSWORD, None)
    saved = tmp_path / "backup.db"
    module.backup(path, saved)
    with pytest.raises(ValueError, match="new, distinct"):
        module.backup(path, saved)
    restored = tmp_path / "restored.db"
    module.backup(saved, restored)
    restored_db = SQLiteStore(Settings(database_path=str(restored)))
    assert restored_db.user(user["id"])["username"] == "backup_user"
    assert restored_db.verify_password(restored_db.user(user["id"]), PASSWORD)
    with restored_db.connection() as conn:
        assert conn.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert conn.execute("PRAGMA foreign_key_check").fetchall() == []


def test_ocr_user_cache_isolation_anonymous_quotas_no_persistence_and_switch(tmp_path):
    settings = Settings(
        database_path=str(tmp_path / "data.db"),
        ocr_enabled=True,
        ocr_user_daily_limit=1,
        ocr_installation_daily_limit=1,
        ocr_ip_daily_limit=2,
        ocr_anonymous_global_daily_limit=2,
    )
    app = create_app(settings)
    fake = FakeRecognizer()
    app.state.recognizer = fake
    with TestClient(app) as client:
        a, b = register(client, "user_a").json()["data"], register(client, "user_b").json()["data"]
        first = parse(client, a).json()["data"]
        assert parse(client, a).json()["data"] == first
        assert parse(client, a, "new-id").status_code == 429
        second = parse(client, b).json()["data"]
        assert first["document_id"] != second["document_id"]
        assert fake.calls == 2
        assert (
            client.post(
                f"/api/v1/ocr/documents/{first['document_id']}:archive", headers=headers(b)
            ).status_code
            == 404
        )
        anon = installation(client)
        anonymous_result = parse(client, anon).json()["data"]
        assert parse(client, anon).json()["data"] == anonymous_result
        assert parse(client, anon, "anon-2").status_code == 429
        assert parse(client, installation(client, "another-installation")).status_code == 200
        assert parse(client, installation(client, "third-installation")).status_code == 429
        with app.state.store.connection() as db:
            assert db.execute("SELECT count(*) FROM ocr_documents").fetchone()[0] == 2
        assert list(tmp_path.glob("*.png")) == []
        app.state.settings.ocr_enabled = False
        denied = parse(client, a)
        assert denied.status_code == 503 and denied.json()["error"]["code"] == "OCR_DISABLED"
    restarted = create_app(settings)
    restarted.state.settings.ocr_enabled = True
    with TestClient(restarted) as client:
        assert parse(client, a).json()["data"] == first


@pytest.mark.parametrize(
    "setting",
    ["ocr_user_global_daily_limit", "ocr_ip_daily_limit", "ocr_anonymous_global_daily_limit"],
)
def test_each_ocr_global_or_ip_quota(setting):
    app = make_app(ocr_enabled=True, **{setting: 1})
    app.state.recognizer = FakeRecognizer()
    with TestClient(app) as client:
        if setting == "ocr_user_global_daily_limit":
            a = register(client, "user_a").json()["data"]
            b = register(client, "user_b").json()["data"]
        else:
            a, b = installation(client), installation(client, "another-installation")
        assert parse(client, a).status_code == 200
        assert parse(client, b).status_code == 429


def test_cache_capacity_and_expiry(monkeypatch):
    monkeypatch.setattr("finunity_server.cache.time.monotonic", lambda: 100)
    cache = TTLCache(2, 5)
    cache["a"], cache["b"] = 1, 2
    assert cache.get("a") == 1
    cache["c"] = 3
    assert cache.get("b") is None
    monkeypatch.setattr("finunity_server.cache.time.monotonic", lambda: 106)
    assert cache.get("a") is None and cache.get("c") is None


def test_migration_preserves_users_children_and_invalidates_legacy_refresh(tmp_path):
    path = tmp_path / "legacy.db"
    with sqlite3.connect(path) as db:
        db.executescript("""CREATE TABLE users(id TEXT PRIMARY KEY,email TEXT NOT NULL UNIQUE,
            display_name TEXT,password_hash TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,
            archived_at TEXT);
            INSERT INTO users VALUES(
              'usr_legacy','legacy@example.com','旧用户',NULL,'old','old',NULL);
            CREATE TABLE accounts (
                id TEXT PRIMARY KEY,user_id TEXT NOT NULL,name TEXT NOT NULL,
                institution TEXT,account_type TEXT NOT NULL,currency TEXT NOT NULL,
                opening_balance TEXT NOT NULL DEFAULT '0',version INTEGER NOT NULL,
                created_at TEXT NOT NULL,updated_at TEXT NOT NULL,archived_at TEXT,
                FOREIGN KEY(user_id) REFERENCES users(id));
            INSERT INTO accounts VALUES(
                'acc_legacy','usr_legacy','原有账户',NULL,'BANK','CNY','100',1,'old','old',NULL);
            CREATE TABLE refresh_sessions(token_hash TEXT PRIMARY KEY,subject TEXT NOT NULL,
                expires_at INTEGER NOT NULL,created_at TEXT NOT NULL);
            INSERT INTO refresh_sessions VALUES('old','usr_legacy',9999999999,'old');""")
    store = SQLiteStore(Settings(database_path=str(path)))
    assert store.user("usr_legacy")["display_name"] == "旧用户"
    assert list(tmp_path.glob("*.pre-public-v3-*"))
    with store.connection() as db:
        assert db.execute("SELECT count(*) FROM refresh_sessions").fetchone()[0] == 0
        account = db.execute("SELECT * FROM accounts WHERE id='acc_legacy'").fetchone()
        assert account["user_id"] == "usr_legacy" and account["opening_balance"] == "100"
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
    store.set_user_password("usr_legacy", PASSWORD)
    code = store.set_username("usr_legacy", "legacy-user", PASSWORD)
    assert code and store.user_by_username("LEGACY-USER")["id"] == "usr_legacy"


def test_cross_user_isolation_export_and_deletion():
    app = make_app(ocr_enabled=True)
    app.state.recognizer = FakeRecognizer()
    with TestClient(app) as client:
        a, b = register(client, "user_a").json()["data"], register(client, "user_b").json()["data"]
        ha, hb = headers(a), headers(b)
        account_body = {"name": "Private account", "account_type": "BANK", "currency": "CNY"}
        account = client.post("/api/v1/accounts", headers=ha, json=account_body).json()["data"]
        source = client.post("/api/v1/sources", headers=ha, json={"name": "Private source"}).json()[
            "data"
        ]
        holding_body = {
            "name": "Private holding",
            "asset_type": "STOCK",
            "quantity": "1",
            "currency": "CNY",
        }
        holding = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=ha,
            json={"client_request_id": "h-one", "items": [holding_body]},
        ).json()["data"]["items"][0]
        tx_body = {
            "transaction_type": "DEPOSIT",
            "amount": "10",
            "currency": "CNY",
            "occurred_at": "2026-09-29",
        }
        tx = client.post(
            "/api/v1/transactions:batch-upsert",
            headers=ha,
            json={"client_request_id": "t-one", "items": [tx_body]},
        ).json()["data"]["items"][0]
        doc = parse(client, a).json()["data"]
        batch = client.post(
            "/api/v1/holding-imports",
            headers=ha,
            json={"client_request_id": "i-one", "document_ids": [doc["document_id"]], "rows": []},
        ).json()["data"]
        skipped_row = {"document_id": doc["document_id"], "row_id": "row-1", "action": "SKIP"}
        ready = client.post(
            f"/api/v1/holding-imports/{batch['batch_id']}:preview",
            headers=ha,
            json={"version": batch["version"], "rows": [skipped_row]},
        )
        assert ready.status_code == 200
        committed = client.post(
            f"/api/v1/holding-imports/{batch['batch_id']}:commit",
            headers=ha,
            json={
                "client_request_id": "i-confirm",
                "version": ready.json()["data"]["version"],
                "rows": [skipped_row],
            },
        )
        assert committed.status_code == 200
        with app.state.store.connection() as db:
            assert db.execute(
                "SELECT count(*) FROM import_references WHERE user_id=?",
                (a["user"]["user_id"],),
            ).fetchone()[0] == 1
        for collection in ("accounts", "sources", "holdings", "transactions", "ocr/documents"):
            assert client.get("/api/v1/" + collection, headers=hb).json()["data"]["items"] == []
        summary = client.get("/api/v1/ledger/summary", headers=hb)
        assert summary.status_code == 200
        assert all(items == [] for items in summary.json()["data"]["records"].values())
        for table, identifier, payload in (
            ("accounts", "account_id", account),
            ("holdings", "holding_id", holding),
            ("transactions", "transaction_id", tx),
        ):
            assert (
                client.post(
                    f"/api/v1/{table}/{payload[identifier]}:archive",
                    headers=hb,
                    json={"version": 1},
                ).status_code
                == 404
            )
        assert (
            client.post(
                "/api/v1/accounts",
                headers=hb,
                json={**account_body, "account_id": account["account_id"], "version": 1},
            ).status_code
            == 404
        )
        assert (
            client.post(
                "/api/v1/sources",
                headers=hb,
                json={"source_id": source["source_id"], "name": "attack", "version": 1},
            ).status_code
            == 404
        )
        assert (
            client.post(
                "/api/v1/holdings:batch-upsert",
                headers=hb,
                json={
                    "client_request_id": "attack-h",
                    "items": [{**holding_body, "holding_id": holding["holding_id"], "version": 1}],
                },
            ).status_code
            == 404
        )
        assert (
            client.post(
                "/api/v1/transactions:batch-upsert",
                headers=hb,
                json={
                    "client_request_id": "attack-t",
                    "items": [{**tx_body, "transaction_id": tx["transaction_id"], "version": 1}],
                },
            ).status_code
            == 404
        )
        assert (
            client.get(f"/api/v1/holding-imports/{batch['batch_id']}", headers=hb).status_code
            == 404
        )
        assert (
            client.post(
                "/api/v1/holding-imports",
                headers=hb,
                json={
                    "client_request_id": "attack-doc",
                    "document_ids": [doc["document_id"]],
                    "rows": [],
                },
            ).status_code
            == 404
        )
        assert (
            client.post(
                f"/api/v1/holding-imports/{batch['batch_id']}:commit",
                headers=hb,
                json={
                    "client_request_id": "attack-commit",
                    "version": 1,
                    "rows": [{"row_id": "row-1", "action": "SKIP"}],
                },
            ).status_code
            == 404
        )
        exported = client.get("/api/v1/me/export", headers=ha).json()["data"]
        assert (
            len(exported["accounts"])
            == len(exported["holdings"])
            == len(exported["transactions"])
            == 1
        )
        assert client.get("/api/v1/me/export", headers=hb).json()["data"]["holdings"] == []
        assert client.post(
            "/api/v1/me/preferences",
            headers=ha,
            json={"language": "zh-CN", "base_currency": "CNY", "version": 0},
        ).status_code == 200
        assert client.post(
            f"/api/v1/ocr/documents/{doc['document_id']}:archive",
            headers=ha,
            json={"version": 1},
        ).status_code == 200
        assert (
            client.post(
                "/api/v1/me:delete", headers=ha, json={"password": "wrong-password"}
            ).status_code
            == 401
        )
        assert (
            client.post("/api/v1/me:delete", headers=ha, json={"password": PASSWORD}).status_code
            == 200
        )
        assert client.get("/api/v1/me", headers=ha).status_code == 401
        assert (
            client.post(
                "/api/v1/auth/session:refresh", json={"refresh_token": a["refresh_token"]}
            ).status_code
            == 401
        )
        with app.state.store.connection() as db:
            for table in (
                "accounts",
                "holdings",
                "transactions",
                "sources",
                "holding_imports",
                "import_references",
                "ocr_documents",
                "user_settings",
                "idempotency",
            ):
                assert (
                    db.execute(
                        f"SELECT count(*) FROM {table} WHERE user_id=?", (a["user"]["user_id"],)
                    ).fetchone()[0]
                    == 0
                )
        assert register(client, "user_a").status_code == 200
