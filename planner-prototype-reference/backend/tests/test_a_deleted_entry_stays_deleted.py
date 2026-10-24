"""A ledger entry the reader deletes stays deleted: the catch-up does not write it again.

Brief AO, item 3, by the reader's choice (2026-10-11). Until then, deleting a
PAC's buy opened its occurrence again and the next start bought it again, at
that day's close, with the plan's amount and carry as they were then: the
redo the model's docstring called deliberate ("how a user asks for it to be
done again"). Measured on copies of the test database with an invented plan
(brief AO's plan entry): the carry already held what the deleted buy left, so
the occurrence was bought with its own leftover a second time (the carry 6.18
too high once the last occurrence was bought again, 10.62 once the first was),
and a broker's buy entered by hand in its place was doubled at the next
start. A dividend the catch-up recorded came back the same way.

Now one function deletes an entry for both doors, the ledger form's Delete and
the chat's card (`crud.delete_transaction`). The last buy of an occurrence
deleted leaves that occurrence settled, as deleted by the reader, so it is
never bought again, and the redo is a correction of the buy instead; any of a
plan's buys deleted sets its carry to zero, which was measured against buys
that are gone, and that money stays in the account's cash. A dividend deleted
is remembered by its account, ticker and day, and the catch-up does not record
one there again. A correction keeps a PAC's buy on its occurrence, as before.

Yahoo is faked (every weekday at 48.00 and 52.00; one dividend of 0.30 a
share), the reader's today pinned. Every name and figure here is invented.
"""

from __future__ import annotations

import datetime

import pytest
from sqlalchemy import select

from app import advisor, crud, dated, models, prices
from app.database import SessionLocal

CLOSES = {"XWLD.MI": 48.0, "XBND.MI": 52.0}


@pytest.fixture()
def market(monkeypatch):
    def fetch(symbol, start, end):
        out, day = [], start
        while day < end:
            if day.weekday() < 5:
                out.append((day.isoformat(), CLOSES[symbol]))
            day += datetime.timedelta(days=1)
        return out

    monkeypatch.setattr(prices, "_fetch_close_window", fetch)
    monkeypatch.setattr(prices, "_fetch_currency", lambda symbol: "EUR")
    monkeypatch.setattr(
        prices,
        "get_dividends_since",
        lambda symbol, since: prices.DividendWindow(
            answered=True,
            dividends=[{"date": "2026-09-23", "dps": 0.30}] if symbol == "XDIV.MI" else [],
        ),
    )

    def on(day: str) -> None:
        monkeypatch.setattr(dated, "today", lambda: day)

    on("2026-10-11")
    return on


def _catch_up(client) -> list[tuple]:
    r = client.post("/api/transactions/catch-up")
    assert r.status_code == 200, r.text
    return [(t["kind"], t["plan_occurrence"] or t["date"], t["symbol"], t["quantity"], t["amount"])
            for t in r.json()["created"]]


def _broker(client) -> int:
    iid = client.post("/api/institutions", json={"name": "Broker A", "type": "broker"}).json()["id"]
    r = client.post(
        f"/api/institutions/{iid}/cash-anchors",
        json={"date": "2026-06-30", "amount": 10000.0, "currency": "EUR"},
    )
    assert r.status_code == 201, r.text
    return iid


def _plan(client, iid: int, *, amount: float, start: str, targets: list[tuple[str, float]]) -> int:
    r = client.post(
        "/api/accumulation-plans",
        json={
            "name": "Example PAC", "amount": amount, "currency": "EUR", "frequency": "monthly",
            "execution": "whole_units", "source_institution_id": iid, "start_date": start,
            "end_date": None,
            "targets": [
                {"symbol": s, "isin": None, "asset_name": s, "institution_id": iid, "weight": w}
                for s, w in targets
            ],
        },
    )
    assert r.status_code == 201, r.text
    return r.json()["id"]


def _buys(client, plan_id: int) -> list[dict]:
    return [t for t in client.get("/api/transactions").json() if t["plan_id"] == plan_id]


