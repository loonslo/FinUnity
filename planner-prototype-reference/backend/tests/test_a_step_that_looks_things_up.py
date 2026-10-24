"""A step of the analysis that looks things up, in rounds: capped, costed, cached.

Brief AR (2026-10-12). The analyst's step and its revision ask in rounds
(`chain._looked_up`): each round may call the analysis's tools and the web, the
next reads what they answered, and the step ends on the first round that
answers in words. At most four rounds, the last offered no tool and the one
before told so; at most three web searches; every round names the step's
ceiling and is asked once more on a 500, 502 or 503, never on a cut; the
step's cost is every round's and every search's, unknown when any is; for
Anthropic's models every round that offers tools marks the system prompt, the
step's question and the end of its request, but the last of them, which marks
where the round before it ended instead; the round with none carries no
marker; every round names the analysis's own session.

Through the real `call_round`, `call_llm` and web search, with only the
transport inside the openai client faked: what is under test is the request
each sends and how the answers are read. Every name, ISIN and figure is
invented."""

from __future__ import annotations

import json

import httpx
import pandas as pd
import pytest

from app import advisor, catalogue, chain, models, prices, websearch
from app.database import SessionLocal

REAL_CALL_LLM, REAL_CALL_ROUND = advisor.call_llm, advisor.call_round
OPUS, OTHER = "anthropic/claude-opus-5.5", "qwen/qwen3.8-max-0902"
FUND = "IE00EXSTEP01"
FITS = "Nothing here needs answering.\n\nVERDICT: FITS"


def _usage(cost, prompt=1000, cached=0):
    usage = {"prompt_tokens": prompt, "completion_tokens": 20, "total_tokens": prompt + 20,
             "prompt_tokens_details": {"cached_tokens": cached}}
    if cost is not None:
        usage["cost"] = cost
    return usage


def _answer(text, cost=0.01, finish="stop", native="end_turn"):
    return 200, {
        "id": "gen-answer", "object": "chat.completion", "created": 1, "model": "m",
        "choices": [{"index": 0, "message": {"role": "assistant", "content": text},
                     "finish_reason": finish, "native_finish_reason": native}],
        "usage": _usage(cost),
    }


def _asks(*calls, cost=0.01):
    """A round asking for tools: (name, arguments) each."""
    return 200, {
        "id": "gen-asks", "object": "chat.completion", "created": 1, "model": "m",
        "choices": [{"index": 0, "finish_reason": "tool_calls", "native_finish_reason": "tool_use",
                     "message": {"role": "assistant", "content": None, "tool_calls": [
                         {"id": f"call_{i}", "type": "function",
                          "function": {"name": name, "arguments": json.dumps(arguments)}}
                         for i, (name, arguments) in enumerate(calls)]}}],
        "usage": _usage(cost),
    }


def _found(cost=0.0073):
    """One search's answer, as the web search's own request gets it back."""
    usage = {"prompt_tokens": 3000, "completion_tokens": 70, "total_tokens": 3070,
             "server_tool_use_details": {"web_search_requests": 1}}
    if cost is not None:
        usage["cost"] = cost
    return 200, {
        "id": "gen-search", "object": "chat.completion", "created": 1, "model": websearch.SEARCH_MODEL,
        "choices": [{"index": 0, "finish_reason": "stop", "message": {
            "role": "assistant", "content": "done", "annotations": [{"type": "url_citation", "url_citation": {
                "url": "https://example.org/page", "title": "An example page", "content": "An excerpt.",
                "start_index": 0, "end_index": 0}}]}}],
        "usage": usage,
    }


def _role(body: dict) -> str:
    if body["model"] == websearch.SEARCH_MODEL:
        return "search"
    system = body["messages"][0]["content"]
    text = system if isinstance(system, str) else system[0]["text"]
    for role, prompt in (("analyst", advisor.PORTFOLIO_SYSTEM_PROMPT), ("confidant", chain.CONFIDANT_SYSTEM_PROMPT),
                         ("revision", chain.REVISION_SYSTEM_PROMPT), ("synthesis", chain.SYNTHESIS_SYSTEM_PROMPT)):
        if text.startswith(prompt):
            return role
    return "unknown"


