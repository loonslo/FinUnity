from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, model_validator


def today() -> date:
    return datetime.now(timezone(timedelta(hours=8))).date()


def decimal_input(value: object) -> object:
    if not isinstance(value, (str, Decimal)):
        raise ValueError("金额请使用十进制字符串，不能使用浮点数")
    return value


Money = Annotated[
    Decimal,
    BeforeValidator(decimal_input, json_schema_input_type=str),
    Field(ge=0, max_digits=28, decimal_places=8),
]
Identifier = Annotated[str, Field(pattern=r"^[a-zA-Z0-9_-]{1,64}$")]
Quality = Literal["verified", "estimated", "unknown"]


class Contract(BaseModel):
    model_config = ConfigDict(
        extra="forbid", json_schema_serialization_defaults_required=True
    )


class Asset(Contract):
    id: Identifier
    name: str = Field(min_length=1, max_length=100)
    kind: Literal["cash", "investment", "property", "other"] = "cash"
    liquidity: Literal["available", "restricted", "illiquid"] = "available"
    currency: str = Field(default="CNY", pattern=r"^[A-Z]{3}$")
    amount: Money | None = None
    observed_on: date
    quality: Quality = "verified"
    source: str = Field(default="手动录入", min_length=1, max_length=200)


class Debt(Contract):
    id: Identifier
    name: str = Field(min_length=1, max_length=100)
    balance: Money | None = None
    currency: str = Field(default="CNY", pattern=r"^[A-Z]{3}$")
    minimum_payment: Money | None = None
    payment_in_expenses: bool = False
    source: str = Field(default="手动录入", min_length=1, max_length=200)


class Goal(Contract):
    id: Identifier
    name: str = Field(min_length=1, max_length=100)
    target: Money
    target_date: date
    monthly: Money = Decimal("0")
    priority: int = Field(default=1, ge=1, le=100)


class Allocation(Contract):
    asset_id: Identifier
    goal_id: Identifier
    amount: Money  # in the asset's original currency


class Commitment(Contract):
    id: Identifier
    name: str = Field(min_length=1, max_length=100)
    monthly: Money
    goal_id: Identifier | None = None


class Rate(Contract):
    currency: str = Field(pattern=r"^[A-Z]{3}$")
    to_base: Annotated[
        Decimal,
        BeforeValidator(decimal_input, json_schema_input_type=str),
        Field(gt=0, max_digits=28, decimal_places=12),
    ]
    observed_on: date
    source: str = Field(min_length=1, max_length=200)


class Household(Contract):
    name: str = Field(default="我的财务", min_length=1, max_length=100)
    as_of: date = Field(default_factory=lambda: today())
    base_currency: str = Field(default="CNY", pattern=r"^[A-Z]{3}$")
    language: Literal["zh-CN"] = "zh-CN"
    timezone: Literal["Asia/Shanghai"] = "Asia/Shanghai"
    income: Money | None = None
    expenses: Money | None = None
    essential_expenses: Money | None = None
    reserve_months: int = Field(default=6, ge=0, le=60)
    income_complete: bool = False
    expenses_complete: bool = False
    debts_complete: bool = False
    assets_complete: bool = False
    risk_tolerance: Literal["unknown", "cautious", "balanced", "high"] = "unknown"
    constraints: str = Field(default="", max_length=2000)
    source: str = Field(default="手动录入", min_length=1, max_length=200)
    assets: list[Asset] = Field(default_factory=list, max_length=100)
    debts: list[Debt] = Field(default_factory=list, max_length=100)
    goals: list[Goal] = Field(default_factory=list, max_length=100)
    allocations: list[Allocation] = Field(default_factory=list, max_length=500)
    commitments: list[Commitment] = Field(default_factory=list, max_length=100)
    rates: list[Rate] = Field(default_factory=list, max_length=100)

    @model_validator(mode="after")
    def references(self) -> Household:
        for rows in (self.assets, self.debts, self.goals, self.commitments):
            ids = [x.id for x in rows]
            if len(ids) != len(set(ids)):
                raise ValueError("同类记录的 ID 不能重复")
        codes = [r.currency for r in self.rates]
        if len(codes) != len(set(codes)):
            raise ValueError("每种货币只能提供一条汇率")
        assets = {a.id: a for a in self.assets}
        goals = {g.id for g in self.goals}
        pairs = [(a.asset_id, a.goal_id) for a in self.allocations]
        if len(pairs) != len(set(pairs)):
            raise ValueError("同一资产与目标的分配请合并为一条")
        for a in self.allocations:
            if a.asset_id not in assets or a.goal_id not in goals:
                raise ValueError("资金分配引用了不存在的资产或目标")
            asset = assets[a.asset_id]
            if (
                asset.kind not in ("cash", "investment")
                or asset.liquidity != "available"
            ):
                raise ValueError("受限资金与房产不能作为可动用目标资金")
        if any(
            c.goal_id is not None and c.goal_id not in goals for c in self.commitments
        ):
            raise ValueError("定投关联的目标不存在")
        if self.as_of > today() or any(a.observed_on > self.as_of for a in self.assets):
            raise ValueError("资产观测日期不能晚于建档日期，建档日期不能在未来")
        if self.as_of.year < 2000 or any(
            g.target_date.year > today().year + 100 for g in self.goals
        ):
            raise ValueError("建档日期至少为2000年；原型目标期限最多100年")
        if any(r.observed_on > self.as_of for r in self.rates):
            raise ValueError("汇率日期不能晚于建档日期")
        if (
            self.essential_expenses is not None
            and self.expenses is not None
            and self.essential_expenses > self.expenses
        ):
            raise ValueError("必要支出不能超过总支出")
        return self