def _buy_of(client, plan_id: int, occurrence: str, symbol: str = "XWLD.MI") -> dict:
    (t,) = [t for t in _buys(client, plan_id) if t["plan_occurrence"] == occurrence and t["symbol"] == symbol]
    return t


def _state(client, iid: int, plan_id: int) -> dict:
    plan = client.get(f"/api/accumulation-plans/{plan_id}").json()
    cash = client.get(f"/api/institutions/{iid}/cash", params={"as_of": dated.today()}).json()
    held = {r["symbol"]: r["quantity"] for r in client.get("/api/dashboard/portfolio").json()["rows"]}
    with SessionLocal() as db:
        settled = sorted(crud.get_plan_occurrences_settled(db, plan_id))
        unfilled = [
            (u.occurrence, u.reason)
            for u in db.scalars(
                select(models.PlanUnfilledOccurrence).where(models.PlanUnfilledOccurrence.plan_id == plan_id)
            )
        ]
    return {"carry": plan["carried_remainder"], "cash": cash["projected"], "held": held,
            "settled": settled, "unfilled": unfilled}


def _single_fund(client, market) -> tuple[int, int]:
    """300.00 a month from 15 July at 48.00: 6 units each time, and 12.00,
    24.00, 36.00 carried; caught up to 11 October."""
    iid = _broker(client)
    plan_id = _plan(client, iid, amount=300.0, start="2026-07-15", targets=[("XWLD.MI", 1)])
    assert len(_catch_up(client)) == 3
    return iid, plan_id


def _form_correction(client, tx: dict, **typed) -> dict:
    """The ledger form's PUT for an entry opened and saved (`Portfolio.tsx`,
    `startEdit` then `submit`), the debit box empty as for a derived amount."""
    body = {
        "kind": tx["kind"], "date": tx["date"], "institution_id": tx["institution_id"],
        "asset_name": tx["asset_name"].strip(), "symbol": (tx["symbol"] or "").strip().upper(),
        "quantity": tx["quantity"], "unit_price": tx["unit_price"],
        "fees": tx["fees"] if tx["fees"] else 0, "currency": tx["currency"],
        "price_currency": tx["price_currency"] or tx["currency"], "amount": None,
    }
    body.update(typed)
    r = client.put(f"/api/transactions/{tx['id']}", json=body)
    assert r.status_code == 200, r.text
    return r.json()


# --- A PAC's buy --------------------------------------------------------------------------


def test_a_pac_buy_deleted_leaves_its_occurrence_settled_and_the_next_start_buys_nothing(client, market):
    iid, plan_id = _single_fund(client, market)
    before = _state(client, iid, plan_id)
    assert before["carry"] == 36.0 and before["cash"] == 9136.0 and before["held"] == {"XWLD.MI": 18.0}

    tx = _buy_of(client, plan_id, "2026-09-15")
    assert client.delete(f"/api/transactions/{tx['id']}").status_code == 204

    after = _state(client, iid, plan_id)
    assert after["settled"] == ["2026-07-15", "2026-08-15", "2026-09-15"]
    ((occurrence, reason),) = after["unfilled"]
    assert occurrence == "2026-09-15"
    assert reason.startswith(crud.DELETED_FROM_THE_LEDGER)
    # The carry, measured against the buys, goes to zero; the money of the
    # deleted buy is back in the account's cash, and its units are gone.
    assert after["carry"] == 0.0
    assert after["cash"] == 9136.0 + 288.0
    assert after["held"] == {"XWLD.MI": 12.0}

    assert _catch_up(client) == []
    assert _state(client, iid, plan_id) == after

    # The next occurrence buys from its own amount.
    market("2026-10-16")
    assert _catch_up(client) == [("buy", "2026-10-15", "XWLD.MI", 6.0, 288.0)]
    assert _state(client, iid, plan_id)["carry"] == 12.0


