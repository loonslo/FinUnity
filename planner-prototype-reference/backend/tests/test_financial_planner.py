"""Financial invariants, versioned proposals, actuals and interrupted AI calls."""

from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from decimal import Decimal
from types import SimpleNamespace

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.database import SessionLocal
from app.planner import assistant, engine, service
from app.planner.router import router
from app.planner.schemas import (
    AssistantAnswer,
    Household,
    Occurrence,
    PlanRequest,
    today,
)


def household(**changes):
    anchor = today() - timedelta(days=1)
    data = {
        "as_of": anchor.isoformat(),
        "income": "6000",
        "expenses": "2000",
        "essential_expenses": "1000",
        "reserve_months": 1,
        "assets_complete": True,
        "income_complete": True,
        "expenses_complete": True,
        "debts_complete": True,
        "assets": [
            {
                "id": "cash",
                "name": "现金",
                "amount": "10000",
                "observed_on": anchor.isoformat(),
            },
            {
                "id": "invest",
                "name": "投资",
                "kind": "investment",
                "amount": "1000",
                "observed_on": anchor.isoformat(),
            },
            {
                "id": "home",
                "name": "房产",
                "kind": "property",
                "liquidity": "illiquid",
                "amount": "100000",
                "observed_on": anchor.isoformat(),
            },
        ],
        "debts": [
            {"id": "loan", "name": "贷款", "balance": "2000", "minimum_payment": "100"}
        ],
        "goals": [
            {
                "id": "goal",
                "name": "学习",
                "target": "20000",
                "target_date": (today() + timedelta(days=366)).isoformat(),
                "monthly": "1000",
            }
        ],
        "allocations": [{"asset_id": "cash", "goal_id": "goal", "amount": "2000"}],
    }
    data.update(changes)
    return Household.model_validate(data)


def calc(h=None, request=None, events=None):
    return engine.calculate(
        h or household(), events or [], request or PlanRequest(), today()
    )


@pytest.fixture
def planner_client():
    app = FastAPI()
    app.include_router(router)
    with TestClient(app) as client:
        yield client


def seed(client, h=None):
    response = client.post(
        "/api/v1/planner/household",
        json={
            "expected_revision": 0,
            "household": (h or household()).model_dump(mode="json"),
        },
    )
    assert response.status_code == 200, response.text
    return response.json()


def propose(client, revision=1):
    response = client.post(
        "/api/v1/planner/plans", json={"expected_revision": revision}
    )
    assert response.status_code == 201, response.text
    return response.json()


def event(**changes):
    data = dict(
        client_key="event-one",
        kind="income",
        state="confirmed",
        quality="verified",
        date=today().isoformat(),
        asset_id="cash",
        amount="100",
    )
    data.update(changes)
    return Occurrence.model_validate(data)


def record(client, e, revision=1):
    return client.post(
        "/api/v1/planner/occurrences",
        json={"expected_revision": revision, "event": e.model_dump(mode="json")},
    )


def test_property_and_restricted_assets_are_not_cash():
    result = calc()
    assert result["net_worth"] == "109000.00"
    assert result["available_cash"] == "10000.00"
    assert result["unallocated_cash"] == "7000.00"
    assert result["monthly_surplus"] == "3900.00"


def test_one_cash_balance_cannot_fund_two_goals():
    h = household()
    data = h.model_dump(mode="json")
    data["goals"].append({**data["goals"][0], "id": "second"})
    data["allocations"] = [
        {"asset_id": "cash", "goal_id": "goal", "amount": "6000"},
        {"asset_id": "cash", "goal_id": "second", "amount": "6000"},
    ]
    result = calc(Household.model_validate(data))
    assert not result["executable"]
    assert any("超额分配" in e for e in result["errors"])


def test_reserve_cannot_be_allocated_twice():
    result = calc(
        household(
            allocations=[{"asset_id": "cash", "goal_id": "goal", "amount": "9500"}]
        )
    )
    assert not result["executable"]
    assert "目标现金分配占用了应急预留" in result["errors"]


def test_linked_investment_is_counted_once():
    h = household(
        commitments=[
            {"id": "pac", "name": "定投", "monthly": "1000", "goal_id": "goal"}
        ]
    )
    assert calc(h)["monthly_committed"] == "1000.00"
    assert calc(h)["monthly_remaining"] == "2900.00"


