"""The chat corrects and deletes an account's balance as its page does.

Brief AP, item 4 (2026-10-12). Brief AN gave the chat `set_cash_balance`: a
day's figure added, or replaced on a day that has one. A balance could not be
moved to another day or deleted but on the account's page. `change_cash_balance`
is that page's ✎ and Delete: a correction is the PUT of the day, the amount
and the currency (`DatedAmountEditor`'s `toPayload`), through
`crud.update_cash_anchor`; a deletion its DELETE.

What that does was measured on copies of the test database before the card
was written (the record, brief AP's plan): deleting an account's only balance
leaves its cash unprojected and counted 0 in every total, raising them where
the projection was negative; deleting its latest balance runs the cash from
the one before it, with everything dated after that one; a balance moved to a
later day leaves today's cash as it was and the days between without it. The
card says it in figures (`cash_moves`), and so does the page's Delete, from
the same function; `set_cash_balance`'s card that replaces a day says the
same figures.

Today is pinned. Every account, line and figure is invented.
"""

from __future__ import annotations

import json

import pytest
from conftest import _empty_every_table

from app import advisor, dated

TODAY = "2026-10-12"


@pytest.fixture(autouse=True)
def today(monkeypatch):
    monkeypatch.setattr(dated, "today", lambda: TODAY)


def _events(response) -> list[dict]:
    assert response.status_code == 200, response.text
    return [
        json.loads(frame.split("\n")[1][len("data: "):])
        for frame in response.text.strip().split("\n\n")
    ]


def _script(monkeypatch, rounds):
    def fake(system_prompt, messages, model=None, tools=None, cache_at=None, kept_to=None):
        yield from rounds.pop(0)

    monkeypatch.setattr(advisor, "stream_llm", fake)


def _asks(client, monkeypatch, tool: str = "change_cash_balance", **arguments) -> list[dict]:
    call = advisor.ToolCall(id="call_1", name=tool, arguments=json.dumps(arguments))
    _script(monkeypatch, [[("tool_call", call)]])
    return _events(client.post("/api/chat", json={"content": "please"}))


def _draw(client, monkeypatch, tool: str = "change_cash_balance", **arguments) -> dict:
    events = _asks(client, monkeypatch, tool, **arguments)
    cards = [e["card"] for e in events if e["kind"] == "card"]
    assert cards, [e for e in events if e["kind"] == "tool"]
    return cards[0]


def _refused(client, monkeypatch, **arguments) -> str:
    events = _asks(client, monkeypatch, **arguments)
    assert [e for e in events if e["kind"] == "card"] == []
    (refused,) = [e for e in events if e["kind"] == "tool"]
    return refused["detail"]


def _decide(client, monkeypatch, card: dict, decision: str = "confirm"):
    _script(monkeypatch, [[("text", "Done.")]])
    return client.post(f"/api/chat/cards/{card['card_id']}", json={"decision": decision})


def _made(response) -> int:
    assert response.status_code == 201, response.text
    return response.json()["id"]


def _setup(client) -> dict:
    """Three euro accounts. Example Bank: two balances, a salary on the 10th
    and a rent on the 3rd. Example Broker: one balance and a fee on the 22nd.
    Example Card: one balance and a plan on the 27th that takes it below
    zero."""
    ids = {}
    for name, balances, flows in (
        ("Example Bank", [("2026-08-28", 1800.0), ("2026-09-14", 4600.0)],
         [("income", "Salary", 2700.0, "2026-01-10"), ("expense", "Rent", 900.0, "2026-01-03")]),
        ("Example Broker", [("2026-09-02", 1200.0)], [("expense", "Fees", 40.0, "2026-01-22")]),
        ("Example Card", [("2026-09-08", 30.0)], [("expense", "Card plan", 260.0, "2026-01-27")]),
    ):
        iid = _made(client.post("/api/institutions", json={"name": name}))
        ids[name] = iid
        for day, amount in balances:
            _made(client.post(
                f"/api/institutions/{iid}/cash-anchors", json={"date": day, "amount": amount, "currency": "EUR"}
            ))
        for side, line, amount, first in flows:
            if side == "income":
                _made(client.post("/api/income-sources", json={
                    "name": line, "kind": "active", "amount": amount, "currency": "EUR",
                    "frequency": "monthly", "institution_id": iid, "start_date": first, "end_date": None,
                }))
            else:
                _made(client.post("/api/expenses", json={
                    "name": line, "nature": "essential", "category": "housing", "amount": amount,
                    "currency": "EUR", "frequency": "monthly", "institution_id": iid,
                    "start_date": first, "end_date": None,
                }))
    return ids


