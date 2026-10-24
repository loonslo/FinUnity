import io
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from PIL import Image

from finunity_server.config import Settings
from finunity_server.main import create_app
from finunity_server.models import (
    AssetType,
    CostSource,
    ModelRecognitionResult,
    RecognizedHolding,
)


def register(client: TestClient, email: str = "web@example.com") -> dict:
    code_response = client.post(
        "/api/v1/auth/email/code",
        json={"email": email, "purpose": "REGISTER", "locale": "zh-CN"},
    )
    assert code_response.status_code == 200
    code = code_response.json()["data"]["dev_code"]
    response = client.post(
        "/api/v1/auth/email/register",
        json={
            "email": email,
            "code": code,
            "display_name": "Web User",
            "client_request_id": "register-1",
        },
    )
    assert response.status_code == 200
    return response.json()["data"]


def test_web_registration_and_portfolio_crud() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
            email_code_pepper="test-email-pepper",
        )
    )
    with TestClient(app) as client:
        auth = register(client)
        headers = {"Authorization": f"Bearer {auth['access_token']}"}

        me = client.get("/api/v1/me", headers=headers)
        assert me.status_code == 200
        assert me.json()["data"]["email"] == "web@example.com"

        account = client.post(
            "/api/v1/accounts",
            headers=headers,
            json={
                "name": "Main brokerage",
                "institution": "Example",
                "account_type": "BROKERAGE",
                "currency": "CNY",
            },
        ).json()["data"]
        assert account["version"] == 1

        holding_response = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "holdings-1",
                "items": [
                    {
                        "account_id": account["account_id"],
                        "instrument_id": "XSHG:510300",
                        "name": "CSI 300 ETF",
                        "asset_type": "ETF",
                        "quantity": "100",
                        "average_cost": "3.5",
                        "currency": "CNY",
                        "metadata": {"current_price": 3.6, "risk_bucket": "BALANCED"},
                    }
                ],
            },
        )
        assert holding_response.status_code == 200
        holding = holding_response.json()["data"]["items"][0]
        assert holding["current_price"] == 3.6

        transaction_response = client.post(
            "/api/v1/transactions:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "transactions-1",
                "items": [
                    {
                        "account_id": account["account_id"],
                        "transaction_type": "DEPOSIT",
                        "amount": "1000.00",
                        "currency": "CNY",
                        "occurred_at": "2026-09-02",
                        "note": '{"title":"Salary","category":"Income"}',
                    }
                ],
            },
        )
        assert transaction_response.status_code == 200
        assert transaction_response.json()["data"]["items"][0]["amount_minor"] == 100000
        assert transaction_response.json()["data"]["items"][0]["transaction_type"] == "deposit"

        conflict = client.post(
            f"/api/v1/accounts/{account['account_id']}:archive",
            headers=headers,
            json={"version": account["version"]},
        )
        assert conflict.status_code == 409

        archived = client.post(
            f"/api/v1/holdings/{holding['holding_id']}:archive",
            headers=headers,
            json={"version": holding["version"]},
        )
        assert archived.status_code == 200


def test_web_data_requires_user_bearer() -> None:
    app = create_app(Settings(database_path=":memory:"))
    with TestClient(app) as client:
        assert client.get("/api/v1/accounts").status_code == 401


def test_account_currency_defaults_and_holdings_can_use_quote_currency() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
            email_code_pepper="test-email-pepper",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "quote-currency@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        account_response = client.post(
            "/api/v1/accounts",
            headers=headers,
            json={
                "name": "Imported account",
                "account_type": "CRYPTO",
                "opening_balance": "100",
            },
        )
        assert account_response.status_code == 200
        account = account_response.json()["data"]
        assert account["currency"] == "CNY"

        holding_response = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "quote-currency-holding",
                "items": [
                    {
                        "account_id": account["account_id"],
                        "name": "USDT wallet",
                        "asset_type": "CRYPTO",
                        "quantity": "2",
                        "average_cost": "9",
                        "currency": "USDT",
                        "metadata": {"current_price": 10},
                    }
                ],
            },
        )
        assert holding_response.status_code == 200

        ledger = client.get("/api/v1/ledger/summary", headers=headers).json()["data"]
        assert ledger["accounts"] == []
        assert ledger["base"]["total_assets_minor"] == 0
        assert ledger["by_currency"]["USDT"]["holdings_value_minor"] == 2000
        assert ledger["by_currency"]["USDT"]["total_assets_minor"] == 2000