def test_multiple_linked_investments_are_summed_before_deduplication():
    h = household(
        commitments=[
            {"id": "p1", "name": "定投1", "monthly": "600", "goal_id": "goal"},
            {"id": "p2", "name": "定投2", "monthly": "800", "goal_id": "goal"},
        ]
    )
    assert calc(h)["monthly_committed"] == "1400.00"


def test_minimum_payment_already_in_expenses_is_not_counted_twice():
    h = household(
        debts=[
            {
                "id": "loan",
                "name": "贷款",
                "balance": "2000",
                "minimum_payment": "100",
                "payment_in_expenses": True,
            }
        ]
    )
    assert calc(h)["monthly_surplus"] == "4000.00"


@pytest.mark.parametrize("field", ["income", "expenses", "essential_expenses"])
def test_missing_amount_blocks_executable_plan(field):
    result = calc(household(**{field: None}))
    assert result["missing"] and not result["executable"]


def test_missing_fx_preserves_original_currency_and_partial_total():
    h = household()
    h.assets[0].currency = "USD"
    result = calc(h)
    assert result["net_worth"] is None
    assert result["assets"][0]["balance"] == "10000"
    assert any("汇率" in m for m in result["missing"])


def test_no_growth_default_and_scenario_are_repeatable():
    base = calc()
    request = PlanRequest.model_validate(
        {"scenario": {"extension_months": 12, "target_reduction_percent": "10"}}
    )
    adjusted = calc(request=request)
    assert adjusted == calc(request=request)
    assert Decimal(adjusted["goals"][0]["shortfall"]) < Decimal(
        base["goals"][0]["shortfall"]
    )
    assert base["assumptions"]["annual_return"] == "0"


def test_monthly_overcommitment_is_blocked():
    result = calc(request=PlanRequest(monthly={"goal": "99999"}))
    assert not result["executable"]
    assert result["goals"][0]["affordable_monthly"] == "3900.00"


def test_confirmed_estimated_amount_is_actual_but_forecast_is_not():
    result = calc(
        events=[
            event(quality="estimated"),
            event(client_key="forecast", state="forecast", amount="9999"),
        ]
    )
    assert result["available_cash"] == "10100.00"


def test_same_day_event_is_not_reapplied_to_anchor():
    h = household()
    result = calc(h, events=[event(date=h.as_of)])
    assert result["available_cash"] == "10000.00"


def test_transfer_is_not_income_or_extra_wealth():
    e = event(kind="transfer", target_asset_id="invest", goal_id="goal", amount="300")
    result = calc(events=[e])
    assert result["net_worth"] == "109000.00"
    assert result["available_cash"] == "9700.00"
    assert result["goals"][0]["current"] == "2300.00"


def test_debt_payment_separates_principal_and_interest():
    result = calc(
        events=[
            event(kind="debt_payment", debt_id="loan", amount="100", principal="80")
        ]
    )
    assert result["net_worth"] == "108980.00"


def test_invalid_decimal_float_and_future_actual_are_refused(planner_client):
    bad = household().model_dump(mode="json")
    bad["income"] = 0.1
    assert (
        planner_client.post(
            "/api/v1/planner/household", json={"expected_revision": 0, "household": bad}
        ).status_code
        == 422
    )
    with pytest.raises(ValueError):
        event(date=today() + timedelta(days=1))


def test_confirm_twice_creates_only_one_action_and_keeps_snapshot(planner_client):
    seed(planner_client)
    plan = propose(planner_client)
    path = f"/api/v1/planner/plans/{plan['id']}/confirm"
    assert planner_client.post(path, json={}).status_code == 422
    assert (
        planner_client.post(path, json={"accept_shortfalls": True}).status_code == 200
    )
    assert (
        planner_client.post(path, json={"accept_shortfalls": True}).status_code == 200
    )
    assert len(planner_client.get("/api/v1/planner/actions").json()) == 1
    assert planner_client.get("/api/v1/planner/state").json()["revision"] == 1


def test_stale_proposal_cannot_be_confirmed(planner_client):
    seed(planner_client)
    plan = propose(planner_client)
    assert record(planner_client, event()).status_code == 201
    response = planner_client.post(
        f"/api/v1/planner/plans/{plan['id']}/confirm", json={"accept_shortfalls": True}
    )
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "STALE_PROPOSAL"