def test_a_broker_buy_entered_in_place_of_a_deleted_one_is_not_doubled(client, market):
    """The case the old redo doubled: measured on a copy, 24 units for 18."""
    iid, plan_id = _single_fund(client, market)
    tx = _buy_of(client, plan_id, "2026-09-15")
    assert client.delete(f"/api/transactions/{tx['id']}").status_code == 204
    made = client.post(
        "/api/transactions",
        json={"kind": "buy", "date": "2026-09-15", "institution_id": iid, "asset_name": "XWLD.MI",
              "symbol": "XWLD.MI", "quantity": 6, "unit_price": 48.95, "fees": 2.95,
              "currency": "EUR", "price_currency": "EUR", "amount": None},
    )
    assert made.status_code == 201, made.text
    assert _catch_up(client) == []
    assert _state(client, iid, plan_id)["held"] == {"XWLD.MI": 18.0}


def test_one_leg_of_two_deleted_leaves_the_occurrence_to_the_other_and_the_last_settles_it(
    client, market
):
    """500.00 at 70/30, one budget in whole units: 7 at 48.00 and 3 at 52.00,
    8.00 carried."""
    iid = _broker(client)
    market("2026-07-21")
    plan_id = _plan(
        client, iid, amount=500.0, start="2026-07-20", targets=[("XWLD.MI", 70), ("XBND.MI", 30)]
    )
    assert sorted(_catch_up(client)) == [
        ("buy", "2026-07-20", "XBND.MI", 3.0, 156.0),
        ("buy", "2026-07-20", "XWLD.MI", 7.0, 336.0),
    ]
    assert _state(client, iid, plan_id)["carry"] == 8.0

    bond = _buy_of(client, plan_id, "2026-07-20", "XBND.MI")
    assert client.delete(f"/api/transactions/{bond['id']}").status_code == 204
    one = _state(client, iid, plan_id)
    assert one["settled"] == ["2026-07-20"] and one["unfilled"] == []
    assert one["carry"] == 0.0 and one["held"] == {"XWLD.MI": 7.0}
    assert _catch_up(client) == []

    world = _buy_of(client, plan_id, "2026-07-20", "XWLD.MI")
    assert client.delete(f"/api/transactions/{world['id']}").status_code == 204
    last = _state(client, iid, plan_id)
    assert last["settled"] == ["2026-07-20"]
    assert [o for o, _ in last["unfilled"]] == ["2026-07-20"]
    assert _catch_up(client) == []
    assert _buys(client, plan_id) == []


def test_a_corrected_pac_buy_keeps_its_occurrence_and_its_carry(client, market):
    """The redo is a correction now. It keeps the buy on its occurrence, even
    dated in another month, clears `estimated`, and leaves the carry the
    estimate left: the next start buys nothing for it, and the occurrence
    after it spends what is carried."""
    iid, plan_id = _single_fund(client, market)
    tx = _buy_of(client, plan_id, "2026-09-15")
    corrected = _form_correction(client, tx, unit_price=48.95, fees=2.95, date="2026-10-01")
    assert corrected["amount"] == 296.65 and corrected["estimated"] is False
    assert corrected["plan_occurrence"] == "2026-09-15"

    state = _state(client, iid, plan_id)
    assert state["carry"] == 36.0 and state["unfilled"] == []
    assert round(state["cash"], 2) == 9127.35  # 8.65 more spent than the estimate
    assert _catch_up(client) == []

    market("2026-10-16")
    assert _catch_up(client) == [("buy", "2026-10-15", "XWLD.MI", 7.0, 336.0)]
    assert _state(client, iid, plan_id)["carry"] == 0.0


