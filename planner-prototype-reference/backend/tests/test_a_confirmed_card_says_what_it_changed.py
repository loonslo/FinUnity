"""A confirmed card goes back to the chat with what it changed, before and after.

Brief AO, item 5 (2026-10-11). In the brief's paid run (Opus 5.5, on a test
database) the reply after a PAC's amount card was confirmed took its own
earlier carry for another plan's, the card having set it to zero ("una
correzione a quello che ti avevo detto"), and the answer to the next question
said the plan had been at the new amount all along, "la conferma ha quindi
lasciato l'importo com'era". A confirmed card went back to the model as what
it wrote, the figures after the write, which the picture shows too, and
nothing said the card had moved them. Now every confirmed card with a diff
goes back with each line of it, before and after, as the card showed it to
the reader; read off the stored card, so its bytes are the same at every
later turn. A card that changed no figure goes back as before.

The model is faked at `advisor.stream_llm`; every name and figure here is
invented.
"""

from __future__ import annotations

import datetime
import json

import pytest

from app import advisor, dated, prices


def _events(response) -> list[dict]:
    assert response.status_code == 200, response.text
    return [
        json.loads(frame.split("\n")[1][len("data: "):])
        for frame in response.text.strip().split("\n\n")
    ]


def _script(monkeypatch, rounds) -> list[list[dict]]:
    """Each round's messages, as the model was asked them."""
    seen: list[list[dict]] = []

    def fake(system_prompt, messages, model=None, tools=None, cache_at=None, kept_to=None):
        seen.append(list(messages))
        yield from rounds.pop(0)

    monkeypatch.setattr(advisor, "stream_llm", fake)
    return seen


def _call(tool: str, **arguments) -> tuple:
    return ("tool_call", advisor.ToolCall(id="call_1", name=tool, arguments=json.dumps(arguments)))


def _card_answer(messages: list[dict], call_id: str = "call_1") -> dict:
    """What the card's call is answered with, in a request."""
    (answer,) = [m for m in messages if m.get("role") == "tool" and m.get("tool_call_id") == call_id]
    return json.loads(answer["content"])


def _rent(client) -> int:
    bank = client.post("/api/institutions", json={"name": "Example Bank"}).json()["id"]
    client.post(f"/api/institutions/{bank}/cash-anchors", json={"date": "2026-09-30", "amount": 2000.0, "currency": "EUR"})
    made = client.post(
        "/api/expenses",
        json={"name": "Rent", "nature": "essential", "category": "housing", "amount": 800.0, "currency": "EUR",
              "frequency": "monthly", "institution_id": bank, "start_date": "2026-01-05", "end_date": None},
    )
    assert made.status_code == 201, made.text
    return bank


def _drawn_and_confirmed(client, monkeypatch, call: tuple) -> tuple[dict, list[list[dict]]]:
    _script(monkeypatch, [[("text", "Here it is:"), call]])
    events = _events(client.post("/api/chat", json={"content": "please"}))
    card = next(e["card"] for e in events if e["kind"] == "card")
    seen = _script(monkeypatch, [[("text", "Done.")]])
    _events(client.post(f"/api/chat/cards/{card['card_id']}", json={"decision": "confirm"}))
    return card, seen


def test_a_confirmed_card_goes_back_with_what_it_changed_before_and_after(client, monkeypatch):
    _rent(client)
    card, seen = _drawn_and_confirmed(
        client, monkeypatch, _call("write_flow", action="correct", side="expense", name="Rent", amount=850)
    )
    answered = _card_answer(seen[0])
    assert answered["outcome"] == "confirmed"
    assert answered["changed"] == [{"what": "Amount", "before": "800.00", "after": "850.00"}]
    assert answered["written"]["line"]["amount"] == 850.0
    assert list(answered) == ["outcome", "changed", "written"]


def test_the_same_bytes_go_back_at_every_later_turn(client, monkeypatch):
    """Read off the stored card: the turn after the next one sends the card's
    answer as the next one did, so the cache keeps reading it."""
    _rent(client)
    card, _ = _drawn_and_confirmed(
        client, monkeypatch, _call("write_flow", action="correct", side="expense", name="Rent", amount=850)
    )
    conversation = client.get("/api/chat/conversations").json()[0]["id"]
    answers = []
    for question in ("And now?", "And after that?"):
        seen = _script(monkeypatch, [[("text", "Still 850.")]])
        _events(client.post("/api/chat", json={"content": question, "conversation_id": conversation}))
        (answer,) = [m["content"] for m in seen[0] if m.get("role") == "tool" and m.get("tool_call_id") == "call_1"]
        answers.append(answer)
    assert answers[0] == answers[1]
    assert '"changed": [{"what": "Amount", "before": "800.00", "after": "850.00"}]' in answers[0]


def test_a_card_that_changed_no_figure_goes_back_as_before(client, monkeypatch):
    """An append overwrites nothing: its card has no diff, and it goes back
    with what it wrote alone, as every card did."""
    _rent(client)
    _, seen = _drawn_and_confirmed(
        client, monkeypatch,
        _call("write_flow", action="add", side="expense", name="Gym", account="Example Bank", amount=45,
              first_payment="2026-11-03", nature="discretionary", category="leisure"),
    )
    answered = _card_answer(seen[0])
    assert list(answered) == ["outcome", "written"]


@pytest.fixture()
def market(monkeypatch):
    def fetch(symbol, start, end):
        out, day = [], start
        while day < end:
            if day.weekday() < 5:
                out.append((day.isoformat(), 48.0))
            day += datetime.timedelta(days=1)
        return out

    monkeypatch.setattr(prices, "_fetch_close_window", fetch)
    monkeypatch.setattr(prices, "_fetch_currency", lambda symbol: "EUR")
    monkeypatch.setattr(dated, "today", lambda: "2026-10-11")


def test_the_pac_amount_card_goes_back_with_the_carry_it_set_to_zero(client, monkeypatch, market):
    """The run's case: the carry the card set to zero is said as the card's
    doing, beside the amount it changed."""
    broker = client.post("/api/institutions", json={"name": "Broker A", "type": "broker"}).json()["id"]
    client.post(f"/api/institutions/{broker}/cash-anchors", json={"date": "2026-06-30", "amount": 10000.0, "currency": "EUR"})
    made = client.post(
        "/api/accumulation-plans",
        json={"name": "Example PAC", "amount": 300.0, "currency": "EUR", "frequency": "monthly",
              "execution": "whole_units", "source_institution_id": broker, "start_date": "2026-07-15",
              "end_date": None, "targets": [{"symbol": "XWLD.MI", "isin": None, "asset_name": "XWLD.MI",
                                            "institution_id": broker, "weight": 1}]},
    )
    assert made.status_code == 201, made.text
    assert client.post("/api/transactions/catch-up").status_code == 200
    _, seen = _drawn_and_confirmed(client, monkeypatch, _call("change_pac_amount", plan="Example PAC", amount=250))
    assert _card_answer(seen[0])["changed"] == [
        {"what": "Amount per occurrence", "before": "300.00 EUR", "after": "250.00 EUR"},
        {"what": "Carried", "before": "36.00 EUR", "after": "0.00 EUR"},
    ]