def test_duplicate_event_and_different_content_same_key(planner_client):
    seed(planner_client)
    first = record(planner_client, event())
    assert first.status_code == 201
    assert (
        record(planner_client, event(), revision=0).json()["id"] == first.json()["id"]
    )
    assert record(planner_client, event(amount="200")).status_code == 409
    assert (
        planner_client.get("/api/v1/planner/state").json()["result"]["available_cash"]
        == "10100.00"
    )


def test_settle_forecast_without_double_counting(planner_client):
    seed(planner_client)
    forecast = record(planner_client, event(state="forecast"))
    assert forecast.status_code == 201
    assert (
        planner_client.get("/api/v1/planner/state").json()["result"]["available_cash"]
        == "10000.00"
    )
    actual = event(client_key="actual", settles_id=forecast.json()["id"], amount="110")
    assert record(planner_client, actual, revision=2).status_code == 201
    assert (
        planner_client.get("/api/v1/planner/occurrences").json()[1]["status"]
        == "settled"
    )
    assert (
        planner_client.get("/api/v1/planner/state").json()["result"]["available_cash"]
        == "10110.00"
    )
    assert (
        record(
            planner_client,
            event(client_key="again", settles_id=forecast.json()["id"]),
            revision=3,
        ).status_code
        == 409
    )


def test_failed_record_is_atomic(planner_client):
    seed(planner_client)
    response = record(planner_client, event(kind="expense", amount="99999"))
    assert response.status_code == 422
    assert planner_client.get("/api/v1/planner/occurrences").json() == []
    assert planner_client.get("/api/v1/planner/state").json()["revision"] == 1


def test_action_requires_actual_and_does_not_create_money(planner_client):
    seed(planner_client)
    plan = propose(planner_client)
    planner_client.post(
        f"/api/v1/planner/plans/{plan['id']}/confirm", json={"accept_shortfalls": True}
    )
    action = planner_client.get("/api/v1/planner/actions").json()[0]
    path = f"/api/v1/planner/actions/{action['id']}/complete"
    assert planner_client.post(path, json={}).status_code == 422
    actual = record(
        planner_client,
        event(kind="transfer", target_asset_id="invest", goal_id="goal", amount="1000"),
    )
    assert actual.status_code == 201
    assert (
        planner_client.post(path, json={"event_id": actual.json()["id"]}).status_code
        == 200
    )
    assert len(planner_client.get("/api/v1/planner/occurrences").json()) == 1
    review = planner_client.post(
        f"/api/v1/planner/plans/{plan['id']}/reviews",
        json={"month": today().strftime("%Y-%m")},
    )
    assert review.status_code == 201
    assert review.json()["result"]["goals"][0]["actual"] == "1000.00"
    assert review.json()["result"]["income"] == "0.00"


def test_new_confirmed_plan_preserves_old_version(planner_client):
    seed(planner_client)
    old = propose(planner_client)
    planner_client.post(
        f"/api/v1/planner/plans/{old['id']}/confirm", json={"accept_shortfalls": True}
    )
    new = propose(planner_client)
    planner_client.post(
        f"/api/v1/planner/plans/{new['id']}/confirm", json={"accept_shortfalls": True}
    )
    plans = planner_client.get("/api/v1/planner/plans").json()
    assert {p["status"] for p in plans} == {"active", "superseded"}
    assert (
        next(p for p in plans if p["id"] == old["id"])["household"]["income"] == "6000"
    )


def test_two_simultaneous_confirmations_are_serialized(planner_client):
    seed(planner_client)
    plan = propose(planner_client)

    def confirm():
        with SessionLocal() as db:
            return service.confirm(db, plan["id"], True)["id"]

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert list(pool.map(lambda _: confirm(), range(2))) == [plan["id"]] * 2
    assert len(planner_client.get("/api/v1/planner/actions").json()) == 1


def task_row(db):
    return service.add_document(
        db,
        "task",
        "running",
        1,
        {
            "message": "如何规划",
            "context": {
                "household": household().model_dump(mode="json"),
                "calculation": calc(),
            },
            "input_events": [],
            "model": "fake/model",
            "calls": [],
            "cost_status": "not_started",
            "answer": None,
            "proposal_id": None,
        },
    )


def test_interrupted_tasks_are_persisted_and_never_replayed(planner_client):
    seed(planner_client)
    with SessionLocal() as db:
        row = task_row(db)
        db.commit()
        identifier = row.id
    with SessionLocal() as db:
        assistant.interrupt_unfinished(db)
    result = planner_client.get(f"/api/v1/planner/assistant/tasks/{identifier}").json()
    assert result["status"] == "interrupted" and result["cost_status"] == "unknown"