def _anchor(client, ids: dict, account: str, day: str) -> dict:
    (found,) = [a for a in client.get(f"/api/institutions/{ids[account]}/cash-anchors").json() if a["date"] == day]
    return found


def _rows(client) -> list[dict]:
    return [
        {k: v for k, v in a.items() if k not in ("id", "created_at")}
        for a in client.get("/api/cash-anchors").json()
    ]


def _cash(client, ids: dict, account: str) -> dict:
    return client.get(f"/api/institutions/{ids[account]}/cash", params={"as_of": TODAY}).json()


def _by_chat_and_by_form(client, monkeypatch, arguments: dict, form) -> list[dict]:
    _setup(client)
    card = _draw(client, monkeypatch, **arguments)
    assert _decide(client, monkeypatch, card).status_code == 200
    by_chat = _rows(client)

    _empty_every_table()
    form(client, _setup(client))
    by_form = _rows(client)

    assert by_chat == by_form
    return by_chat


def _form_save(client, anchor: dict, **changed) -> None:
    """The account page's ✎ and Save: the day, the amount and the currency,
    as `toPayload` sends them, with the boxes the reader changed."""
    body = {"date": anchor["date"], "amount": anchor["amount"], "currency": anchor["currency"], **changed}
    saved = client.put(f"/api/cash-anchors/{anchor['id']}", json=body)
    assert saved.status_code == 200, saved.text


# --- The records, as the page writes them ---------------------------------------------------


def test_a_corrected_amount_is_the_pages_save(client, monkeypatch):
    rows = _by_chat_and_by_form(
        client, monkeypatch,
        {"action": "correct", "account": "Example Bank", "date": "2026-09-14", "new_amount": 4350},
        lambda client, ids: _form_save(client, _anchor(client, ids, "Example Bank", "2026-09-14"), amount=4350.0),
    )
    assert ("2026-09-14", 4350.0, "EUR") in [(a["date"], a["amount"], a["currency"]) for a in rows]


def test_a_balance_moved_to_another_day_is_the_pages_save(client, monkeypatch):
    rows = _by_chat_and_by_form(
        client, monkeypatch,
        {"action": "correct", "account": "Example Bank", "date": "2026-09-14", "new_date": "2026-09-30"},
        lambda client, ids: _form_save(client, _anchor(client, ids, "Example Bank", "2026-09-14"), date="2026-09-30"),
    )
    assert ("2026-09-30", 4600.0) in [(a["date"], a["amount"]) for a in rows]


def test_a_deleted_balance_is_the_pages_delete(client, monkeypatch):
    def form(client, ids):
        gone = client.delete(f"/api/cash-anchors/{_anchor(client, ids, 'Example Bank', '2026-09-14')['id']}")
        assert gone.status_code == 204

    rows = _by_chat_and_by_form(
        client, monkeypatch, {"action": "delete", "account": "Example Bank", "date": "2026-09-14"}, form,
    )
    assert "2026-09-14" not in [a["date"] for a in rows]


# --- What a deletion moves, in figures -----------------------------------------------------


