"""What an amount stands on is computed from the records, not by the model.

Brief AQ (2026-10-12). Asked whether something was worth buying and how much,
the chat answered with its own arithmetic: in the brief's P0 the same goal's
monthly need came out as 631 in one answer and 650 in the next, no reserve was
ever said in months, and the PAC was subtracted only because the model
remembered to (the reader's third test round saw it forgotten). `sizing`
computes the figures once: the cash above a reserve of essential expenses (its
months the reader's, or the app's default said as one), what is left each
month once the PACs and the goals' recorded contributions are taken out, and
what each goal needs a month.

Every name and amount here is invented.
"""

from __future__ import annotations

import datetime
import importlib

import pytest

from app import advisor, dated, planning, questionnaire
from app.database import SessionLocal

TODAY = "2026-03-10"


def _sizing():
    """The module under test, imported when a test runs, so each test says on
    its own what is missing where the module is absent."""
    return importlib.import_module("app.sizing")


def _made(response) -> int:
    assert response.status_code in (200, 201), response.text
    return response.json()["id"]


def _account(client, name, kind="bank", balance=None) -> int:
    iid = _made(client.post("/api/institutions", json={"name": name, "type": kind}))
    if balance is not None:
        made = client.post(f"/api/institutions/{iid}/cash-anchors",
                           json={"date": "2026-03-01", "amount": balance, "currency": "EUR"})
        assert made.status_code == 201, made.text
    return iid


def _flow(client, side, name, amount, start, account=None, **extra) -> int:
    path = "/api/income-sources" if side == "income" else "/api/expenses"
    return _made(client.post(path, json={
        "name": name, "amount": amount, "currency": "EUR", "frequency": "monthly",
        "start_date": start, "institution_id": account, **extra}))


def _plan(client, name, amount, start, source, frequency="monthly", end=None) -> int:
    return _made(client.post("/api/accumulation-plans", json={
        "name": name, "amount": amount, "currency": "EUR", "frequency": frequency,
        "execution": "whole_units", "source_institution_id": source, "start_date": start,
        "end_date": end, "targets": [{"symbol": "EXMP.MI", "isin": None, "asset_name": "Example Fund",
                                      "institution_id": source, "weight": 1}]}))


def _goal(client, name, **fields) -> int:
    return _made(client.post("/api/goals", json={"name": name, "currency": "EUR", **fields}))


@pytest.fixture
def today(monkeypatch):
    monkeypatch.setattr(dated, "today", lambda: TODAY)
    return TODAY


@pytest.fixture
def records(client, today):
    """Three accounts with a balance on 1 March and one with none; a salary,
    rent (essential), groceries (no nature) and a phone (discretionary) whose
    payment on the 3rd takes its account below zero; four PACs (monthly,
    quarterly, one that starts in April, one that ended); a goal with a
    recorded contribution, one whose date has passed, one with no target."""
    bank = _account(client, "Example Bank", balance=9000)
    broker = _account(client, "Broker A", kind="broker", balance=500)
    card = _account(client, "Card Account", balance=100)
    _account(client, "Spare Account")
    _flow(client, "income", "Salary", 3000, "2025-01-27", bank, kind="active", category="salary")
    _flow(client, "expense", "Rent", 900, "2025-01-05", bank, nature="essential", category="housing")
    _flow(client, "expense", "Groceries", 300, "2025-01-28", None, category="food")
    _flow(client, "expense", "Phone", 160, "2025-01-03", card, nature="discretionary",
          category="other")
    _plan(client, "Example PAC", 200, "2026-01-15", broker)
    _plan(client, "Quarterly PAC", 150, "2025-12-01", broker, frequency="quarterly")
    _plan(client, "Later PAC", 100, "2026-04-01", broker)
    _plan(client, "Ended PAC", 80, "2025-01-01", broker, end="2025-12-31")
    _goal(client, "New car", type="target_amount", target_amount=12000, target_date="2027-09-10",
          current_amount=3000, monthly_contribution=250)
    _goal(client, "Old trip", type="target_amount", target_amount=2000, target_date="2026-01-31",
          current_amount=500, monthly_contribution=100)
    _goal(client, "Grow", type="long_term_growth")