def test_ai_failure_does_not_prevent_calculation(planner_client, monkeypatch):
    seed(planner_client)
    with SessionLocal() as db:
        row = task_row(db)
        row.status = "queued"
        db.commit()
        identifier = row.id

    def failed(*args):
        raise TimeoutError("private data must not be persisted")

    monkeypatch.setattr(assistant, "call_model", failed)
    assistant.run(identifier)
    task = planner_client.get(f"/api/v1/planner/assistant/tasks/{identifier}").json()
    assert task["status"] == "failed"
    assert "private data" not in task["error"]
    assert planner_client.post("/api/v1/planner/calculate", json={}).json()["result"][
        "executable"
    ]


def test_ai_tool_uses_frozen_input_and_persists_usage(planner_client, monkeypatch):
    from app import advisor

    seed(planner_client)
    with SessionLocal() as db:
        row = task_row(db)
        row.status = "queued"
        db.commit()
        identifier = row.id
    calls = []

    class Message:
        tool_calls = [
            SimpleNamespace(
                id="call1",
                function=SimpleNamespace(name="calculate_plan", arguments="{}"),
            )
        ]

        def model_dump(self, **kwargs):
            return {
                "role": "assistant",
                "tool_calls": [
                    {
                        "id": "call1",
                        "type": "function",
                        "function": {"name": "calculate_plan", "arguments": "{}"},
                    }
                ],
            }

    class Fake:
        chat = None
        completions = None

        def __init__(self):
            self.chat = self.completions = self

        def with_options(self, **kwargs):
            assert kwargs["max_retries"] == 0
            return self

        def create(self, **kwargs):
            calls.append(kwargs)
            if len(calls) == 1:
                choice = SimpleNamespace(finish_reason="tool_calls", message=Message())
            else:
                assert "3900.00" in kwargs["messages"][-1]["content"]
                choice = SimpleNamespace(
                    finish_reason="stop",
                    message=SimpleNamespace(
                        content=AssistantAnswer(
                            message="请核对缺口后确认", proposal=PlanRequest()
                        ).model_dump_json()
                    ),
                )
            return SimpleNamespace(
                id="fake-provider-id",
                choices=[choice],
                usage=SimpleNamespace(
                    model_dump=lambda **kw: {"cost": 0.001, "total_tokens": 100}
                ),
            )

        def close(self):
            pass

    monkeypatch.setattr(advisor, "_client", Fake)
    assistant.run(identifier)
    task = planner_client.get(f"/api/v1/planner/assistant/tasks/{identifier}").json()
    assert task["status"] == "completed" and task["cost_status"] == "known"
    assert len(task["calls"]) == 2 and task["proposal_id"]
    assert planner_client.get("/api/v1/planner/plans").json()[0]["status"] == "pending"


