from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone, date, timedelta
from decimal import Decimal
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import sole_writer
from app.planner import engine
from app.planner.models import PlanningDocument, PlanningState
from app.planner.schemas import (
    BatchRecordRequest,
    Household,
    Occurrence,
    PlanRequest,
    Reconcile,
    today,
)


def fail(code: str, message: str, status: int = 409) -> None:
    raise HTTPException(status, detail={"code": code, "message": message})


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def encode(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True)


def document(row: PlanningDocument) -> dict:
    return {
        "id": row.id,
        "kind": row.kind,
        "status": row.status,
        "revision": row.revision,
        "created_at": row.created_at,
        **json.loads(row.payload),
    }


def add_document(
    db: Session,
    kind: str,
    status: str,
    revision: int,
    payload: dict,
    identifier: str | None = None,
) -> PlanningDocument:
    row = PlanningDocument(
        id=identifier or uuid4().hex,
        kind=kind,
        status=status,
        revision=revision,
        payload=encode(payload),
        created_at=now(),
    )
    db.add(row)
    return row


def rows(db: Session, kind: str) -> list[PlanningDocument]:
    return list(
        db.scalars(
            select(PlanningDocument)
            .where(PlanningDocument.kind == kind)
            .order_by(PlanningDocument.created_at.desc(), PlanningDocument.id)
        )
    )


def get_document(db: Session, identifier: str, kind: str) -> PlanningDocument:
    row = db.get(PlanningDocument, identifier)
    if row is None or row.kind != kind:
        fail("NOT_FOUND", "记录不存在", 404)
    return row


def state(db: Session) -> tuple[int, Household]:
    row = db.get(PlanningState, 1)
    return (
        (0, Household())
        if row is None
        else (row.revision, Household.model_validate_json(row.payload))
    )


def events(db: Session) -> list[Occurrence]:
    return [
        Occurrence.model_validate(json.loads(row.payload)["event"]).model_copy(
            update={"state": "voided"}
        )
        if row.status == "voided"
        else Occurrence.model_validate(json.loads(row.payload)["event"])
        for row in reversed(rows(db, "occurrence"))
    ]


def snapshot(db: Session, request: PlanRequest | None = None) -> dict:
    revision, household = state(db)
    input_events = events(db)
    active = next((p for p in rows(db, "plan") if p.status == "active"), None)
    active_result = None
    if active is not None:
        saved = json.loads(active.payload)
        active_result = engine.calculate(
            household,
            input_events,
            PlanRequest.model_validate(saved["request"]),
            today(),
        )
    return {
        "revision": revision,
        "household_revision": household_revision(db),
        "household": household.model_dump(mode="json"),
        "result": engine.calculate(
            household, input_events, request or PlanRequest(), today()
        ),
        "active_result": active_result,
        "active_plan_id": None if active is None else active.id,
    }


def household_revision(db: Session, at_revision: int | None = None) -> int:
    return max(
        (
            row.revision
            for row in rows(db, "audit")
            if (at_revision is None or row.revision <= at_revision)
            and json.loads(row.payload).get("operation") == "save_household"
        ),
        default=0,
    )


def plan_view(db: Session, row: PlanningDocument) -> dict:
    data = document(row)
    revision, _ = state(db)
    if row.status == "pending" and (
        row.revision != revision or data["result"]["as_of"] != today().isoformat()
    ):
        data["status"] = "stale"
    data["household_revision"] = data.get(
        "household_revision", household_revision(db, row.revision)
    )
    return data


def discard_plan(db: Session, identifier: str) -> dict:
    with sole_writer(db):
        row = get_document(db, identifier, "plan")
        if row.status not in ("pending", "discarded"):
            fail("NOT_PENDING", "只能放弃尚未确认的方案草案")
        row.status = "discarded"
        response = plan_view(db, row)
    return response


def check_revision(db: Session, expected: int) -> tuple[int, Household]:
    revision, household = state(db)
    if revision != expected:
        fail("STALE_REVISION", "账目已变化，请刷新后重新测算")
    return revision, household