def _room():
    with SessionLocal() as db:
        return _sizing().room(db)


# --- The cash, and the reserve above which a sum may be put in now -------------------


def test_the_cash_is_the_registers_and_names_the_accounts_that_need_saying(records):
    found = _room()
    # 9,000 less the rent of 5 March; 500; 100 less the phone of 3 March; none.
    assert found.cash == 8540.0
    assert found.below_zero == (("Card Account", -60.0),)
    assert found.no_balance == ("Spare Account",)
    assert found.on == TODAY and found.base == "EUR"


def test_the_reserve_is_the_apps_default_said_as_one(records):
    found = _room()
    held = found.reserve
    # Six months of the rent and the groceries, which have no nature and count
    # as essential; the phone is discretionary and does not.
    assert (held.months, held.from_profile) == (6.0, False)
    assert (held.essential, held.unclassified, held.amount) == (900.0, 300.0, 7200.0)
    assert found.cash_above_reserve == 1340.0
    said = _sizing().as_dict(found)["reserve"]["months_from"]
    assert said == "the app's default of 6 months: their profile does not say"


def test_the_reserve_is_the_readers_own_when_the_profile_says_it(records, client):
    saved = client.put("/api/survey", json=[
        {"question_key": "reserve_months", "topic": "Risk & values",
         "question": questionnaire.BY_KEY["reserve_months"].text, "answer": "4"},
    ])
    assert saved.status_code == 200, saved.text
    found = _room()
    assert (found.reserve.months, found.reserve.from_profile, found.reserve.amount) == (4.0, True, 4800.0)
    assert found.cash_above_reserve == 3740.0
    assert _sizing().as_dict(found)["reserve"]["months_from"] == "their answer in the Profile"


def test_no_expense_in_force_sizes_no_reserve(client, today):
    bank = _account(client, "Example Bank", balance=2000)
    _flow(client, "income", "Salary", 3000, "2025-01-27", bank, kind="active", category="salary")
    found = _room()
    assert found.reserve.amount is None and found.cash_above_reserve is None
    # Nothing says what a month costs, so nothing is left to say either.
    assert found.expenses is None and found.left is None and found.left_after_commitments is None


# --- What is left each month -----------------------------------------------------------


def test_what_is_left_each_month_takes_out_the_pacs_and_the_goals_contributions(records):
    found = _room()
    assert (found.income, found.expenses, found.left) == (3000.0, 1360.0, 1640.0)
    assert [(c.kind, c.name, c.monthly, c.recorded) for c in found.commitments] == [
        ("pac", "Example PAC", 200.0, "200.00 EUR monthly"),
        # A third of a quarterly plan's amount, as a quarterly expense's.
        ("pac", "Quarterly PAC", 50.0, "150.00 EUR quarterly"),
        ("goal", "New car", 250.0, "250.00 EUR a month"),
    ]
    # The PAC that starts in April is said apart and taken out of nothing; the
    # ended one and the goal whose date has passed are not there at all.
    assert [(c.name, c.starts) for c in found.starting_later] == [("Later PAC", "2026-04-01")]
    assert found.left_after_commitments == 1140.0


def test_each_goal_says_what_it_needs_a_month_beside_what_is_recorded(records):
    needs = {g.name: g for g in _room().goals}
    assert set(needs) == {"New car", "Old trip"}
    car = needs["New car"]
    years = (datetime.date(2027, 9, 10) - datetime.date(2026, 3, 10)).days / 365.25
    assert car.months_left == round(years * 12, 1) == 18.0
    assert car.gap == 9000.0
    assert car.monthly_need == round(9000 / (years * 12), 2) == 498.98
    assert car.recorded_monthly == 250.0
    assert car.required_return == planning.required_annual_return(3000, 250, 12000, years)
    assert car.assessment == planning.assess(car.required_return)
    # What the need asks beyond the 250 recorded; and what is left each month
    # were it met, 1,140 less that, both the app's and never the model's.
    assert car.shortfall == round(498.98 - 250, 2) == 248.98
    assert _room().left_if_goals_met == round(1140 - 248.98, 2) == 891.02
    trip = needs["Old trip"]
    assert (trip.months_left, trip.monthly_need, trip.assessment) == (None, None, "Its date has passed.")