def test_ai_not_configured_is_explicit(planner_client, monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    response = planner_client.post(
        "/api/v1/planner/assistant/tasks",
        json={"expected_revision": 0, "message": "你好"},
    )
    assert response.status_code == 503


def test_moving_assigned_money_does_not_increase_goal_progress():
    e = event(
        kind="transfer",
        target_asset_id="invest",
        goal_id="goal",
        amount="500",
        allocation_mode="move_existing",
    )
    result = calc(events=[e])
    assert result["goals"][0]["current"] == "2000.00"
    assert result["net_worth"] == "109000.00"


def test_moving_more_than_assigned_money_is_refused(planner_client):
    seed(planner_client)
    response = record(
        planner_client,
        event(
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            amount="3000",
            allocation_mode="move_existing",
        ),
    )
    assert response.status_code == 422


def test_void_preserves_record_and_reopens_forecast(planner_client):
    seed(planner_client)
    forecast = record(planner_client, event(state="forecast")).json()
    actual = record(
        planner_client,
        event(client_key="actual", settles_id=forecast["id"]),
        revision=2,
    ).json()
    response = planner_client.post(
        f"/api/v1/planner/occurrences/{actual['id']}/void",
        json={"expected_revision": 3},
    )
    assert response.status_code == 200 and response.json()["status"] == "voided"
    records = planner_client.get("/api/v1/planner/occurrences").json()
    assert (
        len(records) == 2
        and next(r for r in records if r["id"] == forecast["id"])["status"]
        == "forecast"
    )
    assert (
        planner_client.get("/api/v1/planner/state").json()["result"]["available_cash"]
        == "10000.00"
    )


def test_voiding_action_actual_reopens_action(planner_client):
    seed(planner_client)
    plan = propose(planner_client)
    planner_client.post(
        f"/api/v1/planner/plans/{plan['id']}/confirm", json={"accept_shortfalls": True}
    )
    action = planner_client.get("/api/v1/planner/actions").json()[0]
    actual = record(
        planner_client, event(kind="transfer", target_asset_id="invest", goal_id="goal")
    ).json()
    planner_client.post(
        f"/api/v1/planner/actions/{action['id']}/complete",
        json={"event_id": actual["id"]},
    )
    assert (
        planner_client.post(
            f"/api/v1/planner/occurrences/{actual['id']}/void",
            json={"expected_revision": 2},
        ).status_code
        == 200
    )
    assert (
        planner_client.get("/api/v1/planner/actions").json()[0]["status"] == "pending"
    )


def test_adopted_scenario_is_visible_without_changing_facts(planner_client):
    seed(planner_client)
    response = planner_client.post(
        "/api/v1/planner/plans",
        json={"expected_revision": 1, "scenario": {"extra_income": "500"}},
    ).json()
    planner_client.post(
        f"/api/v1/planner/plans/{response['id']}/confirm",
        json={"accept_shortfalls": True},
    )
    state = planner_client.get("/api/v1/planner/state").json()
    assert state["household"]["income"] == "6000"
    assert state["active_result"]["monthly_surplus"] == "4400.00"
    assert state["result"]["monthly_surplus"] == "3900.00"


def test_unknown_expense_and_empty_goals_cannot_be_executable():
    assert not calc(household(goals=[], allocations=[]))["executable"]


def active_action(client):
    seed(client)
    plan = propose(client)
    assert (
        client.post(
            f"/api/v1/planner/plans/{plan['id']}/confirm",
            json={"accept_shortfalls": True},
        ).status_code
        == 200
    )
    return client.get("/api/v1/planner/actions").json()[0]


def batch(client, items, revision=1, key="batch-one"):
    return client.post(
        "/api/v1/planner/occurrences/batch",
        json={
            "expected_revision": revision,
            "batch_key": key,
            "items": [item.model_dump(mode="json") for item in items],
        },
    )


def test_goal_actuals_auto_link_without_manual_completion(planner_client):
    action = active_action(planner_client)
    actual = record(
        planner_client,
        event(kind="transfer", target_asset_id="invest", goal_id="goal", amount="400"),
    )
    assert actual.status_code == 201
    progress = planner_client.get("/api/v1/planner/actions").json()[0]
    assert progress["id"] == action["id"] and progress["status"] == "in_progress"
    assert (
        progress["actual_amount"] == "400.00"
        and progress["remaining_amount"] == "600.00"
    )
    assert progress["event_ids"] == [actual.json()["id"]]


def test_multiple_contributions_accumulate_and_void_reduces_progress(planner_client):
    action = active_action(planner_client)
    items = [
        event(
            client_key=f"pay-{i}",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            action_id=action["id"],
            amount="500",
        )
        for i in range(2)
    ]
    response = batch(planner_client, items)
    assert response.status_code == 201, response.text
    progress = planner_client.get("/api/v1/planner/actions").json()[0]
    assert progress["status"] == "completed" and len(progress["event_ids"]) == 2
    assert progress["remaining_amount"] == "0.00"
    first = response.json()["items"][0]["id"]
    assert (
        planner_client.post(
            f"/api/v1/planner/occurrences/{first}/void", json={"expected_revision": 3}
        ).status_code
        == 200
    )
    progress = planner_client.get("/api/v1/planner/actions").json()[0]
    assert (
        progress["status"] == "in_progress" and progress["remaining_amount"] == "500.00"
    )


def test_partial_amount_cannot_be_manually_marked_complete(planner_client):
    action = active_action(planner_client)
    actual = record(
        planner_client,
        event(kind="transfer", target_asset_id="invest", goal_id="goal", amount="100"),
    ).json()
    response = planner_client.post(
        f"/api/v1/planner/actions/{action['id']}/complete",
        json={"event_id": actual["id"]},
    )
    assert (
        response.status_code == 422
        and response.json()["detail"]["code"] == "INCOMPLETE_ACTION"
    )


def test_forecast_and_moving_assigned_money_do_not_complete_plan(planner_client):
    active_action(planner_client)
    items = [
        event(
            client_key="future",
            state="forecast",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            amount="1000",
        ),
        event(
            client_key="move",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            allocation_mode="move_existing",
            amount="1000",
        ),
    ]
    assert batch(planner_client, items).status_code == 201
    assert (
        planner_client.get("/api/v1/planner/actions").json()[0]["actual_amount"]
        == "0.00"
    )


def test_action_link_cannot_name_different_goal_or_income(planner_client):
    action = active_action(planner_client)
    response = record(planner_client, event(action_id=action["id"]))
    assert response.status_code == 422
    assert planner_client.get("/api/v1/planner/occurrences").json() == []


def test_link_to_superseded_plan_is_rejected(planner_client):
    action = active_action(planner_client)
    new = propose(planner_client)
    planner_client.post(
        f"/api/v1/planner/plans/{new['id']}/confirm", json={"accept_shortfalls": True}
    )
    response = record(
        planner_client,
        event(
            action_id=action["id"],
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
        ),
    )
    assert response.status_code == 409


def test_mixed_batch_has_correct_balance_and_is_idempotent(planner_client):
    seed(planner_client)
    items = [
        event(amount="300"),
        event(client_key="spend", kind="expense", amount="50"),
        event(
            client_key="invest-more",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            amount="500",
        ),
    ]
    response = batch(planner_client, items)
    assert response.status_code == 201
    assert batch(planner_client, items, revision=0).json() == response.json()
    result = planner_client.get("/api/v1/planner/state").json()["result"]
    assert result["available_cash"] == "9750.00" and result["net_worth"] == "109250.00"
    assert len(planner_client.get("/api/v1/planner/occurrences").json()) == 3


def test_failure_in_later_batch_row_rolls_back_entire_batch(planner_client):
    seed(planner_client)
    response = batch(
        planner_client,
        [event(amount="300"), event(client_key="bad", kind="expense", amount="999999")],
    )
    assert response.status_code == 422 and response.json()["detail"]["item_index"] == 1
    assert planner_client.get("/api/v1/planner/occurrences").json() == []
    assert planner_client.get("/api/v1/planner/state").json()["revision"] == 1


def test_batch_failure_restores_forecast_settlement(planner_client):
    seed(planner_client)
    forecast = record(planner_client, event(state="forecast")).json()
    response = batch(
        planner_client,
        [
            event(client_key="settle", settles_id=forecast["id"]),
            event(client_key="bad", kind="expense", amount="999999"),
        ],
        revision=2,
    )
    assert response.status_code == 422
    records = planner_client.get("/api/v1/planner/occurrences").json()
    assert (
        len(records) == 1
        and records[0]["status"] == "forecast"
        and not records[0]["settled_by"]
    )


def test_stale_or_conflicting_batch_is_rejected(planner_client):
    seed(planner_client)
    assert batch(planner_client, [event()], revision=0).status_code == 409
    assert batch(planner_client, [event()]).status_code == 201
    assert batch(planner_client, [event(amount="200")], revision=2).status_code == 409


def test_batch_rejects_duplicate_row_keys(planner_client):
    seed(planner_client)
    assert batch(planner_client, [event(), event()]).status_code == 422
    assert planner_client.get("/api/v1/planner/occurrences").json() == []


def test_concurrent_repeated_batch_creates_one_set_of_records(planner_client):
    from app.planner.schemas import BatchRecordRequest

    seed(planner_client)
    request = BatchRecordRequest(
        expected_revision=1, batch_key="concurrent", items=[event()]
    )

    def submit():
        with SessionLocal() as db:
            return service.record_batch(db, request)["batch_id"]

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: submit(), range(2)))
    assert results[0] == results[1]
    assert len(planner_client.get("/api/v1/planner/occurrences").json()) == 1