@pytest.fixture
def openrouter(monkeypatch):
    """OpenRouter answering each request with the next entry its role's script
    holds (`analyst`, `confidant`, `revision`, `synthesis`, `search`), and a 500
    past it. Every request body is kept, with its role."""

    class Fake:
        script: dict[str, list] = {}
        asked: list[tuple[str, dict]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        role = _role(body)
        Fake.asked.append((role, body))
        queue = Fake.script.get(role) or []
        if not queue:
            return httpx.Response(500, json={"error": {"code": 500, "message": "past the script"}})
        status, answer = queue.pop(0)
        return httpx.Response(status, json=answer)

    def client():
        return advisor.sdk().OpenAI(
            base_url=advisor.OPENROUTER_BASE_URL, api_key="invalid", max_retries=0,
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

    monkeypatch.setattr(advisor, "call_llm", REAL_CALL_LLM)
    monkeypatch.setattr(advisor, "call_round", REAL_CALL_ROUND)
    monkeypatch.setattr(advisor, "_client", client)
    monkeypatch.setenv("OPENROUTER_MODEL", OPUS)
    for var in ("OPENROUTER_ANALYST_MODEL", "OPENROUTER_CONFIDANT_MODEL"):
        monkeypatch.delenv(var, raising=False)
    return Fake


@pytest.fixture
def registry(client, monkeypatch):
    """One invented fund in the catalogue, and Yahoo answering for one invented
    listing, so the analyst's tools have something to read."""
    monkeypatch.setattr(catalogue, "_fetch_overview", lambda: pd.DataFrame(
        [{"name": "Alfa Step World UCITS ETF Acc", "ticker": None, "dividends": "Accumulating", "ter": 0.2,
          "size": 900, "replication": "Full replication", "domicile_country": "Ireland", "currency": "USD",
          "number_of_holdings": 1000, "hedged": False}],
        index=pd.Index([FUND], name="isin")))
    assert client.post("/api/instruments/catalogue/refresh").status_code == 200
    monkeypatch.setattr(prices, "_fetch_lookup", lambda query, count: [
        {"symbol": "EXST.MI", "name": "Example Step SpA", "quote_type": "EQUITY", "exchange": "MIL", "price": 9.0}])


def _run(db):
    walk = chain.run_chain(db)
    said = []
    while True:
        try:
            said.append(next(walk))
        except StopIteration as done:
            return said, done.value


def _of(fake, role):
    return [body for r, body in fake.asked if r == role]


def test_a_step_asks_in_rounds_and_its_cost_is_every_round(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("instrument_facts", {"isin": FUND}), ("catalogue_same_index", {"isin": FUND}),
                          cost=0.02),
                    _answer("The findings.", cost=0.05)],
        "confidant": [_answer(FITS, cost=0.01)],
        "synthesis": [_answer("The verdict.", cost=0.03)],
    }
    with SessionLocal() as db:
        said, run = _run(db)
        assert [s.role for s in run.steps] == ["analyst", "confidant", "synthesis"]
        assert run.steps[0].cost == pytest.approx(0.07)
        assert run.steps[0].output == "The findings."
    assert said[0].looked_up == "looked up 2 things in 1 round"
    first, second = _of(openrouter, "analyst")
    assert first["tools"] == second["tools"], "the tools change inside the cached prefix"
    assert [d["function"]["name"] for d in first["tools"]] == [
        "get_look_through", "catalogue_same_index", "instrument_facts", "weigh_change", "search_web"]
    assert [m["role"] for m in second["messages"]] == ["system", "user", "assistant", "tool", "tool"]
    facts = json.loads(second["messages"][3]["content"])
    assert facts["ok"] and facts["result"]["ter_pct"] == 0.2


