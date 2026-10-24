"""Audit A–E plus renewal/reconciliation invariants, using pytest's isolated SQLite."""

from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from decimal import Decimal

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.planner import schemas, service
from app.planner.router import router
from test_financial_planner import household, event, seed, propose, record

P = "/api/v1/planner"


@pytest.fixture
def clock(monkeypatch):
    current = [date(2026, 10, 19)]
    monkeypatch.setattr(schemas, "today", lambda: current[0])
    monkeypatch.setattr(service, "today", lambda: current[0])
    # The helpers imported above also need the same deterministic clock.
    monkeypatch.setattr("test_financial_planner.today", lambda: current[0])
    return current


@pytest.fixture
def client(clock):
    app = FastAPI()
    app.include_router(router)
    with TestClient(app) as client:
        yield client


def active(client):
    seed(client)
    plan = propose(client)
    assert (
        client.post(
            f"{P}/plans/{plan['id']}/confirm", json={"accept_shortfalls": True}
        ).status_code
        == 200
    )
    return client.get(f"{P}/actions").json()[0]


def contribution(action, **changes):
    return event(
        kind="transfer",
        goal_id="goal",
        target_asset_id="invest",
        action_id=action["id"],
        amount="1000",
        **changes,
    )


def revision(client):
    return client.get(f"{P}/state").json()["revision"]


def test_a_ended_day_anchor_allows_today_but_same_day_anchor_rejects(client, clock):
    action = active(client)
    assert record(client, contribution(action)).status_code == 201
    h = household(as_of=clock[0].isoformat())
    data = h.model_dump(mode="json")
    data["assets"][0]["observed_on"] = clock[0].isoformat()
    assert (
        client.post(
            f"{P}/household", json={"expected_revision": 2, "household": data}
        ).status_code
        == 200
    )
    response = record(client, event(client_key="same-day"), revision=3)
    assert response.status_code == 422
    assert response.json()["detail"]["code"] == "BEFORE_ANCHOR"


def test_b_progress_survives_reanchor_without_replaying_balance_twice(client, clock):
    action = active(client)
    saved = record(client, contribution(action)).json()
    clock[0] = date(2026, 10, 25)
    data = household(as_of="2026-10-25").model_dump(mode="json")
    data["assets"][0].update(amount="9000", observed_on="2026-10-25")
    data["assets"][1].update(amount="2000", observed_on="2026-10-25")
    data["allocations"].append(
        {"asset_id": "invest", "goal_id": "goal", "amount": "1000"}
    )
    result = client.post(
        f"{P}/household", json={"expected_revision": 2, "household": data}
    )
    assert result.status_code == 200, result.text
    balances = {a["id"]: a["balance"] for a in result.json()["result"]["assets"]}
    assert Decimal(balances["cash"]) == 9000
    assert Decimal(balances["invest"]) == 2000
    plan = propose(client, revision=3)
    client.post(f"{P}/plans/{plan['id']}/confirm", json={"accept_shortfalls": True})
    new = next(
        a for a in client.get(f"{P}/actions").json() if a["plan_id"] == plan["id"]
    )
    assert new["actual_amount"] == "1000.00"
    assert new["remaining_amount"] == "0.00"
    assert new["event_ids"] == [saved["id"]]
    review = client.post(
        f"{P}/plans/{plan['id']}/reviews", json={"month": "2026-10"}
    ).json()
    assert review["result"]["goals"][0]["actual"] == "1000.00"