def test_accountless_import_is_versioned_and_idempotent() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
            email_code_pepper="test-email-pepper",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "import-batch@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        source = client.post(
            "/api/v1/sources",
            headers=headers,
            json={"name": "Test broker"},
        ).json()["data"]
        create_body = {
            "client_request_id": "import-create-1",
            "rows": [],
        }
        created = client.post("/api/v1/holding-imports", headers=headers, json=create_body)
        assert created.status_code == 200
        batch = created.json()["data"]
        repeated = client.post("/api/v1/holding-imports", headers=headers, json=create_body)
        assert repeated.json()["data"]["batch_id"] == batch["batch_id"]

        row = {
            "row_id": "row-1",
            "name": "USDT",
            "asset_type": "CRYPTO",
            "quantity": "10",
            "average_cost": "1",
            "current_price": 1,
            "currency": "USDT",
            "source_id": source["source_id"],
            "action": "ADD",
        }
        preview = client.post(
            f"/api/v1/holding-imports/{batch['batch_id']}:preview",
            headers=headers,
            json={"version": batch["version"], "rows": [row]},
        )
        assert preview.status_code == 200
        ready = preview.json()["data"]
        assert ready["status"] == "READY"
        committed = client.post(
            f"/api/v1/holding-imports/{batch['batch_id']}:commit",
            headers=headers,
            json={
                "client_request_id": "import-commit-1",
                "version": ready["version"],
                "rows": [row],
            },
        )
        assert committed.status_code == 200
        assert committed.json()["data"]["items"][0]["source_id"] == source["source_id"]
        retry = client.post(
            f"/api/v1/holding-imports/{batch['batch_id']}:commit",
            headers=headers,
            json={
                "client_request_id": "import-commit-1",
                "version": ready["version"],
                "rows": [row],
            },
        )
        assert retry.status_code == 200
        assert len(client.get("/api/v1/holdings", headers=headers).json()["data"]["items"]) == 1

        existing = committed.json()["data"]["items"][0]
        update_batch = client.post(
            "/api/v1/holding-imports",
            headers=headers,
            json={"client_request_id": "import-create-update-1", "rows": []},
        ).json()["data"]
        update_row = {
            **row,
            "row_id": "row-update-1",
            "quantity": "25",
            "action": "UPDATE",
            "holding_id": existing["holding_id"],
            "version": existing["version"],
        }
        update_preview = client.post(
            f"/api/v1/holding-imports/{update_batch['batch_id']}:preview",
            headers=headers,
            json={"version": update_batch["version"], "rows": [update_row]},
        )
        assert update_preview.status_code == 200
        updated = client.post(
            f"/api/v1/holding-imports/{update_batch['batch_id']}:commit",
            headers=headers,
            json={
                "client_request_id": "import-commit-update-1",
                "version": update_preview.json()["data"]["version"],
                "rows": [update_row],
            },
        )
        assert updated.status_code == 200
        saved = client.get("/api/v1/holdings", headers=headers).json()["data"]["items"]
        assert len(saved) == 1
        assert saved[0]["quantity"] == 25


def test_logout_revokes_access_token() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "logout@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        assert client.post("/api/v1/auth/logout", headers=headers).status_code == 200
        assert client.get("/api/v1/me", headers=headers).status_code == 401


def test_ledger_calculates_cash_holdings_transfers_and_currency_boundaries() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "ledger@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}

        def account(name: str, currency: str, opening: str) -> dict:
            response = client.post(
                "/api/v1/accounts",
                headers=headers,
                json={
                    "name": name,
                    "account_type": "BANK",
                    "currency": currency,
                    "opening_balance": opening,
                },
            )
            assert response.status_code == 200
            return response.json()["data"]

        cny_source = account("CNY source", "CNY", "1000")
        cny_target = account("CNY target", "CNY", "100")
        usd = account("USD account", "USD", "50")
        holding = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "ledger-holding",
                "items": [
                    {
                        "account_id": cny_source["account_id"],
                        "name": "Fund",
                        "asset_type": "FUND",
                        "quantity": "10",
                        "average_cost": "8",
                        "currency": "CNY",
                        "metadata": {"current_price": 10},
                    }
                ],
            },
        )
        assert holding.status_code == 200
        transactions = client.post(
            "/api/v1/transactions:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "ledger-transactions",
                "items": [
                    {
                        "account_id": cny_source["account_id"],
                        "transaction_type": "DEPOSIT",
                        "amount": "200",
                        "currency": "CNY",
                        "occurred_at": "2026-09-01",
                    },
                    {
                        "account_id": cny_source["account_id"],
                        "transaction_type": "WITHDRAWAL",
                        "amount": "30",
                        "currency": "CNY",
                        "occurred_at": "2026-09-02",
                    },
                    {
                        "account_id": cny_source["account_id"],
                        "to_account_id": cny_target["account_id"],
                        "transaction_type": "TRANSFER",
                        "amount": "300",
                        "currency": "CNY",
                        "occurred_at": "2026-09-03",
                    },
                    {
                        "account_id": usd["account_id"],
                        "transaction_type": "DEPOSIT",
                        "amount": "10",
                        "currency": "USD",
                        "occurred_at": "2026-09-04",
                    },
                ],
            },
        )
        assert transactions.status_code == 200

        response = client.get("/api/v1/ledger/summary?month=2026-09", headers=headers)
        assert response.status_code == 200
        ledger = response.json()["data"]
        assert ledger["accounts"] == []
        assert ledger["base"]["holdings_value_minor"] == 10000
        assert ledger["base"]["total_assets_minor"] == 10000
        assert ledger["base"]["income_minor"] == 20000
        assert ledger["base"]["expense_minor"] == 3000
        assert ledger["base"]["net_cashflow_minor"] == 17000
        assert ledger["by_currency"]["USD"]["total_assets_minor"] == 0
        assert ledger["excluded_currencies"] == ["USD"]