def test_the_picture_says_an_occurrence_was_deleted_not_that_it_ran_and_bought_nothing(client, market):
    iid, plan_id = _single_fund(client, market)
    tx = _buy_of(client, plan_id, "2026-09-15")
    client.delete(f"/api/transactions/{tx['id']}")
    with SessionLocal() as db:
        picture = advisor.build_context(db)
    (line,) = [line for line in picture.split("\n") if line.startswith("- Example PAC:")]
    assert "ran and bought nothing" not in line
    assert (
        "2 occurrences executed; the last is that of 2026-08-15, bought 2026-08-17."
    ) in line
    assert (
        "The buy of its occurrence of 2026-09-15 was deleted from the ledger, so it does not "
        "buy that occurrence again."
    ) in line


# --- A dividend ---------------------------------------------------------------------------


def _holder(client) -> int:
    """20 units of a distributing share on 1 September, its dividend of 0.30
    a share going ex on 23 September."""
    iid = _broker(client)
    sid = client.post(f"/api/institutions/{iid}/snapshots", json={"date": "2026-09-01"}).json()["id"]
    r = client.post(
        f"/api/snapshots/{sid}/holdings",
        json={"asset_name": "Example Dividend Share", "asset_class": "equity", "symbol": "XDIV.MI",
              "quantity": 20, "unit_price": 10, "currency": "EUR", "distribution_policy": "dist"},
    )
    assert r.status_code == 201, r.text
    return iid


def _dividends(client) -> list[tuple]:
    return [(t["date"], t["symbol"], t["amount"], t["estimated"])
            for t in client.get("/api/transactions").json() if t["kind"] == "dividend"]


def test_a_dividend_the_app_recorded_and_deleted_is_not_recorded_again(client, market):
    iid = _holder(client)
    assert _catch_up(client) == [("dividend", "2026-09-23", "XDIV.MI", 20.0, 6.0)]
    (row,) = [t for t in client.get("/api/transactions").json() if t["kind"] == "dividend"]
    assert client.delete(f"/api/transactions/{row['id']}").status_code == 204

    assert _catch_up(client) == []
    assert _dividends(client) == []
    with SessionLocal() as db:
        assert crud.get_dividend_dates_settled(db, iid, "XDIV.MI") == {"2026-09-23"}
        assert crud.get_dividend_dates_recorded(db, iid, "XDIV.MI") == set()


def test_a_dividend_corrected_with_the_brokers_credit_is_not_recorded_again(client, market):
    iid = _holder(client)
    _catch_up(client)
    (row,) = [t for t in client.get("/api/transactions").json() if t["kind"] == "dividend"]
    _form_correction(client, row, amount=4.41)
    assert _catch_up(client) == []
    assert _dividends(client) == [("2026-09-23", "XDIV.MI", 4.41, False)]
    assert iid


def test_a_dividend_entered_by_hand_on_its_ex_date_and_deleted_is_not_recorded_by_the_catch_up(
    client, market
):
    """Any dividend row on the day settles it for the catch-up, the reader's
    own included; so does deleting one."""
    iid = _holder(client)
    made = client.post(
        "/api/transactions",
        json={"kind": "dividend", "date": "2026-09-23", "institution_id": iid,
              "asset_name": "Example Dividend Share", "symbol": "XDIV.MI", "quantity": 20,
              "unit_price": 0.22, "currency": "EUR", "price_currency": "EUR", "amount": None},
    )
    assert made.status_code == 201, made.text
    assert client.delete(f"/api/transactions/{made.json()['id']}").status_code == 204
    assert _catch_up(client) == []
    assert _dividends(client) == []


def test_a_deleted_dividend_is_remembered_for_its_account_ticker_and_day_only(client, market):
    """Another day, another ticker or another account is not settled by it."""
    iid = _holder(client)
    _catch_up(client)
    (row,) = [t for t in client.get("/api/transactions").json() if t["kind"] == "dividend"]
    client.delete(f"/api/transactions/{row['id']}")
    with SessionLocal() as db:
        assert crud.get_dividend_dates_settled(db, iid, "XDIV.MI") == {"2026-09-23"}
        assert crud.get_dividend_dates_settled(db, iid, "XDIV") == set()
        assert crud.get_dividend_dates_settled(db, iid + 1, "XDIV.MI") == set()