def test_c_renewal_is_explicit_atomic_and_idempotent(client, clock):
    action = active(client)
    clock[0] = date(2026, 11, 2)
    assert not any(a["month"] == "2026-11" for a in client.get(f"{P}/actions").json())
    url = f"{P}/plans/{action['plan_id']}/renew"
    payload = {"expected_revision": 1, "accept_shortfalls": True}
    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: client.post(url, json=payload), range(2)))
    assert [r.status_code for r in responses] == [200, 200]
    assert responses[0].json() == responses[1].json()
    assert (
        len([a for a in client.get(f"{P}/actions").json() if a["month"] == "2026-11"])
        == 1
    )
    assert revision(client) == 1  # A plan action is not a financial fact.
    new = responses[0].json()[0]
    assert record(client, contribution(new)).status_code == 201
    assert (
        client.post(
            f"{P}/plans/{action['plan_id']}/reviews", json={"month": "2026-10"}
        ).status_code
        == 201
    )


@pytest.mark.parametrize("day, expected", [(1, 201), (10, 201), (11, 422)])
def test_c_previous_month_closing_window(client, clock, day, expected):
    action = active(client)
    clock[0] = date(2026, 11, day)
    response = client.post(
        f"{P}/plans/{action['plan_id']}/reviews", json={"month": "2026-10"}
    )
    assert response.status_code == expected
    if expected == 201:
        assert response.json()["result"]["partial_month"] is False
        assert (
            client.post(
                f"{P}/plans/{action['plan_id']}/reviews", json={"month": "2026-10"}
            ).json()["id"]
            == response.json()["id"]
        )


def test_c_prior_month_review_can_use_superseded_plan(client, clock):
    action = active(client)
    clock[0] = date(2026, 11, 2)
    newer = propose(client)
    client.post(f"{P}/plans/{newer['id']}/confirm", json={"accept_shortfalls": True})
    assert (
        client.post(
            f"{P}/plans/{action['plan_id']}/reviews", json={"month": "2026-10"}
        ).status_code
        == 201
    )
    assert (
        client.post(
            f"{P}/plans/{newer['id']}/reviews", json={"month": "2026-10"}
        ).status_code
        == 422
    )


def test_c_renewal_rejects_changed_household_and_stale_revision(client, clock):
    action = active(client)
    client.post(
        f"{P}/household",
        json={
            "expected_revision": 1,
            "household": household(name="修改建档").model_dump(mode="json"),
        },
    )
    clock[0] = date(2026, 11, 2)
    url = f"{P}/plans/{action['plan_id']}/renew"
    assert (
        client.post(url, json={"expected_revision": 1}).json()["detail"]["code"]
        == "STALE_REVISION"
    )
    assert (
        client.post(
            url, json={"expected_revision": 2, "accept_shortfalls": True}
        ).json()["detail"]["code"]
        == "HOUSEHOLD_CHANGED"
    )
    assert not any(a["month"] == "2026-11" for a in client.get(f"{P}/actions").json())


def test_d_stale_drafts_are_visible_unconfirmable_and_discardable(client, clock):
    seed(client)
    plan = propose(client)
    assert record(client, event()).status_code == 201
    plans = client.get(f"{P}/plans").json()
    assert plans[0]["status"] == "stale"
    assert (
        client.post(
            f"{P}/plans/{plan['id']}/confirm", json={"accept_shortfalls": True}
        ).status_code
        == 409
    )
    assert (
        client.post(f"{P}/plans/{plan['id']}/discard", json={}).json()["status"]
        == "discarded"
    )
    assert client.post(f"{P}/plans/{plan['id']}/discard", json={}).status_code == 200
    assert revision(client) == 2


def test_d_date_alone_stales_pending_plan(client, clock):
    seed(client)
    propose(client)
    clock[0] += timedelta(days=1)
    assert client.get(f"{P}/plans").json()[0]["status"] == "stale"