def test_transfer_currency_validation_and_transaction_archive_guard() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "transfer@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        accounts = []
        for name, currency in (("CNY", "CNY"), ("USD", "USD")):
            response = client.post(
                "/api/v1/accounts",
                headers=headers,
                json={"name": name, "account_type": "BANK", "currency": currency},
            )
            accounts.append(response.json()["data"])
        rejected = client.post(
            "/api/v1/transactions:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "cross-currency",
                "items": [
                    {
                        "account_id": accounts[0]["account_id"],
                        "to_account_id": accounts[1]["account_id"],
                        "transaction_type": "TRANSFER",
                        "amount": "10",
                        "currency": "CNY",
                        "occurred_at": "2026-09-01",
                    }
                ],
            },
        )
        assert rejected.status_code == 400
        assert rejected.json()["error"]["code"] == "TRANSFER_CURRENCY_MISMATCH"

        saved = client.post(
            "/api/v1/transactions:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "deposit-guard",
                "items": [
                    {
                        "account_id": accounts[0]["account_id"],
                        "transaction_type": "DEPOSIT",
                        "amount": "10",
                        "currency": "CNY",
                        "occurred_at": "2026-09-01",
                    }
                ],
            },
        )
        assert saved.status_code == 200
        archive = client.post(
            f"/api/v1/accounts/{accounts[0]['account_id']}:archive",
            headers=headers,
            json={"version": accounts[0]["version"]},
        )
        assert archive.status_code == 409
        assert archive.json()["error"]["code"] == "ACCOUNT_HAS_TRANSACTIONS"


def test_batch_upsert_rolls_back_every_item_on_version_conflict() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "atomic@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        account = client.post(
            "/api/v1/accounts",
            headers=headers,
            json={"name": "Atomic", "account_type": "BROKERAGE", "currency": "CNY"},
        ).json()["data"]
        initial = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "atomic-create",
                "items": [
                    {
                        "account_id": account["account_id"],
                        "name": name,
                        "asset_type": "FUND",
                        "quantity": "1",
                        "currency": "CNY",
                    }
                    for name in ("First", "Second")
                ],
            },
        ).json()["data"]["items"]
        failed = client.post(
            "/api/v1/holdings:batch-upsert",
            headers=headers,
            json={
                "client_request_id": "atomic-update",
                "items": [
                    {
                        "holding_id": initial[0]["holding_id"],
                        "account_id": account["account_id"],
                        "name": "Changed",
                        "asset_type": "FUND",
                        "quantity": "1",
                        "currency": "CNY",
                        "version": initial[0]["version"],
                    },
                    {
                        "holding_id": initial[1]["holding_id"],
                        "account_id": account["account_id"],
                        "name": "Conflict",
                        "asset_type": "FUND",
                        "quantity": "1",
                        "currency": "CNY",
                        "version": 999,
                    },
                ],
            },
        )
        assert failed.status_code == 409
        items = client.get("/api/v1/holdings", headers=headers).json()["data"]["items"]
        assert [item["name"] for item in items] == ["First", "Second"]


def test_refresh_token_survives_server_restart() -> None:
    database_path = Path("data") / f"test-refresh-{uuid4().hex}.db"
    settings = Settings(
        database_path=str(database_path),
        session_secret="persistent-session-secret",
    )
    try:
        with TestClient(create_app(settings)) as first_client:
            tokens = first_client.post(
                "/api/v1/auth/session",
                json={
                    "installation_id": "persistent-installation",
                    "platform": "ANDROID",
                    "app_version": "1.0.0",
                    "version_code": 1,
                    "device_locale": "zh-CN",
                },
            ).json()["data"]
        with TestClient(create_app(settings)) as restarted_client:
            refreshed = restarted_client.post(
                "/api/v1/auth/session:refresh",
                json={"refresh_token": tokens["refresh_token"]},
            )
            replayed = restarted_client.post(
                "/api/v1/auth/session:refresh",
                json={"refresh_token": tokens["refresh_token"]},
            )
        assert refreshed.status_code == 200
        assert replayed.status_code == 401
    finally:
        database_path.unlink(missing_ok=True)