def bump(db: Session, household: Household | None = None) -> int:
    revision, existing = state(db)
    row = db.get(PlanningState, 1)
    if row is None:
        row = PlanningState(id=1, revision=0, payload=existing.model_dump_json())
        db.add(row)
    row.revision = revision + 1
    row.payload = (household or existing).model_dump_json()
    db.flush()
    return row.revision


def save_household(db: Session, household: Household, expected: int) -> dict:
    with sole_writer(db):
        _, current = check_revision(db, expected)
        if household == current and expected > 0:
            return snapshot(db)
        existing_events = events(db)
        asset_ids = {a.id for a in household.assets}
        debt_ids = {d.id for d in household.debts}
        goal_ids = {g.id for g in household.goals}
        for event in existing_events:
            if (
                event.asset_id not in asset_ids
                or (event.target_asset_id and event.target_asset_id not in asset_ids)
                or (event.debt_id and event.debt_id not in debt_ids)
                or (event.goal_id and event.goal_id not in goal_ids)
            ):
                fail(
                    "REFERENCED_RECORD", "已有流水引用这些账户、负债或目标，请保留其 ID"
                )
        result = engine.calculate(household, existing_events, PlanRequest(), today())
        hard_errors = [
            e for e in result["errors"] if "月度投入" not in e and "负结余" not in e
        ]
        if hard_errors:
            fail("INVALID_ALLOCATION", "；".join(hard_errors), 422)
        revision = bump(db, household)
        add_document(
            db,
            "audit",
            "completed",
            revision,
            {
                "operation": "save_household",
                "household": household.model_dump(mode="json"),
            },
        )
    return snapshot(db)


def propose_locked(
    db: Session, request: PlanRequest, expected: int
) -> PlanningDocument:
    revision, household = check_revision(db, expected)
    input_events = events(db)
    result = engine.calculate(household, input_events, request, today())
    return add_document(
        db,
        "plan",
        "pending",
        revision,
        {
            "request": request.model_dump(mode="json"),
            "household_revision": household_revision(db),
            "household": household.model_dump(mode="json"),
            "input_events": [e.model_dump(mode="json") for e in input_events],
            "result": result,
        },
    )


def propose(db: Session, request: PlanRequest, expected: int) -> dict:
    with sole_writer(db):
        row = propose_locked(db, request, expected)
        db.flush()
        result = document(row)
    return result


def confirm(db: Session, identifier: str, accept_shortfalls: bool) -> dict:
    with sole_writer(db):
        row = get_document(db, identifier, "plan")
        if row.status in ("active", "superseded"):
            return document(row)
        if row.status != "pending":
            fail("NOT_PENDING", "该方案已经结束，请重新生成")
        revision, _ = state(db)
        payload = json.loads(row.payload)
        if (
            row.revision != revision
            or payload["result"]["as_of"] != today().isoformat()
        ):
            fail("STALE_PROPOSAL", "账目或日期已变化，请重新测算并生成方案")
        result = payload["result"]
        if not result["executable"]:
            fail("INCOMPLETE_PLAN", "请先补齐资料并处理资金分配冲突", 422)
        shortfalls = (
            any(Decimal(g["shortfall"]) > 0 for g in result["goals"])
            or Decimal(result["reserve_shortfall"] or "0") > 0
        )
        if shortfalls and not accept_shortfalls:
            fail("ACCEPT_SHORTFALLS", "请确认你已了解目标或应急金缺口", 422)
        for previous in rows(db, "plan"):
            if previous.status == "active":
                previous.status = "superseded"
                for action in rows(db, "action"):
                    data = json.loads(action.payload)
                    if data["plan_id"] == previous.id and action.status == "pending":
                        action.status = "superseded"
        row.status = "active"
        payload["confirmed_at"] = now()
        row.payload = encode(payload)
        create_month_actions(db, row.id, result, revision)
        add_document(
            db,
            "audit",
            "completed",
            revision,
            {"operation": "confirm_plan", "plan_id": row.id},
        )
        response = document(row)
    return response


def create_month_actions(
    db: Session, plan_id: str, result: dict, revision: int
) -> None:
    for goal in result["goals"]:
        monetary = Decimal(goal["affordable_monthly"]) > 0
        add_document(
            db,
            "action",
            "pending",
            revision,
            {
                "plan_id": plan_id,
                "goal_id": goal["id"],
                "month": today().strftime("%Y-%m"),
                "name": f"为「{goal['name']}」记录本月实际投入"
                if monetary
                else f"检查「{goal['name']}」的资金安排与期限",
                "amount": goal["affordable_monthly"],
                "requires_event": monetary,
            },
        )