def test_the_tool_reads_every_figure_with_what_it_is(records):
    said = _sizing().as_dict(_room())
    assert said["cash"] == {
        "on_the_accounts": 8540.0,
        "accounts_below_zero": [{"account": "Card Account", "cash": -60.0}],
        "accounts_with_no_balance": ["Spare Account"],
    }
    assert said["cash_above_reserve"] == 1340.0
    assert said["each_month"]["left_after_commitments"] == 1140.0
    assert said["each_month"]["starting_later_not_counted"] == [
        {"what": "pac", "name": "Later PAC", "a_month": 100.0, "as_recorded": "100.00 EUR monthly",
         "from": "2026-04-01"},
    ]
    assert said["goals"][0]["needs_a_month_with_no_growth"] == 498.98
    assert said["goals"][0]["lacks_a_month"] == 248.98
    assert said["each_month"]["left_if_each_goal_gets_its_need"] == 891.02
    assert (said["investments"], said["financial_wealth"]) == (0.0, 8540.0)


# --- The picture -------------------------------------------------------------------------


def _section(text: str, heading: str) -> list[str]:
    lines = text.splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith(heading))
    end = next((i for i in range(start, len(lines)) if not lines[i]), len(lines))
    return lines[start:end]


def test_the_picture_says_what_is_committed_each_month_and_what_is_left_after_it(records):
    with SessionLocal() as db:
        picture = advisor.build_context(db)
    flows = _section(picture, "## Income and expenses in force today")
    assert flows[3] == "- Left each month (income minus expenses): 1640.00, a savings rate of 54.7%"
    assert flows[4] == (
        '- Already committed each month beside the expenses: the PAC "Example PAC" 200.00 '
        '(200.00 EUR monthly); the PAC "Quarterly PAC" 50.00 (150.00 EUR quarterly); the goal '
        '"New car" 250.00 (250.00 EUR a month, its recorded contribution). '
        "Left each month after them: 1140.00"
    )
    assert flows[5] == (
        '- Starting after today, so NOT taken out above: the PAC "Later PAC" 100.00 '
        "(100.00 EUR monthly) from 2026-04-01"
    )


def test_the_analysts_picture_is_told_no_goal(records):
    with SessionLocal() as db:
        picture = advisor.build_portfolio_context(db)
    assert "Already committed each month" not in picture
    assert "New car" not in picture


def test_nothing_committed_adds_no_line(client, today):
    bank = _account(client, "Example Bank", balance=2000)
    _flow(client, "income", "Salary", 3000, "2025-01-27", bank, kind="active", category="salary")
    _flow(client, "expense", "Rent", 900, "2025-01-05", bank, nature="essential", category="housing")
    with SessionLocal() as db:
        picture = advisor.build_context(db)
    assert "committed each month" not in picture


# --- The Profile asks for the reserve ------------------------------------------------------


def test_the_profile_asks_how_many_months_the_reserve_is(client):
    asked = {q["key"]: q for q in client.get("/api/survey/questions").json()}
    question = asked["reserve_months"]
    assert (question["topic"], question["type"]) == ("Risk & values", "number")
    assert question["text"] == (
        "How many months of essential expenses do you want to keep aside as a reserve, "
        "whatever you invest?"
    )
    assert questionnaire.fit(questionnaire.BY_KEY["reserve_months"], "4") == "4"
    with pytest.raises(questionnaire.AnswerRefused):
        questionnaire.fit(questionnaire.BY_KEY["reserve_months"], "four months")
