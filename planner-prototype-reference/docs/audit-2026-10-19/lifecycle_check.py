"""FinUnity 原型评审 · 隔离的后端复现实验（2026-10-19）

用法（在项目根目录）：
    cd backend
    uv run --no-sync python ../docs/audit-2026-10-19/lifecycle_check.py

这个脚本只读写系统临时目录里的一份全新 SQLite，不会碰 backend/finunity-prototype.db；
用 FastAPI TestClient 直接调用真实的 /api/v1/planner 接口，并把「今天」临时改成指定日期来模拟跨月。
每个实验对应评审报告里的一个编号：

  A  → P2  建档日期 = 今天：当天记不了任何「已发生」的账
  B  → P3  重设基准后，当月已记录的投入从行动进度与复盘里消失
  C  → P1  跨月：没有新月份的行动；上月复盘被拒；旧行动不能再关联
  D  → P5  待确认方案遇到账目变化：仍显示 pending，确认返回 409
  E  → N9  「每月需要 X」四舍五入，填回去仍差几分钱

只是证据脚本，不是回归测试；产品修复后，期望输出应当随之改变。
"""

from __future__ import annotations

import datetime as dt
import json
import os
import pathlib
import sys
import tempfile

BACKEND = str(pathlib.Path(__file__).resolve().parents[2] / "backend")
tmpdir = pathlib.Path(tempfile.mkdtemp(prefix="finunity-audit-db-"))
db_url = "sqlite:///" + (tmpdir / "audit.db").as_posix()
os.environ["DATABASE_URL"] = db_url           # 必须在导入 app 之前设置
os.environ["FINUNITY_DATABASE_URL"] = db_url
os.environ["AURELIO_SKIP_MIGRATIONS"] = "1"   # 与后端测试一致：直接用模型建表
os.environ.pop("OPENROUTER_API_KEY", None)
sys.path.insert(0, BACKEND)

from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import delete  # noqa: E402

from app import database  # noqa: E402
from app.planner import schemas, service  # noqa: E402
from app.planner.models import PlanningDocument, PlanningState  # noqa: E402
from app.prototype import app  # noqa: E402

assert tmpdir.as_posix() in str(database.engine.url), database.engine.url  # 绝不碰正式库
print("被测数据库:", database.engine.url)


def set_today(day: dt.date) -> None:
    """让服务端认为今天是 day。Household.as_of 的默认值也要一起改，否则默认值仍是真实日期。"""
    schemas.today = lambda: day
    service.today = lambda: day
    schemas.Household.model_fields["as_of"].default_factory = lambda: day
    schemas.Household.model_rebuild(force=True)


def reset() -> None:
    with database.SessionLocal() as db:
        db.execute(delete(PlanningDocument))
        db.execute(delete(PlanningState))
        db.commit()


def household(as_of: str, cash: str = "100000", invest: str = "20000", alloc_inv: str | None = None):
    allocations = [{"asset_id": "cash", "goal_id": "study", "amount": "20000"}]
    if alloc_inv:
        allocations.append({"asset_id": "investment", "goal_id": "study", "amount": alloc_inv})
    return {
        "name": "审计用虚构家庭", "as_of": as_of, "base_currency": "CNY",
        "language": "zh-CN", "timezone": "Asia/Shanghai",
        "income": "16000", "expenses": "7000", "essential_expenses": "5000",
        "reserve_months": 6, "income_complete": True, "expenses_complete": True,
        "debts_complete": True, "assets_complete": True, "risk_tolerance": "cautious",
        "constraints": "", "source": "audit",
        "assets": [
            {"id": "cash", "name": "日常与储蓄账户", "kind": "cash", "liquidity": "available",
             "currency": "CNY", "amount": cash, "observed_on": as_of, "quality": "verified", "source": "audit"},
            {"id": "investment", "name": "目标投资账户", "kind": "investment", "liquidity": "available",
             "currency": "CNY", "amount": invest, "observed_on": as_of, "quality": "estimated", "source": "audit"},
        ],
        "debts": [],
        "goals": [{"id": "study", "name": "学习进修", "target": "50000",
                   "target_date": "2027-10-31", "monthly": "2000", "priority": 1}],
        "allocations": allocations, "commitments": [], "rates": [],
    }


def contribution(key: str, date: str, action_id: str | None, amount: str = "2000"):
    return {
        "client_key": key, "kind": "transfer", "state": "confirmed", "quality": "verified",
        "date": date, "asset_id": "cash", "amount": amount, "target_asset_id": "investment",
        "target_amount": None, "goal_id": "study", "debt_id": None, "principal": None,
        "allocation_mode": "new_funding", "action_id": action_id, "settles_id": None,
        "source": "手动录入", "note": "",
    }


