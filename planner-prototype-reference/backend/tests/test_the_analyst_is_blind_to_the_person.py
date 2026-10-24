"""The analysis's asymmetry is code, both ways, with its tools in it.

The chain's value is that the analyst judges the records without knowing whose
they are, and the confidant argues for the person without seeing a figure
(`chain`). Since brief AR (2026-10-12) the analyst and its revision can look
things up (`analyst_tools`), and a tool is one more door. So unique markers
are planted through the forms' own requests: in the person's questionnaire
answers (a free answer and a figure in it), in a goal (its name and its
amount) and in the tax country, and in the records (an account, its balance,
an income, a holding).
A whole run is then scripted at the transport, the analyst calling every tool
it has and the web, and every request the run sends is read:

- nothing of the person reaches any request of the analyst's or the
  revision's, their tools' answers and the search's own request included;
- but the tax country does: it is the one field of the Profile page the
  analyst reads, with the rates declared beside it;
- no figure or name of the records reaches the confidant;
- the confidant and the synthesis are offered no tool.

Every name, ISIN and figure here is invented."""

from __future__ import annotations

import datetime
import json

import httpx
import pandas as pd
import pytest

from app import advisor, catalogue, chain, composition, prices, websearch
from app.database import SessionLocal

REAL_CALL_LLM, REAL_CALL_ROUND = advisor.call_llm, advisor.call_round
FUND = "IE00ZQXFND01"
DAY = (datetime.date.today() - datetime.timedelta(days=30)).isoformat()

# The goal's amount is whole: the person's context writes it rounded, and a
# marker with cents would miss a rounded copy of itself.
PERSON = ("ZQX-ANSWER-7731", "48271.93", "ZQX-GOAL-4417", "731915")
RECORDS = ("ZQX-BANK-5512", "913742.61", "ZQX-SALARY-6620", "6173.29", "ZQX-FUND-3308")
# The one field of the Profile page the analyst reads. Its rates come with it,
# so that a tax is quoted from the reader's own setting and never invented
# (`advisor._render_tax`): the reader kept both in the analyst's context
# (2026-10-12).
TAX_COUNTRY = "ZQX-COUNTRY-2290"


@pytest.fixture
def planted(client, monkeypatch):
    """The person and the records, each with its markers, through the forms;
    the catalogue, Yahoo and the fund's holdings answering for what is held."""
    monkeypatch.setattr(catalogue, "_fetch_overview", lambda: pd.DataFrame(
        [{"name": "Alfa Blind World UCITS ETF Acc", "ticker": None, "dividends": "Accumulating", "ter": 0.2,
          "size": 900, "replication": "Full replication", "domicile_country": "Ireland", "currency": "USD",
          "number_of_holdings": 1000, "hedged": False}],
        index=pd.Index([FUND], name="isin")))
    assert client.post("/api/instruments/catalogue/refresh").status_code == 200

    assert client.put("/api/survey", json=[
        {"question_key": "about_age", "topic": "About you", "question": "Your age?", "answer": "52"},
        {"question_key": "zqx_own_words", "topic": "In your own words", "question": "Anything else?",
         "answer": f"{PERSON[0]}: we keep {PERSON[1]} aside for a relative."},
    ]).status_code == 200
    assert client.post("/api/goals", json={
        "name": PERSON[2], "type": "target_amount", "currency": "EUR", "target_amount": float(PERSON[3]),
        "target_date": "2031-06-30", "current_amount": 1000, "monthly_contribution": 200,
    }).status_code in (200, 201)
    assert client.put("/api/settings/tax", json={
        "country": TAX_COUNTRY, "capital_gains_rate": 26, "dividend_withholding_rate": 12.5,
    }).status_code == 200

    bank = client.post("/api/institutions", json={"name": RECORDS[0], "type": "bank"}).json()["id"]
    assert client.post(f"/api/institutions/{bank}/cash-anchors", json={
        "date": DAY, "amount": float(RECORDS[1]), "currency": "EUR"}).status_code == 201
    assert client.post("/api/income-sources", json={
        "name": RECORDS[2], "amount": float(RECORDS[3]), "currency": "EUR", "frequency": "monthly",
        "institution_id": bank, "start_date": DAY}).status_code in (200, 201)
    sid = client.post(f"/api/institutions/{bank}/snapshots", json={"date": DAY}).json()["id"]
    for body in (
        {"asset_name": RECORDS[4], "asset_class": "fund_etf", "isin": FUND, "quantity": 10,
         "unit_price": 100, "currency": "EUR"},
        {"asset_name": "Example Blind Share", "asset_class": "equity", "symbol": "EXBL.MI", "quantity": 20,
         "unit_price": 9, "currency": "EUR"},
    ):
        assert client.post(f"/api/snapshots/{sid}/holdings", json=body).status_code == 201

    yesterday = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
    monkeypatch.setattr(prices, "_fetch_lookup", lambda query, count: [
        {"symbol": "EXBL.MI", "name": "Example Blind SpA", "quote_type": "EQUITY", "exchange": "MIL", "price": 9.0}])
    monkeypatch.setattr(prices, "_fetch_close_window", lambda symbol, start, end: [(yesterday, 9.0)])
    monkeypatch.setattr(prices, "_fetch_currency", lambda symbol: "EUR")
    monkeypatch.setattr(prices, "_fetch_name", lambda symbol: "Example Blind SpA")
    monkeypatch.setattr(composition, "_fetch_issuer", lambda isin: {
        "name": None, "countries": [{"name": "United States", "pct": 100.0}],
        "sectors": [{"name": "Industrials", "pct": 100.0}],
        "top_holdings": [{"name": "Example Company", "pct": 2.0, "isin": None, "kind": "Equity"}],
        "holdings_count": 1000})
    monkeypatch.setattr(composition, "_fetch_equity_profile", lambda symbol: (
        {"name": "Example Blind SpA", "country": "Italy", "sector": "Utilities"} if symbol == "EXBL.MI" else None))