class SaveHousehold(Contract):
    expected_revision: int = Field(ge=0)
    household: Household


class Scenario(Contract):
    extra_income: Money = Decimal("0")
    extra_expenses: Money = Decimal("0")
    extension_months: int = Field(default=0, ge=0, le=120)
    target_reduction_percent: Annotated[
        Decimal,
        BeforeValidator(decimal_input, json_schema_input_type=str),
        Field(ge=0, le=100),
    ] = Decimal("0")
    annual_return: Annotated[
        Decimal,
        BeforeValidator(decimal_input, json_schema_input_type=str),
        Field(ge=Decimal("-0.9"), le=Decimal("0.5")),
    ] = Decimal("0")
    annual_inflation: Annotated[
        Decimal,
        BeforeValidator(decimal_input, json_schema_input_type=str),
        Field(ge=0, le=Decimal("0.3")),
    ] = Decimal("0")


class PlanRequest(Contract):
    scenario: Scenario = Field(default_factory=Scenario)
    monthly: dict[Identifier, Money] = Field(default_factory=dict, max_length=100)
    allocations: list[Allocation] | None = Field(default=None, max_length=500)


class ProposeRequest(PlanRequest):
    expected_revision: int = Field(ge=0)


class Confirm(Contract):
    accept_shortfalls: bool = False


class Renew(Confirm):
    expected_revision: int = Field(ge=0)


class Reconcile(Contract):
    expected_revision: int = Field(ge=0)
    client_key: Identifier
    asset_id: Identifier
    balance: Money
    source: str = Field(default="银行余额核对", min_length=1, max_length=200)
    note: str = Field(default="", max_length=1000)


class Occurrence(Contract):
    client_key: Identifier
    kind: Literal["income", "expense", "transfer", "debt_payment", "adjustment"]
    adjustment_direction: Literal["increase", "decrease"] = "increase"
    state: Literal["forecast", "pending", "confirmed", "voided"] = "pending"
    quality: Quality = "verified"
    date: date
    asset_id: Identifier
    amount: Money | None = None
    target_asset_id: Identifier | None = None
    target_amount: Money | None = None
    goal_id: Identifier | None = None
    debt_id: Identifier | None = None
    action_id: Identifier | None = None
    allocation_mode: Literal["new_funding", "move_existing"] = "new_funding"
    principal: Money | None = None
    settles_id: Identifier | None = None
    source: str = Field(default="手动录入", min_length=1, max_length=200)
    note: str = Field(default="", max_length=1000)

    @model_validator(mode="after")
    def actual(self) -> Occurrence:
        if self.state == "confirmed" and (
            self.amount is None or self.quality == "unknown" or self.date > today()
        ):
            raise ValueError("实际记录需要已知金额，且日期不能在未来")
        if self.state == "confirmed" and self.amount == 0 and self.kind != "adjustment":
            raise ValueError("实际记录金额必须大于零")
        if self.kind != "adjustment" and self.adjustment_direction != "increase":
            raise ValueError("仅差额调整可以指定调整方向")
        if self.kind == "transfer" and (
            not self.target_asset_id or self.target_asset_id == self.asset_id
        ):
            raise ValueError("转账需要不同的转出与转入账户")
        if self.kind != "transfer" and (
            self.target_asset_id or self.target_amount is not None or self.goal_id
        ):
            raise ValueError("目标投入必须通过转账记录")
        if self.allocation_mode == "move_existing" and (
            self.kind != "transfer" or not self.goal_id
        ):
            raise ValueError("转移已分配资金需要指定目标与转入账户")
        if (
            self.state == "confirmed"
            and self.kind == "transfer"
            and self.target_amount is not None
            and self.target_amount == 0
        ):
            raise ValueError("实际转账的转入金额必须大于零")
        if self.kind == "debt_payment":
            if (
                not self.debt_id
                or self.principal is None
                or self.amount is None
                or self.principal > self.amount
            ):
                raise ValueError("还款需要负债、还款金额和不超过还款额的本金")
        elif self.debt_id or self.principal is not None:
            raise ValueError("非还款记录不能减少负债")
        return self