def test_the_latest_deleted_runs_the_cash_from_the_balance_before_it(client, monkeypatch):
    """1,800.00 on 28 August, then two rents and two salaries: 5,400.00, where
    the balance of 14 September and what came after it gave 6,400.00."""
    ids = _setup(client)
    card = _draw(client, monkeypatch, action="delete", account="Example Bank", date="2026-09-14")
    assert card["title"] == "delete balance: Example Bank, 4600.00 EUR on 2026-09-14"
    assert card["diff"] == [
        {"field": "Balance", "now": "2026-09-14: 4600.00 EUR", "proposed": "deleted"},
        {"field": "Cash at Example Bank today", "now": "6400.00 EUR", "proposed": "5400.00 EUR"},
    ]
    assert card["consequence"] == (
        "It goes, and nothing brings it back. Example Bank's cash changes from 2026-09-14 on; it "
        "then runs from its balance of 2026-08-28, with everything dated after it."
    )
    assert _decide(client, monkeypatch, card).status_code == 200
    assert _cash(client, ids, "Example Bank")["projected"] == pytest.approx(5400.0)


def test_an_earlier_balance_deleted_moves_only_the_days_it_held(client, monkeypatch):
    _setup(client)
    card = _draw(client, monkeypatch, action="delete", account="Example Bank", date="2026-08-28")
    assert card["diff"] == [{"field": "Balance", "now": "2026-08-28: 1800.00 EUR", "proposed": "deleted"}]
    assert card["consequence"] == (
        "It goes, and nothing brings it back. Example Bank's cash changes from 2026-08-28 to "
        "2026-09-13, with no balance in force there, and not today: its latest balance, of "
        "2026-09-14, is the one its cash today runs from."
    )


def test_the_only_balance_deleted_leaves_the_cash_unprojected_and_counted_zero(client, monkeypatch):
    ids = _setup(client)
    card = _draw(client, monkeypatch, action="delete", account="Example Broker", date="2026-09-02")
    assert card["diff"] == [
        {"field": "Balance", "now": "2026-09-02: 1200.00 EUR", "proposed": "deleted"},
        {"field": "Cash at Example Broker today", "now": "1160.00 EUR", "proposed": "not projected, no balance"},
    ]
    assert card["consequence"] == (
        "It goes, and nothing brings it back. Example Broker is then left with no balance: its "
        "cash is no longer projected, and every total counts 0 for it where it counts 1160.00 EUR "
        "today. With no balance, every form proposes the base, EUR, for Example Broker's amounts."
    )
    assert _decide(client, monkeypatch, card).status_code == 200
    cash = _cash(client, ids, "Example Broker")
    assert (cash["anchor_date"], cash["projected"]) == (None, 0.0)


def test_a_negative_cash_left_without_a_balance_raises_the_total(client, monkeypatch):
    """As measured on a copy: an account projected below zero counts 0 once
    its only balance goes, and every total rises by what it was below."""
    _setup(client)

    def total() -> float:
        return sum(p["projected"] for p in client.get("/api/cash/positions", params={"as_of": TODAY}).json())

    before = total()
    card = _draw(client, monkeypatch, action="delete", account="Example Card", date="2026-09-08")
    assert "every total counts 0 for it where it counts -230.00 EUR today" in card["consequence"]
    assert _decide(client, monkeypatch, card).status_code == 200
    assert total() == pytest.approx(before + 230.0)


def test_the_pages_delete_asks_with_the_cards_words_and_todays_figures(client, monkeypatch):
    ids = _setup(client)
    anchor = _anchor(client, ids, "Example Bank", "2026-09-14")
    asked = client.get(f"/api/cash-anchors/{anchor['id']}/deletion")
    assert asked.status_code == 200, asked.text
    assert asked.json() == {
        "said": (
            "It goes, and nothing brings it back. Example Bank's cash changes from 2026-09-14 on: "
            "today from 6400.00 EUR to 5400.00 EUR; it then runs from its balance of 2026-08-28, "
            "with everything dated after it."
        )
    }
    only = _anchor(client, ids, "Example Broker", "2026-09-02")
    assert client.get(f"/api/cash-anchors/{only['id']}/deletion").json() == {
        "said": (
            "It goes, and nothing brings it back. Example Broker is then left with no balance: its "
            "cash is no longer projected, and every total counts 0 for it where it counts 1160.00 "
            "EUR today. With no balance, every form proposes the base, EUR, for Example Broker's "
            "amounts."
        )
    }


