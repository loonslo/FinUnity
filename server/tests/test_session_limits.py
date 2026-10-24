"""Anonymous session endpoints: throttled, bounded, indexed, and off the event loop.

POST /api/v1/auth/session needs no credentials, so before these limits anyone could write one
30-day refresh row per request. With no index on refresh_sessions.expires_at every call also
scanned the whole table (10 ms at 0 rows, 158 ms at 600k rows on a development machine).
"""

import inspect

from fastapi.testclient import TestClient

from finunity_server import v1_api
from finunity_server.config import Settings
from finunity_server.main import create_app
from finunity_server.store import MAX_REFRESH_SESSIONS, SQLiteStore


def make_app(**overrides):
    return create_app(Settings(database_path=":memory:", **overrides))


def open_session(client, installation_id="installation-test-1"):
    return client.post(
        "/api/v1/auth/session",
        json={
            "installation_id": installation_id,
            "platform": "ANDROID",
            "app_version": "1.0.0",
            "version_code": 1,
        },
    )


def refresh(client, token="x" * 40):
    return client.post("/api/v1/auth/session:refresh", json={"refresh_token": token})


def row_count(app, table="refresh_sessions") -> int:
    with app.state.store.connection() as db:
        return db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]


def assert_rate_limited(response):
    assert response.status_code == 429
    body = response.json()["error"]
    assert body["code"] == "RATE_LIMITED" and body["retryable"] is True
    assert int(response.headers["Retry-After"]) > 0


def test_session_creation_is_limited_per_ip_and_rejections_write_nothing():
    app = make_app(session_ip_hourly_limit=3)
    with TestClient(app) as client:
        for index in range(3):
            assert open_session(client, f"installation-number-{index}").status_code == 200
        assert_rate_limited(open_session(client, "installation-number-3"))
        assert row_count(app) == 3  # the rejected request never reached the table


def test_session_creation_is_limited_per_installation():
    app = make_app(session_ip_hourly_limit=100, session_installation_hourly_limit=2)
    with TestClient(app) as client:
        assert open_session(client, "same-installation").status_code == 200
        assert open_session(client, "same-installation").status_code == 200
        assert_rate_limited(open_session(client, "same-installation"))
        assert open_session(client, "another-installation").status_code == 200


def test_session_creation_has_a_global_circuit_breaker():
    app = make_app(session_ip_hourly_limit=100, session_global_daily_limit=2)
    with TestClient(app) as client:
        assert open_session(client, "installation-number-0").status_code == 200
        assert open_session(client, "installation-number-1").status_code == 200
        assert_rate_limited(open_session(client, "installation-number-2"))


def test_refresh_attempts_are_limited_per_ip():
    app = make_app(session_refresh_ip_hourly_limit=2)
    with TestClient(app) as client:
        assert refresh(client).status_code == 401
        assert refresh(client).status_code == 401
        assert_rate_limited(refresh(client))


def test_installation_keeps_only_its_newest_refresh_sessions():
    app = make_app()
    keep = MAX_REFRESH_SESSIONS["INSTALLATION"]
    with TestClient(app):
        issued = [app.state.sessions.issue("ins_abc", "INSTALLATION") for _ in range(keep + 2)]
        assert row_count(app) == keep
        assert app.state.sessions.rotate(issued[0]["refresh_token"]) is None  # pruned
        assert app.state.sessions.rotate(issued[-1]["refresh_token"]) is not None


def test_user_sessions_are_capped_per_user_without_touching_other_users():
    app = make_app()
    keep = MAX_REFRESH_SESSIONS["USER"]
    with TestClient(app):
        store = app.state.store
        first, _ = store.create_username_user("session_user_one", "secure-test-pass-9", None)
        second, _ = store.create_username_user("session_user_two", "secure-test-pass-9", None)
        for _ in range(keep + 5):
            app.state.sessions.issue(first["id"])
        app.state.sessions.issue(second["id"])
        with store.connection() as db:
            counts = dict(
                db.execute("SELECT subject, COUNT(*) FROM refresh_sessions GROUP BY subject")
            )
        assert counts == {first["id"]: keep, second["id"]: 1}


def test_expiry_purges_use_indexes_instead_of_full_scans():
    app = make_app()
    with app.state.store.connection() as db:
        for table, index in (
            ("refresh_sessions", "idx_refresh_sessions_expires"),
            ("revoked_access_tokens", "idx_revoked_access_tokens_expires"),
            ("usage_counters", "idx_usage_counters_expires"),
        ):
            plan = " ".join(
                row[3]
                for row in db.execute(
                    f"EXPLAIN QUERY PLAN DELETE FROM {table} WHERE expires_at<=?", (0,)
                )
            )
            assert index in plan, f"{table}: {plan}"
        names = {row[1] for row in db.execute("PRAGMA index_list(refresh_sessions)")}
        assert "idx_refresh_sessions_subject" in names


def test_indexes_are_added_to_an_existing_database(tmp_path):
    path = str(tmp_path / "legacy.db")
    SQLiteStore(Settings(database_path=path))
    import sqlite3

    with sqlite3.connect(path) as db:
        db.execute("DROP INDEX idx_refresh_sessions_expires")
        db.execute("DROP INDEX idx_refresh_sessions_subject")
    reopened = SQLiteStore(Settings(database_path=path))
    with reopened.connection() as db:
        names = {row[1] for row in db.execute("PRAGMA index_list(refresh_sessions)")}
    assert {"idx_refresh_sessions_expires", "idx_refresh_sessions_subject"} <= names


def test_unauthenticated_database_writes_do_not_run_on_the_event_loop():
    assert not inspect.iscoroutinefunction(v1_api.create_session)
    assert not inspect.iscoroutinefunction(v1_api.refresh_session)