def short(resp):
    try:
        body = resp.json()
    except Exception:
        body = resp.text
    if isinstance(body, dict) and "detail" in body:
        d = body["detail"]
        if isinstance(d, dict):
            return f"{resp.status_code} {d.get('code')} {d.get('message')}"
        if isinstance(d, list):
            return f"{resp.status_code} " + "；".join(
                f"{'/'.join(map(str, x.get('loc', [])[1:]))}: {x.get('msg')}" for x in d)
        return f"{resp.status_code} {d}"
    return f"{resp.status_code}"


def setup_active_plan(c: TestClient, as_of: str):
    r = c.post("/api/v1/planner/household", json={"expected_revision": 0, "household": household(as_of)})
    assert r.status_code == 200, short(r)
    rev = r.json()["revision"]
    r = c.post("/api/v1/planner/plans", json={"expected_revision": rev, "scenario": {}, "monthly": {}})
    assert r.status_code == 201, short(r)
    plan = r.json()
    r = c.post(f"/api/v1/planner/plans/{plan['id']}/confirm", json={"accept_shortfalls": True})
    assert r.status_code == 200, short(r)
    return plan["id"]


results: dict[str, object] = {}

with TestClient(app) as c:
    P = "/api/v1/planner"

    # ---------------------------------------------------------------- A
    reset()
    set_today(dt.date(2026, 10, 19))
    print("\n== A（P2）建档日期 = 今天：新用户走「开始建档」的默认值 ==")
    plan_id = setup_active_plan(c, "2026-10-19")
    action = next(a for a in c.get(f"{P}/actions").json() if a["goal_id"] == "study")
    rev = c.get(f"{P}/state").json()["revision"]
    for label, date in (("记录日期 = 今天 2026-10-19", "2026-10-19"), ("记录日期 = 明天 2026-10-20", "2026-10-20")):
        r = c.post(f"{P}/occurrences/batch", json={
            "expected_revision": rev, "batch_key": "a-" + date,
            "items": [contribution("a-" + date, date, action["id"])]})
        print(f"  {label:<28} -> {short(r)}")
        results[f"A {label}"] = short(r)

    # ---------------------------------------------------------------- B
    reset()
    set_today(dt.date(2026, 10, 19))
    print("\n== B（P3）记录投入后重设基准（再次保存建档并确认新方案） ==")
    plan1 = setup_active_plan(c, "2026-10-17")
    action = next(a for a in c.get(f"{P}/actions").json() if a["goal_id"] == "study")
    rev = c.get(f"{P}/state").json()["revision"]
    r = c.post(f"{P}/occurrences/batch", json={
        "expected_revision": rev, "batch_key": "b1",
        "items": [contribution("b1", "2026-10-19", action["id"])]})
    print("  10-19 记录投入 2000          ->", short(r))
    a = next(a for a in c.get(f"{P}/actions").json() if a["goal_id"] == "study" and a["plan_id"] == plan1)
    print(f"  旧方案行动: status={a['status']} 已投入={a['actual_amount']} 还需={a['remaining_amount']}")
    results["B before"] = (a["status"], a["actual_amount"], a["remaining_amount"])

    set_today(dt.date(2026, 10, 25))
    rev = c.get(f"{P}/state").json()["revision"]
    # 用户把各账户余额按银行实际数字重设到 10-25（2000 已经转进投资账户）
    r = c.post(f"{P}/household", json={"expected_revision": rev, "household": household(
        "2026-10-25", cash="98000", invest="22000", alloc_inv="2000")})
    print("  10-25 保存新建档(基准=10-25)  ->", short(r) if r.status_code != 200 else "200 ok")
    rev = c.get(f"{P}/state").json()["revision"]
    r = c.post(f"{P}/plans", json={"expected_revision": rev, "scenario": {}, "monthly": {}})
    plan2 = r.json()["id"]
    r = c.post(f"{P}/plans/{plan2}/confirm", json={"accept_shortfalls": True})
    print("  确认新方案                   ->", short(r) if r.status_code != 200 else "200 ok")
    acts = [x for x in c.get(f"{P}/actions").json() if x["plan_id"] == plan2 and x["goal_id"] == "study"]
    for x in acts:
        print(f"  新方案 10 月行动: status={x['status']} 计划={x['amount']} 已投入={x['actual_amount']} 还需={x['remaining_amount']}")
    results["B after"] = [(x["status"], x["actual_amount"], x["remaining_amount"]) for x in acts]
    rv = c.post(f"{P}/plans/{plan2}/reviews", json={"month": "2026-10"})
    g = rv.json()["result"]["goals"][0]
    print(f"  本月复盘: 学习进修 计划={g['planned']} 实际={g['actual']} 差额={g['variance']}")
    results["B review"] = (g["planned"], g["actual"], g["variance"])

    # ---------------------------------------------------------------- C
    print("\n== C（P1）跨月：沿用 B 的使用中方案，时间推到 2026-11-02 ==")
    set_today(dt.date(2026, 11, 2))
    acts = c.get(f"{P}/actions").json()
    cur = [a for a in acts if a["month"] == "2026-11"]
    print("  2026-11 的行动数量:", len(cur), "｜10 月行动 can_record:", [a["can_record"] for a in acts if a["month"] == "2026-10"])
    results["C nov actions"] = len(cur)
    r = c.post(f"{P}/plans/{plan2}/reviews", json={"month": "2026-10"})
    print("  补做 10 月复盘               ->", short(r))
    results["C review oct"] = short(r)
    r = c.post(f"{P}/plans/{plan2}/reviews", json={"month": "2026-11"})
    print("  生成 11 月复盘               ->", short(r) if r.status_code >= 400 else f"{r.status_code} ok (partial_month={r.json()['result']['partial_month']})")
    state = c.get(f"{P}/state").json()
    r = c.post(f"{P}/occurrences/batch", json={
        "expected_revision": state["revision"], "batch_key": "c1",
        "items": [contribution("c1", "2026-11-02", acts[0]["id"])]})
    print("  11-02 关联 10 月行动记录投入 ->", short(r))
    results["C link old action"] = short(r)

    # ---------------------------------------------------------------- D
    reset()
    set_today(dt.date(2026, 10, 19))
    print("\n== D（P5）待确认方案遇到账目变化 ==")
    rev = c.post(f"{P}/household", json={"expected_revision": 0, "household": household("2026-10-17")}).json()["revision"]
    p1 = c.post(f"{P}/plans", json={"expected_revision": rev, "scenario": {}, "monthly": {}}).json()["id"]
    r = c.post(f"{P}/occurrences/batch", json={
        "expected_revision": rev, "batch_key": "d1",
        "items": [{**contribution("d1", "2026-10-19", None, "100"), "kind": "expense", "target_asset_id": None,
                   "goal_id": None}]})
    print("  先在「执行」页记一笔支出       ->", short(r) if r.status_code >= 400 else "201 ok")
    r = c.post(f"{P}/plans", json={"expected_revision": c.get(f"{P}/state").json()["revision"], "scenario": {}, "monthly": {"study": "2500"}})
    p2 = r.json()["id"]
    plans = c.get(f"{P}/plans").json()
    print("  方案列表状态:", [(p["id"][:6], p["status"], "rev", p["revision"]) for p in plans], "｜当前账目修订:", c.get(f"{P}/state").json()["revision"])
    r = c.post(f"{P}/plans/{p1}/confirm", json={"accept_shortfalls": True})
    print("  确认第一份（已过期的待确认）方案 ->", short(r))
    results["D confirm stale"] = short(r)
    results["D statuses"] = [p["status"] for p in plans]

    # ---------------------------------------------------------------- E
    reset()
    set_today(dt.date(2026, 10, 19))
    print("\n== E（N9）「每月需要 X」填回去是否补得上 ==")
    # 与 8011 上的示例一致：学习目标已分配 22,000（含已记的 2,000），到期前还有 12 个月
    c.post(f"{P}/household", json={"expected_revision": 0, "household": household(
        "2026-10-17", cash="98000", invest="22000", alloc_inv="2000")})
    base = c.post(f"{P}/calculate", json={"scenario": {}, "monthly": {}}).json()["result"]["goals"][0]
    print(f"  基线: 每月 {base['affordable_monthly']} → 到期测算 {base['projected']}，缺口 {base['shortfall']}，needed_monthly={base['needed_monthly']}")
    for v in (base["needed_monthly"], "2333.34"):
        g = c.post(f"{P}/calculate", json={"scenario": {}, "monthly": {"study": v}}).json()["result"]["goals"][0]
        print(f"  填 {v:>8} → 到期测算 {g['projected']}，缺口 {g['shortfall']}")
        results[f"E monthly {v}"] = (g["projected"], g["shortfall"])

print("\nSUMMARY", json.dumps(results, ensure_ascii=False))
print("临时库:", tmpdir, "（可手动删除）")