def renew(
    db: Session, identifier: str, expected: int, accept_shortfalls: bool
) -> list[dict]:
    with sole_writer(db):
        revision, household = check_revision(db, expected)
        plan = get_document(db, identifier, "plan")
        if plan.status != "active":
            fail("INACTIVE_PLAN", "请沿用当前使用中的方案")
        existing = [
            row
            for row in rows(db, "action")
            if json.loads(row.payload)["plan_id"] == identifier
            and json.loads(row.payload)["month"] == today().strftime("%Y-%m")
        ]
        if existing:
            return [action_view(db, row) for row in existing]
        saved = json.loads(plan.payload)
        if household.model_dump(mode="json") != saved["household"]:
            fail("HOUSEHOLD_CHANGED", "建档已变更，请重新测算并确认方案后继续")
        # A renewal freezes the original monthly amounts, not a new allocation.
        result = dict(saved["result"])
        current = engine.calculate(
            household, events(db), PlanRequest.model_validate(saved["request"]), today()
        )
        if not current["executable"]:
            fail("INCOMPLETE_PLAN", "当前资料或预算存在冲突，请先调整方案", 422)
        if (
            any(Decimal(g["shortfall"]) > 0 for g in current["goals"])
            or Decimal(current["reserve_shortfall"] or "0") > 0
        ) and not accept_shortfalls:
            fail("ACCEPT_SHORTFALLS", "请确认你已了解目标或应急金缺口", 422)
        create_month_actions(db, identifier, result, revision)
        add_document(
            db,
            "audit",
            "completed",
            revision,
            {
                "operation": "renew_plan",
                "plan_id": identifier,
                "month": today().strftime("%Y-%m"),
            },
        )
        db.flush()
        response = [
            action_view(db, row)
            for row in rows(db, "action")
            if json.loads(row.payload)["plan_id"] == identifier
            and json.loads(row.payload)["month"] == today().strftime("%Y-%m")
        ]
    return response


def reconcile(db: Session, request: Reconcile) -> dict:
    identifier = hashlib.sha256(
        ("reconcile:" + request.client_key).encode()
    ).hexdigest()[:32]
    normalized = request.model_dump(mode="json", exclude={"expected_revision"})
    with sole_writer(db):
        existing = db.get(PlanningDocument, identifier)
        if existing:
            saved = json.loads(existing.payload)
            if saved.get("request") != normalized:
                fail("IDEMPOTENCY_CONFLICT", "同一对账标识的内容不同")
            return document(get_document(db, saved["event_id"], "occurrence"))
        revision, household = check_revision(db, request.expected_revision)
        result = engine.calculate(household, events(db), PlanRequest(), today())
        asset = next((a for a in result["assets"] if a["id"] == request.asset_id), None)
        if (
            asset is None
            or asset["kind"] not in ("cash", "investment")
            or asset["balance"] is None
        ):
            fail("INVALID_ACCOUNT", "对账需要已知余额的现金或投资账户", 422)
        difference = request.balance - Decimal(asset["balance"])
        entry = Occurrence(
            client_key="recon-" + identifier,
            kind="adjustment",
            state="confirmed",
            date=today(),
            asset_id=request.asset_id,
            amount=abs(difference),
            adjustment_direction="increase" if difference >= 0 else "decrease",
            source=request.source,
            note=request.note,
        )
        response = _record_locked(db, entry, revision)
        add_document(
            db,
            "reconciliation",
            "completed",
            state(db)[0],
            {"request": normalized, "event_id": response["id"]},
            identifier,
        )
    return response


