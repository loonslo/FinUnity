"""What an amount stands on, computed from the records.

Brief AQ (2026-10-12). Asked "is it worth buying for me, and how much?", the
chat answered the second half with its own arithmetic. Measured in the brief's
P0 (Opus 5.5 through the app's own loop, on a test database with an invented
profile and goal): two answers of three gave a monthly ceiling, the surplus
less a goal's monthly need less the PAC, and the goal's need came out as 631
in one answer and 650 in the next, from the same records; no answer said a
reserve in months; the PAC was subtracted because the model remembered to,
where the reader's own test round had seen it forgotten. A figure for a
purchase, said to a person, is the most delicate line of the product, so what
an amount stands on is computed here, once, and read by everything that says
one: the chat's `size_purchase`, a suggestion card that carries an amount, and
the picture's line of what is left each month.

What it computes, each figure with where it comes from:

- The cash on the accounts today, the cash register's own projection. An
  account below zero is named, and so is one with no balance on record, which
  the register counts as nothing.
- A reserve of essential expenses: the months the reader keeps aside, from
  their answer to the Profile's question (RESERVE_QUESTION), or else
  DEFAULT_RESERVE_MONTHS, said as the app's default and never as theirs, times
  the essential expenses in force. An expense with no nature counts as
  essential, since a reserve too large errs on the safe side (the reader's
  choice, 2026-10-12). No expense in force sizes no reserve at all, which is
  said, never read as a reserve of nothing.
- The cash above that reserve: the most a sum put in now may be.
- What is left each month: the income less the expenses in force, less what
  the reader has already committed each month, every PAC in force at its
  frequency and every goal's recorded monthly contribution while its date is
  ahead (the reader's choice: a contribution is a commitment, as a PAC is). The
  most a monthly amount may be.
- Each goal with a target: the months to its date, the gap, what it needs a
  month with no growth, beside the contribution recorded for it, what it lacks
  of its need, and the return that contribution requires (`planning`). Said
  beside the figures above and never subtracted from them: what is taken out
  each month is what the reader recorded, and what a goal would need is theirs
  to weigh. What would be left each month were every goal given its need is
  computed too, so that no answer works it out (brief AQ's P1: two answers of
  four did, 631.08 less 300 and 2,178 less that, right and the model's own).
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app import analytics, crud, dated, fx, planning

# The months of essential expenses kept aside when the reader has not said how
# many: the reader's choice of a default (2026-10-12), said as the app's
# wherever it is used, so it is never read as theirs.
DEFAULT_RESERVE_MONTHS = 6

# The Profile question that holds the reader's own months.
RESERVE_QUESTION = "reserve_months"


@dataclass(frozen=True)
class Reserve:
    """The reserve of essential expenses, and where its months come from.

    `amount` is None when no expense is in force: nothing says what a month
    costs, so no reserve can be sized."""

    months: float
    from_profile: bool
    essential: float
    unclassified: float
    amount: float | None

    @property
    def monthly(self) -> float:
        """What a month of the reserve is: the essential expenses, and those
        with no nature."""
        return self.essential + self.unclassified


@dataclass(frozen=True)
class Commitment:
    """Money the reader already puts somewhere every month: a PAC, or a goal's
    recorded contribution. `monthly` is in the base currency; `recorded` is
    the figure as the record states it."""

    kind: str  # "pac" | "goal"
    name: str
    monthly: float
    recorded: str
    starts: str | None = None


@dataclass(frozen=True)
class GoalNeed:
    """One goal with a target, measured against today. `months_left` is None
    once its date has passed, and so is what it needs a month."""

    name: str
    currency: str
    target: float
    by: str
    current: float
    recorded_monthly: float
    months_left: float | None
    gap: float
    monthly_need: float | None
    required_return: float | None
    assessment: str
    # What the monthly need asks beyond what is recorded for it, never below
    # zero; None once the date has passed.
    shortfall: float | None = None


@dataclass(frozen=True)
class Room:
    """Everything an amount stands on, on the day `on`, in the base currency."""

    on: str
    base: str
    cash: float
    below_zero: tuple[tuple[str, float], ...]
    no_balance: tuple[str, ...]
    reserve: Reserve
    cash_above_reserve: float | None
    income: float | None
    expenses: float | None
    left: float | None
    commitments: tuple[Commitment, ...]
    starting_later: tuple[Commitment, ...]
    left_after_commitments: float | None
    goals: tuple[GoalNeed, ...]
    investments: float
    financial_wealth: float
    # What is left each month were every goal given its need: what is left
    # after the commitments, less what each goal lacks; None as that is.
    left_if_goals_met: float | None = None


def reserve_months(db: Session) -> tuple[float, bool]:
    """The months of the reserve, and whether they are the reader's own: their
    answer to RESERVE_QUESTION when it reads as a number, else the default."""
    for answer in crud.get_survey_responses(db):
        if answer.question_key != RESERVE_QUESTION:
            continue
        try:
            months = float((answer.answer or "").strip())
        except ValueError:
            break
        if months >= 0:
            return months, True
        break
    return float(DEFAULT_RESERVE_MONTHS), False


def reserve(db: Session, flows: dict) -> Reserve:
    """The reserve, from the flows in force (`analytics.compute_flows_in_force`)."""
    months, own = reserve_months(db)
    essential = flows["essential_expenses"]
    unclassified = flows["monthly_expenses"] - essential - flows["discretionary_expenses"]
    if unclassified < 0.005:
        unclassified = 0.0
    amount = None
    if flows["expenses_in_force"]:
        amount = round(months * (essential + unclassified), 2)
    return Reserve(
        months=months,
        from_profile=own,
        essential=round(essential, 2),
        unclassified=round(unclassified, 2),
        amount=amount,
    )


_EVERY = {"monthly": "monthly", "quarterly": "quarterly", "semiannual": "every six months",
          "annual": "yearly"}


def commitments(
    db: Session, on: str, conv: fx.Converter | None = None
) -> tuple[tuple[Commitment, ...], tuple[Commitment, ...]]:
    """What the reader already puts away every month, as of `on`: each PAC in
    force at its monthly rate and each goal's recorded contribution while its
    date is ahead; then, apart and never subtracted, the PACs that start later.

    A PAC is in force from its first buy to its last; one with no first buy has
    no schedule (`pac.plan_occurrences`) and takes nothing. A month of a
    quarterly plan is a third of its amount, as a quarterly expense's is
    (`analytics.STEP_MONTHS`)."""
    conv = conv or fx.Converter(db)
    running: list[Commitment] = []
    later: list[Commitment] = []
    for plan in crud.get_accumulation_plans(db):
        if not plan.start_date or (plan.end_date and plan.end_date < on):
            continue
        step = analytics.STEP_MONTHS.get(plan.frequency or "monthly", 1)
        entry = Commitment(
            kind="pac",
            name=plan.name,
            monthly=round(conv.to_base_or_as_stored(plan.amount, plan.currency) / step, 2),
            recorded=f"{plan.amount:.2f} {plan.currency} {_EVERY.get(plan.frequency or 'monthly', plan.frequency)}",
            starts=plan.start_date if plan.start_date > on else None,
        )
        (later if entry.starts else running).append(entry)
    for goal in crud.get_goals(db):
        contribution = goal.monthly_contribution or 0.0
        if contribution <= 0 or not goal.target_date or goal.target_date <= on:
            continue
        running.append(
            Commitment(
                kind="goal",
                name=goal.name,
                monthly=round(conv.to_base_or_as_stored(contribution, goal.currency), 2),
                recorded=f"{contribution:.2f} {goal.currency} a month",
            )
        )
    return tuple(running), tuple(later)


def left_after(flows: dict, committed: tuple[Commitment, ...]) -> float | None:
    """What is left each month once the commitments are taken out, or None
    when what is left before them cannot be said (no income or no expense in
    force: the picture's own rule)."""
    if not (flows["incomes_in_force"] and flows["expenses_in_force"]):
        return None
    return round(flows["monthly_net"] - sum(c.monthly for c in committed), 2)


def goal_needs(db: Session, on: str) -> tuple[GoalNeed, ...]:
    """Each goal with a target amount and a date, against the day `on`.

    The months are the days to its date over a year's 365.25, times twelve,
    the reckoning `planning` gives the return in; what it needs a month is its
    gap over them, with no growth. Its return is the one its RECORDED
    contribution requires, the figure the picture already prints."""
    day = datetime.date.fromisoformat(on)
    needs = []
    for goal in crud.get_goals(db):
        if goal.type != "target_amount" or not goal.target_amount or not goal.target_date:
            continue
        current = goal.current_amount or 0.0
        recorded = goal.monthly_contribution or 0.0
        gap = max(goal.target_amount - current, 0.0)
        years = (datetime.date.fromisoformat(goal.target_date) - day).days / 365.25
        if years <= 0:
            needs.append(GoalNeed(goal.name, goal.currency, goal.target_amount, goal.target_date,
                                  current, recorded, None, round(gap, 2), None, None,
                                  "Its date has passed."))
            continue
        months = years * 12
        rate = planning.required_annual_return(current, recorded, goal.target_amount, years)
        need = round(gap / months, 2)
        needs.append(
            GoalNeed(
                name=goal.name,
                currency=goal.currency,
                target=goal.target_amount,
                by=goal.target_date,
                current=current,
                recorded_monthly=recorded,
                months_left=round(months, 1),
                gap=round(gap, 2),
                monthly_need=need,
                required_return=rate,
                assessment=planning.assess(rate),
                shortfall=round(max(need - recorded, 0.0), 2),
            )
        )
    return tuple(needs)


def room(db: Session) -> Room:
    """Everything an amount stands on, today."""
    on = dated.today()
    conv = fx.Converter(db)
    flows = analytics.compute_flows_in_force(db, on)
    registers = analytics.compute_all_cash_positions(
        db, as_of=datetime.date.fromisoformat(on), conv=conv
    )
    cash = round(sum(r["projected"] for r in registers), 2)
    summary = analytics.compute_summary(db, as_of=datetime.date.fromisoformat(on))
    held = reserve(db, flows)
    committed, later = commitments(db, on, conv)
    reckoned = flows["incomes_in_force"] and flows["expenses_in_force"]
    left = left_after(flows, committed)
    goals = goal_needs(db, on)
    lacking = sum(
        conv.to_base_or_as_stored(g.shortfall, g.currency) for g in goals if g.shortfall
    )
    return Room(
        on=on,
        base=conv.base,
        cash=cash,
        below_zero=tuple(
            (r["institution_name"], round(r["projected"], 2))
            for r in registers
            if r["projected"] < -0.005
        ),
        no_balance=tuple(r["institution_name"] for r in registers if r["anchor_date"] is None),
        reserve=held,
        cash_above_reserve=None if held.amount is None else round(cash - held.amount, 2),
        income=round(flows["monthly_income"], 2) if flows["incomes_in_force"] else None,
        expenses=round(flows["monthly_expenses"], 2) if flows["expenses_in_force"] else None,
        left=round(flows["monthly_net"], 2) if reckoned else None,
        commitments=committed,
        starting_later=later,
        left_after_commitments=left,
        goals=goals,
        investments=round(summary["investments_total"], 2),
        financial_wealth=round(summary["financial_total"], 2),
        left_if_goals_met=None if left is None else round(left - lacking, 2),
    )


def as_dict(found: Room) -> dict:
    """The room as the chat's tool returns it: every figure with what it is."""
    held = found.reserve
    return {
        "on": found.on,
        "currency": found.base,
        "cash": {
            "on_the_accounts": found.cash,
            "accounts_below_zero": [{"account": a, "cash": c} for a, c in found.below_zero],
            "accounts_with_no_balance": list(found.no_balance),
        },
        "reserve": {
            "months": held.months,
            "months_from": (
                "their answer in the Profile"
                if held.from_profile
                else f"the app's default of {DEFAULT_RESERVE_MONTHS} months: their profile does not say"
            ),
            "essential_expenses_a_month": held.essential,
            "expenses_with_no_nature_a_month": held.unclassified,
            "amount": held.amount,
        },
        "cash_above_reserve": found.cash_above_reserve,
        "each_month": {
            "income": found.income,
            "expenses": found.expenses,
            "left": found.left,
            "committed": [
                {"what": c.kind, "name": c.name, "a_month": c.monthly, "as_recorded": c.recorded}
                for c in found.commitments
            ],
            "starting_later_not_counted": [
                {"what": c.kind, "name": c.name, "a_month": c.monthly, "as_recorded": c.recorded,
                 "from": c.starts}
                for c in found.starting_later
            ],
            "left_after_commitments": found.left_after_commitments,
            "left_if_each_goal_gets_its_need": found.left_if_goals_met,
        },
        "goals": [
            {
                "name": g.name,
                "currency": g.currency,
                "target": g.target,
                "by": g.by,
                "set_aside": g.current,
                "recorded_a_month": g.recorded_monthly,
                "months_left": g.months_left,
                "gap": g.gap,
                "needs_a_month_with_no_growth": g.monthly_need,
                "lacks_a_month": g.shortfall,
                "return_the_recorded_contribution_requires": (
                    None if g.required_return is None else round(g.required_return * 100, 1)
                ),
                "assessment": g.assessment,
            }
            for g in found.goals
        ],
        "investments": found.investments,
        "financial_wealth": found.financial_wealth,
    }