def test_the_pages_delete_of_a_balance_that_is_gone_is_404(client):
    gone = client.get("/api/cash-anchors/999/deletion")
    assert gone.status_code == 404
    assert gone.json() == {"detail": "Cash anchor 999 not found"}


# --- What a correction moves, in figures ---------------------------------------------------


def test_a_corrected_amount_moves_todays_cash_by_its_difference(client, monkeypatch):
    _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_amount=4350)
    assert card["title"] == "correct balance: Example Bank on 2026-09-14, 4600.00 EUR → 4350.00 EUR"
    assert card["diff"] == [
        {"field": "Held", "now": "4600.00 EUR", "proposed": "4350.00 EUR"},
        {"field": "Cash at Example Bank today", "now": "6400.00 EUR", "proposed": "6150.00 EUR"},
    ]
    assert card["consequence"] == (
        "A correction: the balance takes these figures. Example Bank's cash changes from 2026-09-14 on."
    )


def test_a_balance_moved_earlier_brings_in_what_crosses_it(client, monkeypatch):
    """To 7 September: the salary of the 10th now comes after it."""
    _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_date="2026-09-07")
    assert card["title"] == "correct balance: Example Bank on 2026-09-14, moved to 2026-09-07"
    assert card["diff"] == [
        {"field": "Day", "now": "2026-09-14", "proposed": "2026-09-07"},
        {"field": "Cash at Example Bank today", "now": "6400.00 EUR", "proposed": "9100.00 EUR"},
    ]
    assert card["consequence"] == (
        "A correction: the balance takes these figures. Example Bank's cash changes from 2026-09-07 "
        "on; it then runs from its balance of 2026-09-07, with everything dated after it."
    )


def test_an_only_balance_moved_past_a_transfer_says_the_days_without_one_apart(client, monkeypatch):
    """Moved past a transfer it then holds: the days before it have no balance
    in force, and from it on the cash is the balance's, the transfer inside it.
    Each said apart."""
    ids = _setup(client)
    made = client.post("/api/transfers", json={
        "date": "2026-09-09", "from_institution_id": ids["Example Bank"], "to_institution_id": ids["Example Broker"],
        "amount": 500.0, "currency": "EUR", "to_currency": "EUR", "to_amount": None,
    })
    assert made.status_code == 201, made.text
    card = _draw(client, monkeypatch, action="correct", account="Example Broker", date="2026-09-02", new_date="2026-09-18")
    assert card["diff"] == [
        {"field": "Day", "now": "2026-09-02", "proposed": "2026-09-18"},
        {"field": "Cash at Example Broker today", "now": "1660.00 EUR", "proposed": "1160.00 EUR"},
    ]
    assert card["consequence"] == (
        "A correction: the balance takes these figures. Example Broker's cash changes from "
        "2026-09-02 to 2026-09-17, with no balance in force there, and from 2026-09-18 on; it then "
        "runs from its balance of 2026-09-18, with everything dated after it."
    )


def test_a_balance_moved_later_says_which_past_days_change(client, monkeypatch):
    """The reviewer's case, from the measurement: moved later with nothing
    dated between, today's cash stays, and the days between read the balance
    before it."""
    _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_date="2026-09-30")
    assert card["diff"] == [{"field": "Day", "now": "2026-09-14", "proposed": "2026-09-30"}]
    assert card["consequence"] == (
        "A correction: the balance takes these figures. Example Bank's cash changes from 2026-09-14 "
        "to 2026-09-29, and not today."
    )


def test_an_only_balance_moved_later_leaves_the_days_between_without_one(client, monkeypatch):
    _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Broker", date="2026-09-02", new_date="2026-09-18")
    assert card["consequence"] == (
        "A correction: the balance takes these figures. Example Broker's cash changes from "
        "2026-09-02 to 2026-09-17, with no balance in force there, and not today."
    )