def validate_event(h: Household, event: Occurrence) -> None:
    assets = {a.id: a for a in h.assets}
    asset = assets.get(event.asset_id)
    if asset is None or (
        asset.kind != "cash"
        and not (event.kind == "adjustment" and asset.kind == "investment")
    ):
        fail("INVALID_ACCOUNT", "流水转出或收支账户必须是现金账户", 422)
    if event.state == "confirmed" and event.date <= asset.observed_on:
        fail(
            "BEFORE_ANCHOR",
            "实际流水必须晚于账户日终余额日期；同日记录请通过日终余额核对",
            422,
        )
    if event.kind == "transfer":
        target = assets.get(event.target_asset_id)
        if target is None or target.kind not in ("cash", "investment"):
            fail("INVALID_TARGET", "转入账户不存在或不支持转账", 422)
        if event.state == "confirmed" and event.date <= target.observed_on:
            fail("BEFORE_ANCHOR", "转账日期必须晚于两端账户的日终余额日期", 422)
        if asset.currency != target.currency and event.target_amount is None:
            fail("TARGET_AMOUNT_REQUIRED", "跨币种转账需要两端真实金额", 422)
        if asset.liquidity != "available" or target.liquidity != "available":
            fail(
                "RESTRICTED_ACCOUNT",
                "原型不处理受限账户转账，请先核对资金可用状态",
                422,
            )
        if (
            asset.currency == target.currency
            and event.target_amount is not None
            and event.target_amount != event.amount
        ):
            fail(
                "UNBALANCED_TRANSFER",
                "同币种转账两端金额必须一致，手续费请另记支出",
                422,
            )
        if event.goal_id and event.goal_id not in {g.id for g in h.goals}:
            fail("INVALID_GOAL", "目标不存在", 422)
    if event.debt_id:
        debt = next((d for d in h.debts if d.id == event.debt_id), None)
        if debt is None or debt.currency != asset.currency:
            fail("INVALID_DEBT", "负债不存在或还款币种不同", 422)
        if event.state == "confirmed" and event.date <= h.as_of:
            fail("BEFORE_ANCHOR", "还款日期必须晚于负债建档日期", 422)


def record(db: Session, event: Occurrence, expected: int) -> dict:
    with sole_writer(db):
        return _record_locked(db, event, expected)


def _record_locked(db: Session, event: Occurrence, expected: int) -> dict:
    identifier = hashlib.sha256(event.client_key.encode()).hexdigest()[:32]
    existing = db.get(PlanningDocument, identifier)
    if existing:
        if existing.kind != "occurrence" or Occurrence.model_validate(
            json.loads(existing.payload)["event"]
        ).model_dump(mode="json") != event.model_dump(mode="json"):
            fail("IDEMPOTENCY_CONFLICT", "同一提交标识的内容不同")
        return document(existing)
    _, household = check_revision(db, expected)
    validate_event(household, event)
    validate_action_link(db, event)
    if event.settles_id:
        forecast = get_document(db, event.settles_id, "occurrence")
        before = Occurrence.model_validate(json.loads(forecast.payload)["event"])
        if (
            forecast.status not in ("forecast", "pending")
            or event.state != "confirmed"
            or (
                before.kind,
                before.asset_id,
                before.target_asset_id,
                before.goal_id,
                before.debt_id,
            )
            != (
                event.kind,
                event.asset_id,
                event.target_asset_id,
                event.goal_id,
                event.debt_id,
            )
        ):
            fail("INVALID_SETTLEMENT", "只能用对应账户和用途的实际记录核销待发生项")
        forecast.status = "settled"
        data = json.loads(forecast.payload)
        data["settled_by"] = identifier
        forecast.payload = encode(data)
    result = engine.calculate(household, events(db) + [event], PlanRequest(), today())
    if any(
        "余额不足" in e
        or "还款本金超过" in e
        or "重复或超额分配" in e
        or "转移金额超过" in e
        for e in result["errors"]
    ):
        fail(
            "INSUFFICIENT_BALANCE",
            "记录会导致账户余额不足、目标资金不足或超额还款",
            422,
        )
    revision = bump(db)
    row = add_document(
        db,
        "occurrence",
        event.state,
        revision,
        {"event": event.model_dump(mode="json")},
        identifier,
    )
    add_document(
        db,
        "audit",
        "completed",
        revision,
        {"operation": "record_occurrence", "event_id": identifier},
    )
    response = document(row)
    db.flush()
    return response


