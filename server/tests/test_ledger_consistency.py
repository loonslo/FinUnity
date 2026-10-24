from datetime import UTC, datetime, timedelta

import httpx
import pytest
from fastapi.testclient import TestClient
from test_web_api import register

from finunity_server.config import Settings
from finunity_server.main import create_app


@pytest.fixture
def ledger():
    app = create_app(
        Settings(
            database_path=":memory:",
            expose_verification_code=True,
            email_auth_enabled=True,
            ocr_enabled=True,
            holding_prices_url="https://prices.example.test/batch",
        )
    )
    with TestClient(app) as client:
        auth = register(client)
        headers = {"Authorization": f"Bearer {auth['access_token']}"}
        account = client.post(
            "/api/v1/accounts",
            headers=headers,
            json={
                "name": "Cash",
                "account_type": "BANK",
                "currency": "CNY",
                "opening_balance": "100",
            },
        ).json()["data"]
        yield app, client, headers, account


def add_holding(client, headers, account, **overrides):
    item = {
        "account_id": account["account_id"],
        "name": "ETF",
        "asset_type": "ETF",
        "instrument_id": "XSHG:510300",
        "currency": "CNY",
        "quantity": "3",
        "average_cost": "1.005",
        "metadata": {"current_price": "1.015", "risk_bucket": "AGGRESSIVE"},
    }
    item.update(overrides)
    response = client.post(
        "/api/v1/holdings:batch-upsert",
        headers=headers,
        json={"client_request_id": str(datetime.now(UTC)), "items": [item]},
    )
    return response


def test_snapshot_rounding_cash_allocation_and_unknown_cost(ledger):
    _, client, headers, account = ledger
    holding = add_holding(client, headers, account).json()["data"]["items"][0]
    snapshot = client.get("/api/v1/ledger/summary", headers=headers).json()["data"]
    assert holding["market_value_minor"] == 305
    assert holding["cost_minor"] == 302
    assert holding["unrealized_profit_minor"] == 3
    assert snapshot["records"]["holdings"][0] == holding
    assert snapshot["accounts"] == []
    assert snapshot["base"]["holdings_value_minor"] == 305
    assert snapshot["base"]["total_assets_minor"] == 305
    assert sum(item["value"] for item in snapshot["allocation"]["items"]) == 305
    assert snapshot["allocation"]["items"][0]["value"] == 305
    # 305 minor units * 30% = 91.5, rounded half up to 92, less the actual 305.
    assert snapshot["allocation"]["items"][0]["gap_minor"] == -213
    assert snapshot["allocation"]["items"][1]["gap_minor"] == 183
    assert snapshot["allocation"]["items"][2]["gap_minor"] == 31
    unknown = add_holding(client, headers, account, average_cost=None).json()["data"]["items"][0]
    assert unknown["unrealized_profit_minor"] is None
    snapshot = client.get("/api/v1/ledger/summary", headers=headers).json()["data"]
    assert snapshot["base"]["unrealized_profit_minor"] == 3
    assert sum(item["average_cost"] is None for item in snapshot["records"]["holdings"]) == 1


def test_quote_value_quantity_is_not_multiplied_by_mark_price(ledger):
    _, client, headers, account = ledger
    holding = add_holding(
        client,
        headers,
        account,
        currency="USDT",
        quantity="299.82",
        average_cost="94.37747",
        metadata={
            "current_price": "94.57734",
            "quantity_unit": "USDT",
            "quantity_kind": "VALUE",
            "risk_bucket": "BALANCED",
        },
    ).json()["data"]["items"][0]

    assert holding["quantity"] == 299.82
    assert holding["quantity_kind"] == "VALUE"
    assert holding["market_value_minor"] == 29982
    assert holding["unrealized_profit_minor"] is None


def test_zero_price_and_invalid_metadata(ledger):
    _, client, headers, account = ledger
    holding = add_holding(client, headers, account, metadata={"current_price": 0}).json()["data"][
        "items"
    ][0]
    assert holding["market_value_minor"] == 0
    assert holding["unrealized_profit_minor"] == -302
    for metadata in ({"current_price": -1}, {"current_price": "NaN"}, {"risk_bucket": "TYPO"}):
        assert add_holding(client, headers, account, metadata=metadata).status_code == 400
    assert client.get("/api/v1/ledger/summary?month=2026-13", headers=headers).status_code == 400