def test_a_currency_corrected_says_what_the_forms_then_propose(client, monkeypatch):
    _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_currency="USD")
    assert card["diff"][0] == {"field": "Held", "now": "4600.00 EUR", "proposed": "4600.00 USD"}
    assert card["consequence"].endswith(
        "Where this balance is in force, the forms then propose USD for Example Bank's amounts."
    )


def test_set_cash_balance_replacing_a_day_says_the_same_figures(client, monkeypatch):
    """Brief AN's card for a day that has a balance is a correction too: its
    diff carries the cash today and its words the days, from the same function."""
    _setup(client)
    card = _draw(client, monkeypatch, "set_cash_balance", account="Example Bank", date="2026-09-14", amount=4350)
    assert card["diff"] == [
        {"field": "Balance on 2026-09-14", "now": "4600.00 EUR", "proposed": "4350.00 EUR"},
        {"field": "Cash at Example Bank today", "now": "6400.00 EUR", "proposed": "6150.00 EUR"},
    ]
    assert card["consequence"].startswith("This replaces the balance already on 2026-09-14, which does not come back.")
    assert card["consequence"].endswith("Example Bank's cash changes from 2026-09-14 on.")


# --- Refused in words -------------------------------------------------------------------


def test_a_balance_not_on_record_is_refused_with_those_that_are(client, monkeypatch):
    _setup(client)
    said = _refused(client, monkeypatch, action="delete", account="Example Bank", date="2026-09-15")
    assert said.endswith(
        "Example Bank has no balance on 2026-09-15. Its balances on record: 2026-08-28 1800.00 EUR; "
        "2026-09-14 4600.00 EUR."
    ), said


def test_a_day_that_has_a_balance_is_refused_as_the_page_refuses_it(client, monkeypatch):
    _setup(client)
    said = _refused(
        client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_date="2026-08-28",
    )
    assert said.endswith(
        "Example Bank already has a balance on 2026-08-28, 1800.00 EUR: one balance a day, as the "
        "account's page keeps them. Correct that one, or delete one of the two first."
    ), said


def test_a_day_that_has_not_happened_is_refused(client, monkeypatch):
    _setup(client)
    said = _refused(
        client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_date="2999-01-01",
    )
    assert "Those arguments do not fit change_cash_balance" in said


def test_a_correction_that_changes_nothing_is_refused(client, monkeypatch):
    _setup(client)
    said = _refused(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_amount=4600)
    assert said.endswith(
        "The balance already says that: Example Bank held 4600.00 EUR on 2026-09-14. Nothing would change."
    ), said


# --- Stale and rejected -------------------------------------------------------------------


def test_a_card_is_stale_once_the_balance_changes(client, monkeypatch):
    ids = _setup(client)
    card = _draw(client, monkeypatch, action="delete", account="Example Bank", date="2026-09-14")
    _form_save(client, _anchor(client, ids, "Example Bank", "2026-09-14"), amount=4700.0)
    assert _decide(client, monkeypatch, card).status_code == 409
    assert _anchor(client, ids, "Example Bank", "2026-09-14")["amount"] == 4700.0


def test_a_card_is_stale_once_the_cash_it_states_moves(client, monkeypatch):
    """A transfer recorded since moves the figures the card states."""
    ids = _setup(client)
    card = _draw(client, monkeypatch, action="correct", account="Example Bank", date="2026-09-14", new_amount=4350)
    made = client.post("/api/transfers", json={
        "date": "2026-10-02", "from_institution_id": ids["Example Bank"], "to_institution_id": ids["Example Broker"],
        "amount": 100.0, "currency": "EUR", "to_currency": "EUR", "to_amount": None,
    })
    assert made.status_code == 201, made.text
    assert _decide(client, monkeypatch, card).status_code == 409
    assert _anchor(client, ids, "Example Bank", "2026-09-14")["amount"] == 4600.0


def test_a_rejected_card_writes_nothing(client, monkeypatch):
    _setup(client)
    before = _rows(client)
    card = _draw(client, monkeypatch, action="delete", account="Example Bank", date="2026-09-14")
    assert _decide(client, monkeypatch, card, "reject").status_code == 200
    assert _rows(client) == before