def test_every_round_names_the_step_ceiling(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _answer("The findings.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _run(db)
    assert {body["max_tokens"] for _, body in openrouter.asked} == {advisor.STEP_MAX_TOKENS}


def test_an_anthropic_step_keeps_its_prefix_and_names_its_session(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _answer("The findings.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _run(db)
    for body in _of(openrouter, "analyst"):
        assert body["messages"][0]["content"][0]["cache_control"] == {"type": "ephemeral"}
        assert body["messages"][1]["content"][0]["cache_control"] == {"type": "ephemeral"}
        assert body["cache_control"] == {"type": "ephemeral"}
        assert body["session_id"] == advisor.ANALYSIS_SESSION_ID == "aurelio-analysis"
    # The steps that look nothing up ask for nothing, as before.
    for role in ("confidant", "synthesis"):
        (body,) = _of(openrouter, role)
        assert "cache_control" not in json.dumps(body) and "session_id" not in body


def test_another_models_step_carries_no_marker_no_session_and_no_web(client, registry, openrouter, monkeypatch):
    monkeypatch.setenv("OPENROUTER_MODEL", OTHER)
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _answer("The findings.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _run(db)
    for body in _of(openrouter, "analyst"):
        assert "cache_control" not in json.dumps(body) and "session_id" not in body
        assert "search_web" not in [d["function"]["name"] for d in body["tools"]]
        assert chain.WEB_RULES not in body["messages"][0]["content"]


def test_the_round_before_the_last_is_told_and_the_last_offers_no_tool(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _asks(("get_look_through", {})),
                    _asks(("get_look_through", {})), _answer("The findings, from what I had.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        said, run = _run(db)
        assert run.steps[0].output == "The findings, from what I had."
    rounds = _of(openrouter, "analyst")
    assert len(rounds) == chain.ROUNDS_PER_STEP == 4
    assert all("tools" in body for body in rounds[:3]) and "tools" not in rounds[3]
    assert rounds[2]["messages"][-1] == {"role": "user", "content": chain.LAST_TOOL_ROUND_NOTICE}
    assert all(chain.LAST_TOOL_ROUND_NOTICE not in json.dumps(body) for body in (rounds[0], rounds[1], rounds[3]))
    assert said[0].looked_up == "looked up 3 things in 3 rounds"


def test_a_round_keeps_only_what_a_round_after_it_reads(client, registry, openrouter):
    # Rounds 1 and 2 keep their whole request, which the next sends again and
    # reads. Round 3, the last with tools, reads up to where round 2 ended and
    # keeps nothing new, and round 4 keeps nothing: without the tools its
    # request no longer begins as theirs did, so it reads none of it (brief
    # AR's P1, 2026-10-12, and its check, 2026-10-13).
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _asks(("get_look_through", {})),
                    _asks(("get_look_through", {})), _answer("The findings, from what I had.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _run(db)
    first, second, third, fourth = _of(openrouter, "analyst")
    for body in (first, second, third):
        assert body["messages"][0]["content"][0]["cache_control"] == {"type": "ephemeral"}
        assert body["messages"][1]["content"][0]["cache_control"] == {"type": "ephemeral"}
    assert first["cache_control"] == second["cache_control"] == {"type": "ephemeral"}
    assert "cache_control" not in third
    end = len(second["messages"]) - 1  # round 2's last message, a tool's answer
    assert second["messages"][end]["role"] == "tool"
    assert third["messages"][end]["content"] == [
        {"type": "text", "text": second["messages"][end]["content"], "cache_control": {"type": "ephemeral"}}]
    assert "cache_control" not in json.dumps(third["messages"][2:end] + third["messages"][end + 1:])
    assert "tools" not in fourth and "cache_control" not in json.dumps(fourth)
    assert {body["session_id"] for body in (first, second, third, fourth)} == {advisor.ANALYSIS_SESSION_ID}


def test_another_model_reads_the_notice_in_its_system_prompt(client, registry, openrouter, monkeypatch):
    monkeypatch.setenv("OPENROUTER_MODEL", OTHER)
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})), _asks(("get_look_through", {})),
                    _asks(("get_look_through", {})), _answer("The findings.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _run(db)
    third = _of(openrouter, "analyst")[2]
    assert third["messages"][0]["content"].endswith("\n\n" + chain.LAST_TOOL_ROUND_NOTICE)


def test_a_steps_searches_are_capped_and_counted_in_its_cost(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(*[("search_web", {"query": f"example index methodology {n}"}) for n in range(4)],
                          cost=0.02),
                    _answer("The findings, with [An example page](https://example.org/page).", cost=0.04)],
        "search": [_found(), _found(), _found()],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        said, run = _run(db)
        assert run.steps[0].cost == pytest.approx(0.02 + 0.04 + 3 * 0.0073)
    searched = _of(openrouter, "search")
    assert len(searched) == chain.SEARCHES_PER_STEP == 3
    assert [body["messages"][-1]["content"] for body in searched] == [
        "example index methodology 0", "example index methodology 1", "example index methodology 2"]
    results = [json.loads(m["content"]) for m in _of(openrouter, "analyst")[1]["messages"] if m["role"] == "tool"]
    assert [r["ok"] for r in results] == [True, True, True, False]
    assert results[3]["error"] == chain.SEARCHES_SPENT
    assert said[0].looked_up == "looked up 4 things in 1 round, 3 of them web searches"


def test_a_search_whose_cost_was_not_said_leaves_the_steps_unknown(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("search_web", {"query": "example index methodology"})), _answer("The findings.")],
        "search": [_found(cost=None)],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _, run = _run(db)
        assert run.steps[0].cost is None


def test_a_round_turned_away_with_a_503_is_asked_once_more_and_says_so(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {})),
                    (503, {"error": {"code": 503, "message": "No provider available"}}),
                    _answer("The findings.")],
        "confidant": [_answer(FITS)],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        said, run = _run(db)
        assert run.steps[0].output == "The findings."
    assert len(_of(openrouter, "analyst")) == 3
    assert said[0].asked_again == "asked again after OpenRouter's 503, which it says it does not bill"


def test_a_round_cut_at_the_ceiling_stops_the_run_with_what_the_step_had_cost(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_asks(("get_look_through", {}), cost=0.02),
                    _answer("The top four", cost=0.33, finish="length", native="max_tokens"),
                    _answer("never asked")],
    }
    with SessionLocal() as db:
        with pytest.raises(advisor.AdvisorError) as stopped:
            _run(db)
        assert db.query(models.ChainRun).count() == 0
    assert len(_of(openrouter, "analyst")) == 2, "a cut round was asked again"
    said = str(stopped.value)
    assert said.startswith(
        "The model stopped before finishing its answer: it reached its length limit, the 16,384 "
        "tokens the app allows a step, its reasoning included")
    assert 'The analysis stopped at step 1, "The numbers, on their own merits"' in said
    assert "Until it stopped it had cost $0.35, as OpenRouter reported it." in said


def test_the_confidant_and_the_synthesis_are_offered_no_tool(client, registry, openrouter):
    openrouter.script = {
        "analyst": [_answer("The findings.")],
        "confidant": [_answer("It fails them.\n\nVERDICT: CONTESTED"), _answer(FITS)],
        "revision": [_asks(("get_look_through", {})), _answer("HOLD.")],
        "synthesis": [_answer("The verdict.")],
    }
    with SessionLocal() as db:
        _, run = _run(db)
        assert [s.role for s in run.steps] == ["analyst", "confidant", "revision", "confidant", "synthesis"]
    for role in ("confidant", "synthesis"):
        assert all("tools" not in body for body in _of(openrouter, role))
    assert all("tools" in body for body in _of(openrouter, "revision"))