def test_ocr_result_is_persisted_and_visible_after_server_restart() -> None:
    class FakeRecognizer:
        model = "fake"

        async def recognize(self, **_: object) -> ModelRecognitionResult:
            return ModelRecognitionResult(
                detected_provider="Test Broker",
                holdings=[
                    RecognizedHolding(
                        asset_type=AssetType.ETF,
                        name="CSI 300 ETF",
                        security_code="510300",
                        quantity=100,
                        average_cost=3.5,
                        cost_source=CostSource.DISPLAYED_AVERAGE,
                        current_price=3.6,
                        market_value=360,
                        currency="CNY",
                        confidence=0.95,
                        needs_review=False,
                    )
                ],
            )

    database_path = Path("data") / f"test-ocr-{uuid4().hex}.db"
    settings = Settings(
        database_path=str(database_path),
        expose_verification_code=True,
        email_auth_enabled=True,
        ocr_enabled=True,
        session_secret="persistent-ocr-secret",
    )
    image = io.BytesIO()
    Image.new("RGB", (600, 600), "white").save(image, format="PNG")
    try:
        first_app = create_app(settings)
        first_app.state.recognizer = FakeRecognizer()
        with TestClient(first_app) as first_client:
            auth = register(first_client, "ocr@example.com")
            headers = {"Authorization": f"Bearer {auth['access_token']}"}
            parsed = first_client.post(
                "/api/v1/ocr/holdings:parse",
                headers=headers,
                files={"image": ("holdings.png", image.getvalue(), "image/png")},
                data={
                    "client_request_id": "ocr-persistent-1",
                    "locale": "zh-CN",
                    "default_currency": "CNY",
                },
            )
            assert parsed.status_code == 200
            document_id = parsed.json()["data"]["document_id"]
        with TestClient(create_app(settings)) as restarted_client:
            documents = restarted_client.get("/api/v1/ocr/documents", headers=headers)
        assert documents.status_code == 200
        assert documents.json()["data"]["items"] == [
            {
                "document_id": document_id,
                "created_at": documents.json()["data"]["items"][0]["created_at"],
                "as_of_date": None,
                "broker": {"name": "Test Broker", "confidence": 0.8},
                "row_count": 1,
                "warnings": [],
                "version": 1,
            }
        ]
    finally:
        database_path.unlink(missing_ok=True)


def test_logged_out_access_token_stays_revoked_after_server_restart() -> None:
    database_path = Path("data") / f"test-revocation-{uuid4().hex}.db"
    settings = Settings(
        database_path=str(database_path),
        expose_verification_code=True,
        email_auth_enabled=True,
        ocr_enabled=True,
        session_secret="persistent-revocation-secret",
    )
    try:
        with TestClient(create_app(settings)) as first_client:
            auth = register(first_client, "revocation@example.com")
            headers = {"Authorization": f"Bearer {auth['access_token']}"}
            assert first_client.post("/api/v1/auth/logout", headers=headers).status_code == 200
        with TestClient(create_app(settings)) as restarted_client:
            denied = restarted_client.get("/api/v1/me", headers=headers)
        assert denied.status_code == 401
        assert denied.json()["error"]["code"] == "TOKEN_INVALID"
    finally:
        database_path.unlink(missing_ok=True)


def test_preferences_version_supports_repeated_base_currency_updates() -> None:
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            session_secret="test-session-secret",
        )
    )
    with TestClient(app) as client:
        auth = register(client, "preferences@example.com")
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        me = client.get("/api/v1/me", headers=headers).json()["data"]
        assert me["preferences_version"] == 0
        first = client.post(
            "/api/v1/me/preferences",
            headers=headers,
            json={"language": "zh-CN", "base_currency": "USD", "version": 0},
        )
        assert first.status_code == 200
        second = client.post(
            "/api/v1/me/preferences",
            headers=headers,
            json={"language": "en-US", "base_currency": "HKD", "version": 1},
        )
        assert second.status_code == 200
        updated = client.get("/api/v1/me", headers=headers).json()["data"]
        assert updated["base_currency"] == "HKD"
        assert updated["language"] == "en-US"
        assert updated["preferences_version"] == 2