def record_batch(db: Session, request: BatchRecordRequest) -> dict:
    identifier = hashlib.sha256(("batch:" + request.batch_key).encode()).hexdigest()[
        :32
    ]
    normalized = [item.model_dump(mode="json") for item in request.items]
    with sole_writer(db):
        existing = db.get(PlanningDocument, identifier)
        if existing is not None:
            saved = json.loads(existing.payload)
            if (
                existing.kind != "occurrence_batch"
                or saved.get("request_items") != normalized
            ):
                fail(
                    "IDEMPOTENCY_CONFLICT",
                    "这个批次已经提交过，内容发生了变化，请先刷新核对",
                )
            return saved["receipt"]
        check_revision(db, request.expected_revision)
        result = []
        for index, item in enumerate(request.items):
            try:
                result.append(_record_locked(db, item, state(db)[0]))
            except HTTPException as exc:
                detail = (
                    exc.detail
                    if isinstance(exc.detail, dict)
                    else {"message": str(exc.detail)}
                )
                raise HTTPException(
                    exc.status_code,
                    detail={
                        **detail,
                        "message": f"第 {index + 1} 条：{detail.get('message', '记录无效')}；本批次均未保存",
                        "item_index": index,
                    },
                ) from None
        revision = state(db)[0]
        receipt = {"batch_id": identifier, "revision": revision, "items": result}
        add_document(
            db,
            "occurrence_batch",
            "completed",
            revision,
            {"request_items": normalized, "receipt": receipt},
            identifier,
        )
    return receipt


def validate_action_link(db: Session, event: Occurrence) -> None:
    if event.action_id is None:
        return
    action = get_document(db, event.action_id, "action")
    data = json.loads(action.payload)
    plan = get_document(db, data["plan_id"], "plan")
    if (
        plan.status != "active"
        or action.status == "superseded"
        or data["month"] != today().strftime("%Y-%m")
    ):
        fail("INACTIVE_PLAN", "该投入计划已过期或被替换，请刷新选择当前计划")
    if (
        not data["requires_event"]
        or event.kind != "transfer"
        or event.goal_id != data["goal_id"]
        or event.allocation_mode != "new_funding"
        or not event.date.isoformat().startswith(data["month"])
    ):
        fail(
            "INVALID_ACTION_LINK",
            "投入记录需要匹配该计划的目标、月份，并选择新增目标资金",
            422,
        )


def action_view(db: Session, row: PlanningDocument) -> dict:
    """Monthly goal progress is derived from confirmed facts, never from a click."""
    data = document(row)
    plan = get_document(db, data["plan_id"], "plan")
    household = Household.model_validate(json.loads(plan.payload)["household"])
    assets = {a.id: a for a in household.assets}
    rates = {r.currency: r.to_base for r in household.rates}
    rates[household.base_currency] = Decimal(1)
    total = Decimal(0)
    event_ids = []
    missing = []
    for event_row in reversed(rows(db, "occurrence")):
        event = Occurrence.model_validate(json.loads(event_row.payload)["event"])
        if (
            event_row.status != "confirmed"
            or event.kind != "transfer"
            or event.allocation_mode != "new_funding"
            or event.goal_id != data["goal_id"]
            or not event.date.isoformat().startswith(data["month"])
            or event.date > today()
        ):
            continue
        event_ids.append(event_row.id)
        target = assets.get(event.target_asset_id)
        if target is None or target.currency not in rates:
            missing.append("实际投入的账户或汇率不在方案资料中，请重新核对方案")
            continue
        amount = (
            event.target_amount if event.target_amount is not None else event.amount
        )
        total += (amount or Decimal(0)) * rates[target.currency]
    planned = Decimal(data["amount"])
    status = row.status
    if data["requires_event"] and status != "superseded":
        status = (
            "needs_data"
            if missing
            else "completed"
            if total >= planned
            else "in_progress"
            if total > 0
            else "pending"
        )
    return {
        **data,
        "status": status,
        "base_currency": household.base_currency,
        "actual_amount": None if missing else engine.money(total),
        "remaining_amount": None
        if missing
        else engine.money(max(planned - total, Decimal(0))),
        "excess_amount": None
        if missing
        else engine.money(max(total - planned, Decimal(0))),
        "event_ids": event_ids,
        "progress_missing": list(dict.fromkeys(missing)),
        "can_record": data["requires_event"]
        and plan.status == "active"
        and row.status != "superseded"
        and data["month"] == today().strftime("%Y-%m"),
    }