def test_e_needed_monthly_rounds_up_and_all_offered_levers_close_the_gap(client):
    seed(client)
    result = client.post(f"{P}/calculate", json={}).json()["result"]
    goal = result["goals"][0]
    assert Decimal(goal["shortfall"]) > 0
    assert {x["kind"] for x in goal["adjustments"]} == {
        "monthly",
        "extension",
        "reduction",
    }
    for option in goal["adjustments"]:
        response = client.post(f"{P}/calculate", json=option["request"])
        assert response.status_code == 200
        assert response.json()["result"]["goals"][0]["shortfall"] == "0.00", option
    needed = client.post(
        f"{P}/calculate", json={"monthly": {"goal": goal["needed_monthly"]}}
    ).json()
    assert needed["result"]["goals"][0]["shortfall"] == "0.00"


@pytest.mark.parametrize(
    "asset_id,balance,direction,amount",
    [
        ("cash", "10100", "increase", "100"),
        ("cash", "9900", "decrease", "100"),
        ("invest", "1200", "increase", "200"),
        ("cash", "10000", "increase", "0"),
    ],
)
def test_reconciliation_is_a_reversible_non_income_adjustment(
    client, asset_id, balance, direction, amount
):
    action = active(client)
    payload = {
        "expected_revision": 1,
        "client_key": "bank-check",
        "asset_id": asset_id,
        "balance": balance,
    }
    response = client.post(f"{P}/reconciliations", json=payload)
    assert response.status_code == 201, response.text
    adjustment = response.json()
    assert adjustment["event"]["kind"] == "adjustment"
    assert Decimal(adjustment["event"]["amount"]) == Decimal(amount)
    assert adjustment["event"]["adjustment_direction"] == direction
    assert (
        client.post(f"{P}/reconciliations", json=payload).json()["id"]
        == adjustment["id"]
    )
    after = client.get(f"{P}/state").json()
    assert after["household"]["as_of"] == "2026-10-18"
    assert Decimal(
        next(a for a in after["result"]["assets"] if a["id"] == asset_id)["balance"]
    ) == Decimal(balance)
    report = client.post(
        f"{P}/plans/{action['plan_id']}/reviews", json={"month": "2026-10"}
    ).json()["result"]
    assert report["income"] == report["expenses"] == report["debt_payments"] == "0.00"
    assert report["goals"][0]["actual"] == "0.00"
    assert (
        client.post(
            f"{P}/occurrences/{adjustment['id']}/void", json={"expected_revision": 2}
        ).status_code
        == 200
    )
    restored = client.get(f"{P}/state").json()
    expected = "10000" if asset_id == "cash" else "1000"
    assert Decimal(
        next(a for a in restored["result"]["assets"] if a["id"] == asset_id)["balance"]
    ) == Decimal(expected)


def test_reconciliation_conflict_and_failed_adjustment_roll_back(client):
    active(client)
    payload = {
        "expected_revision": 1,
        "client_key": "reconcile",
        "asset_id": "cash",
        "balance": "9900",
    }
    assert client.post(f"{P}/reconciliations", json=payload).status_code == 201
    assert (
        client.post(f"{P}/reconciliations", json={**payload, "balance": "9800"}).json()[
            "detail"
        ]["code"]
        == "IDEMPOTENCY_CONFLICT"
    )
    failed = client.post(
        f"{P}/reconciliations",
        json={
            **payload,
            "expected_revision": 2,
            "client_key": "too-low",
            "balance": "100",
        },
    )
    assert failed.status_code == 422
    assert revision(client) == 2
    assert len(client.get(f"{P}/occurrences").json()) == 1


def test_household_revision_is_separate_and_noop_save_does_not_stale_plan(client):
    seed(client)
    plan = propose(client)
    same = client.post(
        f"{P}/household",
        json={"expected_revision": 1, "household": household().model_dump(mode="json")},
    )
    assert same.json()["revision"] == 1
    assert client.get(f"{P}/plans").json()[0]["status"] == "pending"
    assert record(client, event()).status_code == 201
    state = client.get(f"{P}/state").json()
    assert state["revision"] == 2
    assert state["household_revision"] == 1
    assert (
        client.get(f"{P}/plans").json()[0]["household_revision"]
        == plan["household_revision"]
        == 1
    )