def test_monthly_review_updates_one_record_and_never_changes_facts(planner_client):
    action = active_action(planner_client)
    path = f"/api/v1/planner/plans/{action['plan_id']}/reviews"
    month = today().strftime("%Y-%m")
    first = planner_client.post(path, json={"month": month}).json()
    assert first["result"]["income"] == "0.00"
    assert record(planner_client, event()).status_code == 201
    before = planner_client.get("/api/v1/planner/state").json()
    actual = planner_client.get("/api/v1/planner/occurrences").json()
    updated = planner_client.post(path, json={"month": month}).json()
    repeated = planner_client.post(path, json={"month": month}).json()
    assert updated["id"] == repeated["id"] == first["id"]
    assert updated["result"]["income"] == "100.00" and updated["revision"] == 2
    assert len(planner_client.get("/api/v1/planner/reviews").json()) == 1
    assert planner_client.get("/api/v1/planner/state").json() == before
    assert planner_client.get("/api/v1/planner/occurrences").json() == actual
    with SessionLocal() as db:
        assert len(service.rows(db, "review")) == 1


@pytest.mark.parametrize("offset", [-1, 32])
def test_review_only_allows_current_month(planner_client, offset):
    action = active_action(planner_client)
    other = (today().replace(day=1) + timedelta(days=offset)).strftime("%Y-%m")
    response = planner_client.post(
        f"/api/v1/planner/plans/{action['plan_id']}/reviews", json={"month": other}
    )
    assert response.status_code == 422
    assert response.json()["detail"]["code"] in ("REVIEW_WINDOW", "NO_MONTH_PLAN")
    assert planner_client.get("/api/v1/planner/reviews").json() == []