def complete_action(
    db: Session, identifier: str, event_id: str | None, note: str
) -> dict:
    with sole_writer(db):
        row = get_document(db, identifier, "action")
        data = json.loads(row.payload)
        plan = get_document(db, data["plan_id"], "plan")
        if row.status == "completed" and (
            not data["requires_event"] or action_view(db, row)["status"] == "completed"
        ):
            return action_view(db, row)
        if plan.status != "active" or row.status not in ("pending", "completed"):
            fail("INACTIVE_PLAN", "请在当前有效方案中记录行动")
        if data["requires_event"]:
            if event_id is None:
                fail("ACTUAL_REQUIRED", "资金行动需要关联真实发生的投入记录", 422)
            event_row = get_document(db, event_id, "occurrence")
            e = json.loads(event_row.payload)["event"]
            month = data["month"]
            if (
                event_row.status != "confirmed"
                or e["kind"] != "transfer"
                or e["goal_id"] != data["goal_id"]
                or not e["date"].startswith(month)
                or e["date"] <= json.loads(plan.payload)["household"]["as_of"]
            ):
                fail(
                    "INVALID_ACTUAL",
                    "请关联本月、建档日期之后且属于该目标的已确认投入",
                    422,
                )
            progress = action_view(db, row)
            if event_id not in progress["event_ids"]:
                fail("INVALID_ACTUAL", "这条流水不是该目标本月的新增实际投入", 422)
            if (
                progress["actual_amount"] is None
                or Decimal(progress["remaining_amount"]) > 0
            ):
                fail(
                    "INCOMPLETE_ACTION",
                    "本月累计投入尚未达到计划金额，已保存的流水会显示为部分完成",
                    422,
                )
        data.update(event_id=event_id, note=note, completed_at=now())
        row.payload = encode(data)
        row.status = "completed"
        result = action_view(db, row)
    return result


def review_documents(db: Session) -> list[PlanningDocument]:
    """Expose one saved review per plan/month, retaining legacy duplicates on disk."""
    unique = {}
    for row in rows(db, "review"):
        result = json.loads(row.payload)["result"]
        key = (result["plan_id"], result["month"])
        if key not in unique:
            unique[key] = row
    return list(unique.values())