def _answer(text):
    return {"id": "gen-a", "object": "chat.completion", "created": 1, "model": "m",
            "choices": [{"index": 0, "message": {"role": "assistant", "content": text},
                         "finish_reason": "stop", "native_finish_reason": "end_turn"}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 10, "total_tokens": 110, "cost": 0.001}}


def _asks(*calls):
    return {"id": "gen-t", "object": "chat.completion", "created": 1, "model": "m",
            "choices": [{"index": 0, "finish_reason": "tool_calls", "native_finish_reason": "tool_use",
                         "message": {"role": "assistant", "content": None, "tool_calls": [
                             {"id": f"call_{i}", "type": "function",
                              "function": {"name": name, "arguments": json.dumps(arguments)}}
                             for i, (name, arguments) in enumerate(calls)]}}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 10, "total_tokens": 110, "cost": 0.001}}


SEARCH_ANSWER = {
    "id": "gen-s", "object": "chat.completion", "created": 1, "model": websearch.SEARCH_MODEL,
    "choices": [{"index": 0, "finish_reason": "stop", "message": {
        "role": "assistant", "content": "done", "annotations": [{"type": "url_citation", "url_citation": {
            "url": "https://example.org/index", "title": "How an example index works", "content": "An excerpt.",
            "start_index": 0, "end_index": 0}}]}}],
    "usage": {"prompt_tokens": 3000, "completion_tokens": 70, "total_tokens": 3070, "cost": 0.0073,
              "server_tool_use_details": {"web_search_requests": 1}},
}

EVERY_TOOL = (
    ("get_look_through", {}),
    ("catalogue_same_index", {"isin": FUND}),
    ("instrument_facts", {"symbol": "EXBL.MI"}),
    ("instrument_facts", {"isin": FUND}),
    ("weigh_change", {"sell": FUND, "sell_amount": 300, "buy_symbol": "EXBL.MI", "buy_amount": 300}),
    ("search_web", {"query": "example world index methodology"}),
)


def _role(body: dict) -> str:
    if body["model"] == websearch.SEARCH_MODEL:
        return "search"
    system = body["messages"][0]["content"]
    text = system if isinstance(system, str) else system[0]["text"]
    for role, prompt in (("analyst", advisor.PORTFOLIO_SYSTEM_PROMPT), ("confidant", chain.CONFIDANT_SYSTEM_PROMPT),
                         ("revision", chain.REVISION_SYSTEM_PROMPT), ("synthesis", chain.SYNTHESIS_SYSTEM_PROMPT)):
        if text.startswith(prompt):
            return role
    raise AssertionError(f"a request of no known role: {text[:80]}")


