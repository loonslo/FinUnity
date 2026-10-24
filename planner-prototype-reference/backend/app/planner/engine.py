"""Pure Decimal calculations. No model, database, network or financial writes."""

from __future__ import annotations

import calendar
from datetime import date
from decimal import Decimal, localcontext, ROUND_HALF_UP, ROUND_CEILING

from app.planner.schemas import Household, Occurrence, PlanRequest

ZERO = Decimal("0")


def money(value: Decimal | None) -> str | None:
    return (
        None
        if value is None
        else str(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
    )


def month_after(day: date, count: int) -> date:
    number = day.year * 12 + day.month - 1 + count
    year, month = divmod(number, 12)
    return date(year, month + 1, min(day.day, calendar.monthrange(year, month + 1)[1]))


def calculate(
    household: Household, events: list[Occurrence], request: PlanRequest, as_of: date
) -> dict:
    with localcontext() as ctx:
        ctx.prec = 96
        return _calculate(household, events, request, as_of)


def _calculate(
    h: Household, events: list[Occurrence], request: PlanRequest, as_of: date
) -> dict:
    errors: list[str] = []
    missing: list[str] = []
    warnings: list[str] = []
    rates = {r.currency: r.to_base for r in h.rates}
    rates[h.base_currency] = Decimal(1)
    assets = {a.id: a for a in h.assets}
    balances = {a.id: a.amount if a.quality != "unknown" else None for a in h.assets}
    debts = {d.id: d.balance for d in h.debts}
    allocations = list(
        h.allocations if request.allocations is None else request.allocations
    )
    goal_ids = {g.id for g in h.goals}
    if set(request.monthly) - goal_ids:
        errors.append("月度投入引用了不存在的目标")
    for flag, label in (
        (h.assets_complete, "资产范围"),
        (h.income_complete, "月收入"),
        (h.expenses_complete, "月支出"),
        (h.debts_complete, "负债与最低还款"),
    ):
        if not flag:
            missing.append(f"请确认{label}已完整填写")
    if h.as_of > as_of:
        missing.append("建档日期晚于测算日期")

    def convert(amount: Decimal | None, currency: str, label: str) -> Decimal | None:
        if amount is None:
            missing.append(f"{label}的金额未知")
            return None
        if currency not in rates:
            missing.append(f"缺少 {currency}/{h.base_currency} 汇率：{label}")
            return None
        return amount * rates[currency]

    # Every confirmed occurrence is replayed only after each account's own anchor.
    for e in sorted(events, key=lambda item: item.date):
        if e.state != "confirmed" or e.date > as_of or e.amount is None:
            continue
        a = assets.get(e.asset_id)
        if a is None:
            errors.append("实际记录引用了已移除的账户")
            continue
        if e.date > a.observed_on and balances[a.id] is not None:
            increase = e.kind == "income" or (
                e.kind == "adjustment" and e.adjustment_direction == "increase"
            )
            balances[a.id] += e.amount if increase else -e.amount
        if e.kind == "transfer":
            target = assets.get(e.target_asset_id)
            if target is None:
                errors.append("实际转账引用了已移除的转入账户")
                continue
            arrival = e.target_amount if e.target_amount is not None else e.amount
            if e.date > target.observed_on and balances[target.id] is not None:
                balances[target.id] += arrival
            if e.goal_id and e.date > h.as_of:
                from app.planner.schemas import Allocation

                if e.allocation_mode == "move_existing":
                    remaining_move = e.amount
                    reduced = []
                    for allocation in allocations:
                        if (
                            allocation.asset_id == e.asset_id
                            and allocation.goal_id == e.goal_id
                        ):
                            used = min(allocation.amount, remaining_move)
                            remaining_move -= used
                            allocation = allocation.model_copy(
                                update={"amount": allocation.amount - used}
                            )
                        reduced.append(allocation)
                    allocations = reduced
                    if remaining_move > 0:
                        errors.append("转移金额超过该账户为目标分配的资金")
                allocations.append(
                    Allocation(asset_id=target.id, goal_id=e.goal_id, amount=arrival)
                )
        if (
            e.kind == "debt_payment"
            and e.date > h.as_of
            and debts.get(e.debt_id) is not None
        ):
            debts[e.debt_id] -= e.principal or ZERO

    total = ZERO
    known = True
    cash = ZERO
    liquid = ZERO
    asset_rows = []
    for a in h.assets:
        value = convert(balances[a.id], a.currency, a.name)
        if value is None:
            known = False
        else:
            total += value
            if value < 0:
                errors.append(f"{a.name}余额不足")
            if a.liquidity == "available" and a.kind in ("cash", "investment"):
                liquid += value
            if a.kind == "cash" and a.liquidity == "available":
                cash += value
        if a.quality == "estimated":
            warnings.append(f"{a.name}使用估算金额")
        asset_rows.append(
            {
                **a.model_dump(mode="json"),
                "balance": None if balances[a.id] is None else str(balances[a.id]),
                "value_base": money(value),
            }
        )
    minimum = ZERO
    debt_total = ZERO
    for d in h.debts:
        value = convert(debts[d.id], d.currency, d.name)
        if value is None:
            known = False
        else:
            debt_total += value
            if value < 0:
                errors.append(f"{d.name}还款本金超过余额")
        payment = convert(d.minimum_payment, d.currency, f"{d.name}最低还款")
        if payment is not None and not d.payment_in_expenses:
            minimum += payment
    if not h.goals:
        missing.append("请添加至少一个有金额与期限的目标")
    if not h.assets_complete:
        known = False
    if h.income is None:
        missing.append("月收入金额未知")
    if h.expenses is None:
        missing.append("月支出金额未知")
    if h.essential_expenses is None:
        missing.append("必要支出未知，无法计算应急预留")
    reserve = (
        None
        if h.essential_expenses is None
        else h.essential_expenses * h.reserve_months
    )
    available = None if reserve is None or not known else max(cash - reserve, ZERO)
    by_asset: dict[str, Decimal] = {}
    funded = {g.id: ZERO for g in h.goals}
    cash_allocated = ZERO
    for a in allocations:
        asset = assets.get(a.asset_id)
        if asset is None or a.goal_id not in funded:
            errors.append("资金分配引用了不存在的资产或目标")
            continue
        if asset.kind not in ("cash", "investment") or asset.liquidity != "available":
            errors.append(f"{asset.name}不能作为可动用目标资金")
            continue
        by_asset[a.asset_id] = by_asset.get(a.asset_id, ZERO) + a.amount
        value = convert(a.amount, asset.currency, "目标分配")
        if value is not None:
            funded[a.goal_id] += value
            if asset.kind == "cash":
                cash_allocated += value
    for aid, value in by_asset.items():
        if balances[aid] is not None and value > balances[aid]:
            errors.append(f"{assets[aid].name}重复或超额分配")
    if available is not None and cash_allocated > available:
        errors.append("目标现金分配占用了应急预留")
    linked: dict[str, Decimal] = {}
    unlinked = ZERO
    for c in h.commitments:
        if c.goal_id:
            linked[c.goal_id] = linked.get(c.goal_id, ZERO) + c.monthly
        else:
            unlinked += c.monthly
    scenario = request.scenario
    surplus = (
        None
        if h.income is None or h.expenses is None
        else h.income
        + scenario.extra_income
        - h.expenses
        - scenario.extra_expenses
        - minimum
    )
    pool = None if surplus is None else max(surplus - unlinked, ZERO)
    remaining = pool or ZERO
    contributions = ZERO
    results = []
    for g in sorted(h.goals, key=lambda item: (item.priority, item.id)):
        wanted = max(request.monthly.get(g.id, g.monthly), linked.get(g.id, ZERO))
        contributions += wanted
        affordable = min(wanted, remaining)
        remaining -= affordable
        due = month_after(g.target_date, scenario.extension_months)
        if due <= as_of:
            months = 0
        else:
            months = (due.year - as_of.year) * 12 + due.month - as_of.month
            if month_after(as_of, months) > due:
                months -= 1
            months = max(months, 0)
        target = (
            g.target
            * (1 - scenario.target_reduction_percent / 100)
            * (1 + scenario.annual_inflation / 12) ** months
        )
        rate = scenario.annual_return / 12
        growth = (1 + rate) ** months
        annuity = Decimal(months) if rate == 0 else (growth - 1) / rate
        projected = funded[g.id] * growth + affordable * annuity
        needed = (
            max((target - funded[g.id] * growth) / annuity, ZERO) if annuity else None
        )
        adjustments = []
        if projected < target:
            if needed is not None:
                required = needed.quantize(Decimal("0.01"), rounding=ROUND_CEILING)
                monthly_request = request.model_copy(
                    update={"monthly": {**request.monthly, g.id: required}}
                )
                adjustments.append(
                    {
                        "kind": "monthly",
                        "title": f"每月多投 {money(max(required - affordable, ZERO))}",
                        "description": f"每月 {money(affordable)} → {money(required)}；保留其它目标与已有定投下限。",
                        "request": monthly_request.model_dump(mode="json"),
                        "within_budget": surplus is not None
                        and required - affordable
                        <= max(
                            pool
                            - sum(
                                max(
                                    request.monthly.get(x.id, x.monthly),
                                    linked.get(x.id, ZERO),
                                )
                                for x in h.goals
                            ),
                            ZERO,
                        ),
                    }
                )
            for extra in range(1, 121 - scenario.extension_months):
                later = month_after(g.target_date, scenario.extension_months + extra)
                count = (later.year - as_of.year) * 12 + later.month - as_of.month
                if month_after(as_of, count) > later:
                    count -= 1
                count = max(count, 0)
                later_growth = (1 + rate) ** count
                later_annuity = (
                    Decimal(count) if rate == 0 else (later_growth - 1) / rate
                )
                later_target = (
                    g.target
                    * (1 - scenario.target_reduction_percent / 100)
                    * (1 + scenario.annual_inflation / 12) ** count
                )
                if (
                    funded[g.id] * later_growth + affordable * later_annuity
                    >= later_target
                ):
                    later_request = request.model_copy(
                        update={
                            "scenario": scenario.model_copy(
                                update={
                                    "extension_months": scenario.extension_months
                                    + extra
                                }
                            )
                        }
                    )
                    adjustments.append(
                        {
                            "kind": "extension",
                            "title": f"延期 {extra} 个月",
                            "description": f"到期日 {due.isoformat()} → {later.isoformat()}；此条件会延长全部目标。",
                            "request": later_request.model_dump(mode="json"),
                            "within_budget": True,
                        }
                    )
                    break
            original_target = g.target * (1 + scenario.annual_inflation / 12) ** months
            if original_target > 0:
                reduction = min(
                    Decimal(100),
                    max(ZERO, (1 - projected / original_target) * 100).quantize(
                        Decimal("0.01"), rounding=ROUND_CEILING
                    ),
                )
                lower_request = request.model_copy(
                    update={
                        "scenario": scenario.model_copy(
                            update={"target_reduction_percent": reduction}
                        )
                    }
                )
                adjustments.append(
                    {
                        "kind": "reduction",
                        "title": f"目标下调至 {reduction}%",
                        "description": "这是全部目标的总下调比例；每月投入不变，请在试算中逐目标核对。",
                        "request": lower_request.model_dump(mode="json"),
                        "within_budget": True,
                    }
                )
        results.append(
            {
                "id": g.id,
                "name": g.name,
                "priority": g.priority,
                "due": due.isoformat(),
                "months": months,
                "current": money(funded[g.id]),
                "target": money(target),
                "requested_monthly": money(wanted),
                "affordable_monthly": money(affordable),
                "needed_monthly": None
                if needed is None
                else str(needed.quantize(Decimal("0.01"), rounding=ROUND_CEILING)),
                "projected": money(projected),
                "shortfall": money(max(target - projected, ZERO)),
                "adjustments": adjustments,
            }
        )
    if pool is not None and contributions > pool:
        errors.append("月度投入超过可分配结余，请调整目标投入或情景条件")
    if surplus is not None and surplus < 0:
        errors.append("收支及最低还款后为负结余")
    if reserve is not None and cash < reserve:
        warnings.append("应急预留尚未补足，目标投入前请确认这一缺口")
    return {
        "as_of": as_of.isoformat(),
        "base_currency": h.base_currency,
        "assumptions": {
            **scenario.model_dump(mode="json"),
            "return_convention": "名义年率/12，月末投入；默认零收益、零通胀",
            "reserve_months": h.reserve_months,
            "reserve_policy": "应急预留不与目标资金重复使用",
            "calendar": "Asia/Shanghai；只计算目标到期前完整的月度投入",
        },
        "sources": [
            {"name": a.name, "date": a.observed_on.isoformat(), "source": a.source}
            for a in h.assets
        ]
        + [r.model_dump(mode="json") for r in h.rates]
        + [{"name": "收支与目标", "date": h.as_of.isoformat(), "source": h.source}],
        "assets": asset_rows,
        "known_net_worth": money(total - debt_total),
        "net_worth": money(total - debt_total)
        if known and h.assets_complete and h.debts_complete
        else None,
        "available_cash": money(cash) if known else None,
        "liquid_assets": money(liquid) if known else None,
        "reserve": money(reserve),
        "reserve_shortfall": money(max(reserve - cash, ZERO))
        if reserve is not None and known
        else None,
        "unallocated_cash": money(max(available - cash_allocated, ZERO))
        if available is not None
        else None,
        "minimum_payments": money(minimum),
        "monthly_surplus": money(surplus),
        "unlinked_commitments": money(unlinked),
        "monthly_committed": money(contributions + unlinked),
        "monthly_remaining": money(remaining) if pool is not None else None,
        "goals": results,
        "missing": list(dict.fromkeys(missing)),
        "errors": list(dict.fromkeys(errors)),
        "warnings": list(dict.fromkeys(warnings)),
        "executable": not missing and not errors,
    }
