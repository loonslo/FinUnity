"""The analysis card says what a run may look up, and what that does to its cost.

Brief AR (2026-10-12). The card quoted "3 model calls, up to 6", the last
run's time and its cost. The analyst's steps now ask in rounds and may search
the web, so a run is still three to six steps but more requests than that, and
what it costs moves with what its analyst looks up. No column of a step says
whether it looked anything up, and none was added (the reader's choice), so
the card says the bounds and keeps quoting the last run as measured."""

from __future__ import annotations

from app import advisor, chain, tools
from app.database import SessionLocal


def _consequence(monkeypatch, model: str) -> str:
    monkeypatch.setenv("OPENROUTER_MODEL", model)
    for var in ("OPENROUTER_ANALYST_MODEL", "OPENROUTER_CONFIDANT_MODEL"):
        monkeypatch.delenv(var, raising=False)
    with SessionLocal() as db:
        card = tools.answer(db, advisor.ToolCall(id="call_1", name="run_analysis", arguments="{}"))
    return card["card"]["consequence"]


def test_the_card_counts_steps_and_says_what_they_may_look_up(client, monkeypatch):
    said = _consequence(monkeypatch, "anthropic/claude-opus-5.5")
    assert said.startswith(
        f"{chain.MIN_STEPS} steps, up to {chain.MAX_STEPS} if the disagreement is real, on "
        "anthropic/claude-opus-5.5. The analyst's steps may look things up first, in up to 4 "
        "requests and 3 web searches a step, so what a run costs moves with what they look up. "
        "Nothing has run yet, so there is no measured time or cost to quote."
    ), said
    assert "model calls" not in said


def test_a_model_offered_no_web_is_told_of_no_search(client, monkeypatch):
    said = _consequence(monkeypatch, "qwen/qwen3.8-max-0902")
    assert "in up to 4 requests a step, so what a run costs moves with what they look up." in said
    assert "web search" not in said