class ActionDecision(Contract):
    event_id: Identifier | None = None
    note: str = Field(default="", max_length=1000)


class ReviewRequest(Contract):
    month: str = Field(pattern=r"^\d{4}-(0[1-9]|1[0-2])$")


class Ask(Contract):
    expected_revision: int = Field(ge=0)
    message: str = Field(min_length=1, max_length=4000)


class AssistantAnswer(Contract):
    message: str = Field(min_length=1, max_length=6000)
    questions: list[str] = Field(default_factory=list, max_length=8)
    proposal: PlanRequest | None = None


class AdjustmentOption(Contract):
    kind: Literal["monthly", "extension", "reduction"]
    title: str
    description: str
    request: PlanRequest
    within_budget: bool


class GoalResult(Contract):
    id: str
    name: str
    priority: int
    due: str
    months: int
    current: str
    target: str
    requested_monthly: str
    affordable_monthly: str
    needed_monthly: str | None
    projected: str
    shortfall: str
    adjustments: list[AdjustmentOption] = Field(default_factory=list)


class AssetResult(Asset):
    balance: str | None
    value_base: str | None


class Calculation(Contract):
    as_of: str
    base_currency: str
    assumptions: dict
    sources: list[dict]
    assets: list[AssetResult]
    known_net_worth: str
    net_worth: str | None
    available_cash: str | None
    liquid_assets: str | None
    reserve: str | None
    reserve_shortfall: str | None
    unallocated_cash: str | None
    minimum_payments: str
    monthly_surplus: str | None
    unlinked_commitments: str
    monthly_committed: str
    monthly_remaining: str | None
    goals: list[GoalResult]
    missing: list[str]
    errors: list[str]
    warnings: list[str]
    executable: bool


class PlanRead(Contract):
    id: str
    kind: str
    status: str
    revision: int
    created_at: str
    request: PlanRequest
    household: Household
    input_events: list[Occurrence]
    result: Calculation
    confirmed_at: str | None = None
    household_revision: int = 0


class DocumentHeader(Contract):
    id: str
    kind: str
    status: str
    revision: int
    created_at: str


class OccurrenceRead(DocumentHeader):
    event: Occurrence
    settled_by: str | None = None
    voided_at: str | None = None


class ActionRead(DocumentHeader):
    plan_id: str
    goal_id: str
    month: str
    name: str
    amount: str
    requires_event: bool
    event_id: str | None = None
    voided_event_id: str | None = None
    completed_at: str | None = None
    note: str = ""
    base_currency: str
    actual_amount: str | None
    remaining_amount: str | None
    excess_amount: str | None
    event_ids: list[str]
    progress_missing: list[str]
    can_record: bool


class BatchRecordRequest(Contract):
    expected_revision: int = Field(ge=0)
    batch_key: Identifier
    items: list[Occurrence] = Field(min_length=1, max_length=50)

    @model_validator(mode="after")
    def unique_keys(self) -> BatchRecordRequest:
        if len({item.client_key for item in self.items}) != len(self.items):
            raise ValueError("同一批次不能包含重复的记录标识")
        return self


class BatchRecordRead(Contract):
    batch_id: str
    revision: int
    items: list[OccurrenceRead]


class ReviewGoal(Contract):
    id: str
    name: str
    planned: str
    actual: str
    variance: str


class ReviewResult(Contract):
    plan_id: str
    month: str
    partial_month: bool
    as_of: str
    base_currency: str
    income: str
    expenses: str
    debt_payments: str
    recorded_cash_surplus: str
    coverage: str
    missing: list[str]
    event_ids: list[str]
    goals: list[ReviewGoal]
    next: str


class ReviewRead(DocumentHeader):
    result: ReviewResult


class TaskRead(DocumentHeader):
    message: str
    context: dict
    input_events: list[Occurrence]
    model: str
    calls: list[dict]
    cost_status: str
    answer: AssistantAnswer | None = None
    proposal_id: str | None = None
    error: str | None = None