def test_concurrent_monthly_reviews_save_one_document(planner_client):
    action = active_action(planner_client)

    def submit(_):
        with SessionLocal() as db:
            return service.review(db, action["plan_id"], today().strftime("%Y-%m"))[
                "id"
            ]

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(submit, range(2)))
    assert results[0] == results[1]
    with SessionLocal() as db:
        assert len(service.rows(db, "review")) == 1


def test_legacy_duplicate_reviews_are_hidden_but_preserved(planner_client):
    action = active_action(planner_client)
    path = f"/api/v1/planner/plans/{action['plan_id']}/reviews"
    month = today().strftime("%Y-%m")
    first = planner_client.post(path, json={"month": month}).json()
    with SessionLocal() as db:
        legacy = service.add_document(
            db, "review", "completed", 1, {"result": first["result"]}
        )
        legacy_id = legacy.id
        db.commit()
    assert [r["id"] for r in planner_client.get("/api/v1/planner/reviews").json()] == [
        legacy_id
    ]
    updated = planner_client.post(path, json={"month": month}).json()
    assert updated["id"] == legacy_id
    with SessionLocal() as db:
        assert len(service.rows(db, "review")) == 2
        assert (
            service.document(service.get_document(db, first["id"], "review")) == first
        )


def test_new_plan_review_preserves_old_plan_review(planner_client):
    action = active_action(planner_client)
    month = today().strftime("%Y-%m")
    old_path = f"/api/v1/planner/plans/{action['plan_id']}/reviews"
    old = planner_client.post(old_path, json={"month": month}).json()
    new = propose(planner_client)
    assert (
        planner_client.post(
            f"/api/v1/planner/plans/{new['id']}/confirm",
            json={"accept_shortfalls": True},
        ).status_code
        == 200
    )
    assert planner_client.post(old_path, json={"month": month}).status_code == 422
    current = planner_client.post(
        f"/api/v1/planner/plans/{new['id']}/reviews", json={"month": month}
    ).json()
    assert current["id"] != old["id"]
    reviews = planner_client.get("/api/v1/planner/reviews").json()
    assert len(reviews) == 2 and old in reviews


def test_review_updates_after_void_and_excludes_forecasts(planner_client):
    action = active_action(planner_client)
    items = [
        event(
            client_key="actual",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            amount="400",
        ),
        event(
            client_key="forecast",
            state="forecast",
            kind="transfer",
            target_asset_id="invest",
            goal_id="goal",
            amount="600",
        ),
    ]
    assert batch(planner_client, items).status_code == 201
    path = f"/api/v1/planner/plans/{action['plan_id']}/reviews"
    payload = {"month": today().strftime("%Y-%m")}
    first = planner_client.post(path, json=payload).json()
    assert first["result"]["goals"][0]["actual"] == "400.00"
    actual = next(
        r
        for r in planner_client.get("/api/v1/planner/occurrences").json()
        if r["status"] == "confirmed"
    )
    assert (
        planner_client.post(
            f"/api/v1/planner/occurrences/{actual['id']}/void",
            json={"expected_revision": 3},
        ).status_code
        == 200
    )
    updated = planner_client.post(path, json=payload).json()
    assert updated["id"] == first["id"]
    assert updated["result"]["goals"][0]["actual"] == "0.00"