def review(db: Session, plan_id: str, month: str) -> dict:
    try:
        start = date.fromisoformat(month + "-01")
    except ValueError:
        fail("INVALID_MONTH", "月份无效", 422)
    first = today().replace(day=1)
    previous = (first - timedelta(days=1)).replace(day=1)
    closing = start == previous and today().day <= 10
    if start != first and not closing:
        fail("REVIEW_WINDOW", "可复盘本月；上月复盘可在次月 1–10 日补做", 422)
    with sole_writer(db):
        plan = get_document(db, plan_id, "plan")
        if plan.status != "active" and not (closing and plan.status == "superseded"):
            fail("INACTIVE_PLAN", "请使用当前已确认方案复盘", 422)
        if closing and not any(
            json.loads(row.payload)["plan_id"] == plan_id
            and json.loads(row.payload)["month"] == month
            for row in rows(db, "action")
        ):
            fail("NO_MONTH_PLAN", "该方案在上月没有投入计划，无法补做复盘", 422)
        data = json.loads(plan.payload)
        h = Household.model_validate(data["household"])
        current_revision, current_h = state(db)
        if current_h.base_currency != h.base_currency:
            fail("CURRENCY_CHANGED", "基础币种已改变，请先用新币种生成方案再复盘", 422)
        assets = {a.id: a for a in h.assets}
        rates = {r.currency: r.to_base for r in h.rates}
        rates[h.base_currency] = Decimal(1)
        income, expense, paid = Decimal(0), Decimal(0), Decimal(0)
        goal_actual: dict[str, Decimal] = {}
        missing = []
        counted = []
        for row in rows(db, "occurrence"):
            e = Occurrence.model_validate(json.loads(row.payload)["event"])
            if (
                row.status != "confirmed"
                or not e.date.isoformat().startswith(month)
                or e.date > today()
            ):
                continue
            asset = assets.get(e.asset_id)
            if asset is None or asset.currency not in rates:
                missing.append(f"流水 {row.id} 缺少方案时点的账户或汇率")
                continue
            value = (e.amount or Decimal(0)) * rates[asset.currency]
            counted.append(row.id)
            if e.kind == "income":
                income += value
            elif e.kind == "expense":
                expense += value
            elif e.kind == "debt_payment":
                paid += value
            elif e.goal_id and e.allocation_mode == "new_funding":
                target = assets.get(e.target_asset_id)
                if target is None or target.currency not in rates:
                    missing.append(f"流水 {row.id} 缺少转入汇率")
                else:
                    value = (
                        e.target_amount if e.target_amount is not None else e.amount
                    ) * rates[target.currency]
                    goal_actual[e.goal_id] = (
                        goal_actual.get(e.goal_id, Decimal(0)) + value
                    )
        result = {
            "plan_id": plan.id,
            "month": month,
            "partial_month": month == today().strftime("%Y-%m"),
            "as_of": today().isoformat(),
            "base_currency": h.base_currency,
            "income": engine.money(income),
            "expenses": engine.money(expense),
            "debt_payments": engine.money(paid),
            "recorded_cash_surplus": engine.money(income - expense - paid),
            "coverage": "仅统计已确认记录；无记录不代表没有收支。",
            "missing": missing,
            "event_ids": counted,
            "goals": [
                {
                    "id": g["id"],
                    "name": g["name"],
                    "planned": g["affordable_monthly"],
                    "actual": engine.money(goal_actual.get(g["id"], Decimal(0))),
                    "variance": engine.money(
                        goal_actual.get(g["id"], Decimal(0))
                        - Decimal(g["affordable_monthly"])
                    ),
                }
                for g in data["result"]["goals"]
            ],
            "next": "可根据偏差调整方案。",
        }
        row = next(
            (
                saved
                for saved in review_documents(db)
                if json.loads(saved.payload)["result"]["plan_id"] == plan_id
                and json.loads(saved.payload)["result"]["month"] == month
            ),
            None,
        )
        if row is None:
            row = add_document(
                db, "review", "completed", current_revision, {"result": result}
            )
        else:
            row.revision = current_revision
            row.payload = encode({"result": result})
        response = document(row)
    return response


def void_occurrence(db: Session, identifier: str, expected: int) -> dict:
    with sole_writer(db):
        row = get_document(db, identifier, "occurrence")
        if row.status == "voided":
            return document(row)
        check_revision(db, expected)
        if row.status == "settled":
            fail("SETTLED_FORECAST", "请先作废关联的实际记录，再处理预测项")
        data = json.loads(row.payload)
        original = Occurrence.model_validate(data["event"])
        remaining = [e for e in events(db) if e.client_key != original.client_key]
        result = engine.calculate(state(db)[1], remaining, PlanRequest(), today())
        if any(
            "余额不足" in e or "转移金额超过" in e or "超额分配" in e
            for e in result["errors"]
        ):
            fail(
                "DEPENDENT_OCCURRENCE",
                "后续记录或资金分配依赖这笔流水，请先修正依赖记录",
            )
        row.status = "voided"
        data["voided_at"] = now()
        row.payload = encode(data)
        if original.settles_id:
            forecast = get_document(db, original.settles_id, "occurrence")
            saved = json.loads(forecast.payload)
            forecast.status = saved["event"]["state"]
            saved.pop("settled_by", None)
            forecast.payload = encode(saved)
        for action in rows(db, "action"):
            saved = json.loads(action.payload)
            if saved.get("event_id") == identifier:
                saved["voided_event_id"] = saved.pop("event_id")
                saved.pop("completed_at", None)
                action.payload = encode(saved)
                plan = get_document(db, saved["plan_id"], "plan")
                action.status = "pending" if plan.status == "active" else "superseded"
        revision = bump(db)
        add_document(
            db,
            "audit",
            "completed",
            revision,
            {"operation": "void_occurrence", "event_id": identifier},
        )
        response = document(row)
    return response
