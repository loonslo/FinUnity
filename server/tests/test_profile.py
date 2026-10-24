from fastapi.testclient import TestClient

from finunity_server.config import Settings
from finunity_server.main import create_app


def test_display_name_is_optional_after_signup_and_scoped_to_current_user() -> None:
    app = create_app(Settings(database_path=":memory:", password_pepper="profile-test-pepper"))
    with TestClient(app) as client:
        headers = []
        for username in ("profile_one", "profile_two"):
            response = client.post(
                "/api/v1/auth/register",
                json={
                    "username": username,
                    "password": "test-password-123",
                    "password_confirmation": "test-password-123",
                    "accept_privacy": True,
                    "accept_terms": True,
                },
            )
            assert response.status_code == 200
            headers.append({"Authorization": f"Bearer {response.json()['data']['access_token']}"})

        assert client.post("/api/v1/me/profile", json={"display_name": "x"}).status_code == 401
        changed = client.post(
            "/api/v1/me/profile", headers=headers[0], json={"display_name": "  我的账本  "}
        )
        assert changed.status_code == 200
        assert changed.json()["data"]["display_name"] == "我的账本"
        assert changed.json()["data"]["username"] == "profile_one"
        assert "password_hash" not in changed.text
        other = client.get("/api/v1/me", headers=headers[1]).json()["data"]
        assert other["display_name"] != "我的账本"
        for invalid in (
            {"display_name": "x" * 101},
            {"display_name": "x", "user_id": other["user_id"]},
        ):
            response = client.post("/api/v1/me/profile", headers=headers[0], json=invalid)
            assert response.status_code == 400
        cleared = client.post("/api/v1/me/profile", headers=headers[0], json={"display_name": " "})
        assert cleared.status_code == 200
        assert not cleared.json()["data"]["display_name"]