@pytest.fixture
def run(planted, monkeypatch):
    """One whole run, scripted at the transport: the analyst calls every tool
    and the web, then writes findings with nothing of either side in them; the
    confidant contests once; the revision looks something up again. Returns
    every request, with its role."""
    asked: list[tuple[str, dict]] = []
    confidant = iter(["It may not fit them.\n\nVERDICT: CONTESTED", "Fine.\n\nVERDICT: FITS"])

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        role = _role(body)
        asked.append((role, body))
        looked = any(m.get("role") == "tool" for m in body["messages"])
        if role == "search":
            return httpx.Response(200, json=SEARCH_ANSWER)
        if role == "analyst":
            return httpx.Response(200, json=_answer("The findings: the fund costs 0.2% a year.") if looked
                                  else _asks(*EVERY_TOOL))
        if role == "revision":
            return httpx.Response(200, json=_answer("HOLD: the figures stand.") if looked
                                  else _asks(("instrument_facts", {"isin": FUND})))
        if role == "confidant":
            return httpx.Response(200, json=_answer(next(confidant)))
        return httpx.Response(200, json=_answer("The verdict."))

    def client():
        return advisor.sdk().OpenAI(
            base_url=advisor.OPENROUTER_BASE_URL, api_key="invalid", max_retries=0,
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

    monkeypatch.setattr(advisor, "call_llm", REAL_CALL_LLM)
    monkeypatch.setattr(advisor, "call_round", REAL_CALL_ROUND)
    monkeypatch.setattr(advisor, "_client", client)
    monkeypatch.setenv("OPENROUTER_MODEL", "anthropic/claude-opus-5.5")
    with SessionLocal() as db:
        walk = chain.run_chain(db)
        while True:
            try:
                next(walk)
            except StopIteration as done:
                assert [s.role for s in done.value.steps] == [
                    "analyst", "confidant", "revision", "confidant", "synthesis"]
                break
    return asked


def _bodies(asked, *roles):
    return [json.dumps(body, ensure_ascii=False) for role, body in asked if role in roles]


def test_nothing_of_the_person_reaches_the_analyst_its_tools_or_the_web(run):
    analyst = _bodies(run, "analyst", "revision")
    assert len(analyst) == 4
    for body in analyst + _bodies(run, "search"):
        for marker in PERSON:
            assert marker not in body, f"{marker} reached the analysis"


def test_the_tax_country_is_the_one_profile_field_the_analyst_reads(run):
    """Every other answer of the Profile page stays out, which the test above
    holds. The base currency set there is the unit every figure is written in,
    not an answer about the person."""
    analyst = _bodies(run, "analyst", "revision")
    assert len(analyst) == 4
    for body in analyst:
        assert f"({TAX_COUNTRY}) are capital gains 26% / dividend withholding 12.5%" in body


def test_every_tool_answered_the_analyst_from_the_records(run):
    """The tools did run, and answered: their answers are what the check above
    read, so a refusal would have proved nothing."""
    second = next(body for role, body in run if role == "analyst"
                  and any(m["role"] == "tool" for m in body["messages"]))
    outcomes = [json.loads(m["content"]) for m in second["messages"] if m["role"] == "tool"]
    assert len(outcomes) == len(EVERY_TOOL)
    assert all(outcome["ok"] for outcome in outcomes), outcomes
    # The records reached it: the asymmetry is about the person, not the figures.
    assert RECORDS[1] in json.dumps(second)


def test_no_figure_or_name_of_the_records_reaches_the_confidant(run):
    confidant = _bodies(run, "confidant")
    assert len(confidant) == 2
    for body in confidant:
        for marker in RECORDS:
            assert marker not in body, f"{marker} reached the confidant"
        # It does read the person: the markers were planted where it looks.
        assert PERSON[0] in body and PERSON[2] in body


def test_the_confidant_and_the_synthesis_are_offered_no_tool(run):
    for role, body in run:
        if role in ("confidant", "synthesis"):
            assert "tools" not in body, role
        if role in ("analyst", "revision"):
            assert [d["function"]["name"] for d in body.get("tools", [])] in (
                ["get_look_through", "catalogue_same_index", "instrument_facts", "weigh_change", "search_web"],
                [],
            ), role


def test_the_analysts_own_context_carries_nothing_of_the_person(planted):
    """Guard: the context was blind before the tools (brief Z), and stays so."""
    with SessionLocal() as db:
        context = advisor.build_portfolio_context(db)
        person = advisor.build_person_context(db)
    for marker in PERSON:
        assert marker not in context, marker
        assert marker in person, marker
    for marker in RECORDS:
        assert marker not in person, marker
        assert marker in context, marker
    assert TAX_COUNTRY in context