def test_transactions_do_not_mutate_cash_snapshots(ledger):
    _, client, headers, account = ledger
    response = client.post(
        "/api/v1/transactions:batch-upsert",
        headers=headers,
        json={
            "client_request_id": "withdraw",
            "items": [
                {
                    "account_id": account["account_id"],
                    "transaction_type": "WITHDRAWAL",
                    "amount": "101",
                    "currency": "CNY",
                    "occurred_at": "2026-09-01",
                }
            ],
        },
    )
    assert response.status_code == 200
    # The summary defaults to the *current* month, so ask for the transaction's month explicitly;
    # relying on the default made this test fail from 2026-10-01 onwards.
    snapshot = client.get("/api/v1/ledger/summary?month=2026-09", headers=headers).json()["data"]
    assert snapshot["base"]["cash_balance_minor"] == 0
    assert snapshot["base"]["total_assets_minor"] == 0
    assert snapshot["base"]["expense_minor"] == 10100


def mock_prices(monkeypatch, items=None, status=200):
    original = httpx.AsyncClient
    transport = httpx.MockTransport(lambda request: httpx.Response(status, json={"items": items}))
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda **kwargs: original(transport=transport, **kwargs)
    )


def test_refresh_persists_and_all_views_agree(ledger, monkeypatch):
    _, client, headers, account = ledger
    holding = add_holding(client, headers, account, metadata={"risk_bucket": "AGGRESSIVE"}).json()[
        "data"
    ]["items"][0]
    quote = {
        "instrument_id": "XSHG:510300",
        "currency": "CNY",
        "price": "2.015",
        "as_of": (datetime.now(UTC) - timedelta(minutes=1)).isoformat(),
        "source": "test-provider",
    }
    mock_prices(monkeypatch, [quote])
    refresh = client.post("/api/v1/holdings:refresh", headers=headers).json()["data"]
    assert refresh["status"] == "success"
    assert refresh["updated_count"] == 1
    current = client.get("/api/v1/holdings", headers=headers).json()["data"]["items"][0]
    snapshot = client.get("/api/v1/ledger/summary", headers=headers).json()["data"]
    assert current == snapshot["records"]["holdings"][0]
    assert current["market_value_minor"] == snapshot["base"]["holdings_value_minor"] == 605
    assert current["price_as_of"] == quote["as_of"]
    assert current["market_source"] == "test-provider"
    assert current["version"] == holding["version"] + 1
    client.post("/api/v1/holdings:refresh", headers=headers)
    assert (
        client.get("/api/v1/holdings", headers=headers).json()["data"]["items"][0]["version"]
        == current["version"]
    )
    # An edit from before the daily refresh must be rejected, never overwrite the quote.
    assert (
        add_holding(
            client, headers, account, holding_id=holding["holding_id"], version=holding["version"]
        ).status_code
        == 409
    )


@pytest.mark.parametrize(
    "status,items,expected",
    [
        (503, [], "failed"),
        (
            200,
            [
                {
                    "instrument_id": "XSHG:510300",
                    "currency": "USD",
                    "price": "99",
                    "as_of": datetime.now(UTC).isoformat(),
                    "source": "wrong-currency",
                }
            ],
            "partial",
        ),
        (
            200,
            [
                {
                    "instrument_id": "XSHG:510300",
                    "currency": "CNY",
                    "price": "99",
                    "as_of": "2020-01-01T00:00:00Z",
                    "source": "stale",
                }
            ],
            "partial",
        ),
    ],
)
def test_failed_or_invalid_quotes_preserve_ledger(ledger, monkeypatch, status, items, expected):
    _, client, headers, account = ledger
    holding = add_holding(client, headers, account).json()["data"]["items"][0]
    mock_prices(monkeypatch, items, status)
    assert (
        client.post("/api/v1/holdings:refresh", headers=headers).json()["data"]["status"]
        == expected
    )
    assert client.get("/api/v1/holdings", headers=headers).json()["data"]["items"][0] == holding


def test_unconfigured_refresh_never_uses_demo_quotes(ledger):
    app, client, headers, account = ledger
    holding = add_holding(client, headers, account).json()["data"]["items"][0]
    app.state.settings.holding_prices_url = None
    assert (
        client.post("/api/v1/holdings:refresh", headers=headers).json()["data"]["status"]
        == "unconfigured"
    )
    assert client.get("/api/v1/holdings", headers=headers).json()["data"]["items"][0] == holding
    assert client.post("/api/v1/holdings:refresh").status_code == 401
