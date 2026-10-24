"""An account below zero is already in the total the analyst reads.

Brief AR's P0 (2026-10-12, paid, a test copy): the revision and the synthesis
called the cash one could use "a little lower than the total" because one
account stood at -250.00, though the total on the accounts had already taken
those 250 off. Covering the deficit from another account moves cash between
the person's accounts and leaves the total as it is. Brief AQ's P1 bis saw the
same reading in the chat. The analyst's cash register now says so, under the
total, whenever an account is below zero. Every name and figure is invented."""

from __future__ import annotations

import datetime

from app import advisor
from app.database import SessionLocal

DAY = (datetime.date.today() - datetime.timedelta(days=20)).isoformat()
LATER = (datetime.date.today() - datetime.timedelta(days=10)).isoformat()


def _account(client, name, cash):
    iid = client.post("/api/institutions", json={"name": name}).json()["id"]
    assert client.post(
        f"/api/institutions/{iid}/cash-anchors", json={"date": DAY, "amount": cash, "currency": "EUR"}
    ).status_code == 201
    return iid


def _moved(client, source, target, amount):
    """A transfer as the Cash flow form posts it."""
    assert client.post("/api/transfers", json={
        "date": LATER, "from_institution_id": source, "to_institution_id": target,
        "amount": amount, "currency": "EUR", "to_currency": "EUR", "to_amount": None,
    }).status_code == 201


def _register():
    with SessionLocal() as db:
        context = advisor.build_portfolio_context(db)
    start = context.index("## Cash register")
    return context[start:context.index("\n\n", start)]


def test_an_account_below_zero_is_said_to_be_in_the_total_already(client):
    alfa = _account(client, "Banca Alfa", 4000.00)
    beta = _account(client, "Banca Beta", 70.00)
    _moved(client, beta, alfa, 330.00)  # Banca Beta at -260.00
    register = _register()
    assert "- Banca Beta: -260.00" in register
    assert register.endswith(
        "- TOTAL cash on the accounts: 4070.00\n"
        "- Netted in the total above: Banca Beta, below zero. Covering it from another of these "
        "accounts moves cash between them and leaves the total, and every share and month of "
        "cover worked out from it, as they are."
    ), register


def test_two_accounts_below_zero_are_named_together(client):
    alfa = _account(client, "Banca Alfa", 4000.00)
    beta = _account(client, "Banca Beta", 70.00)
    gamma = _account(client, "Banca Gamma", 10.00)
    _moved(client, beta, alfa, 330.00)
    _moved(client, gamma, alfa, 50.00)
    assert (
        "- Netted in the total above: Banca Beta and Banca Gamma, below zero. Covering them from "
        "another of these accounts"
    ) in _register()


def test_no_account_below_zero_adds_no_line(client):
    """Guard: the register reads as it did when nothing is below zero."""
    alfa = _account(client, "Banca Alfa", 4000.00)
    beta = _account(client, "Banca Beta", 700.00)
    _moved(client, beta, alfa, 330.00)
    register = _register()
    assert "below zero" not in register and "Netted" not in register
    assert register.endswith("- TOTAL cash on the accounts: 4700.00")
