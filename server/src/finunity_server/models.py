from datetime import date
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class AssetType(StrEnum):
    STOCK = "STOCK"
    ETF = "ETF"
    FUND = "FUND"
    CASH = "CASH"
    CRYPTO = "CRYPTO"
    UNKNOWN = "UNKNOWN"


class CostSource(StrEnum):
    DISPLAYED_AVERAGE = "DISPLAYED_AVERAGE"
    DISPLAYED_TOTAL = "DISPLAYED_TOTAL"
    DISPLAYED_BOTH = "DISPLAYED_BOTH"
    NOT_SHOWN = "NOT_SHOWN"


class RecognizedHolding(StrictModel):
    asset_type: AssetType
    name: str = Field(min_length=1, max_length=200)
    security_code: str | None = Field(default=None, max_length=40)
    quantity: float | None = Field(default=None, gt=0)
    quantity_unit: str | None = Field(default=None, max_length=20, pattern=r"^[A-Z0-9._-]+$")
    quantity_kind: str = Field(default="UNITS", pattern=r"^(UNITS|VALUE)$")
    average_cost: float | None = Field(default=None, ge=0)
    total_cost: float | None = Field(default=None, ge=0)
    cost_source: CostSource
    current_price: float | None = Field(default=None, ge=0)
    market_value: float | None = Field(default=None, ge=0)
    # ISO currencies are three letters; stablecoin screenshots commonly use
    # symbols such as USDT, which are still useful as an explicit quote.
    currency: str | None = Field(default=None, pattern=r"^[A-Z]{3,5}$")
    confidence: float = Field(ge=0, le=1)
    needs_review: bool
    review_reasons: list[str] = Field(default_factory=list, max_length=10)

    @model_validator(mode="after")
    def normalize_cost_and_review_state(self) -> "RecognizedHolding":
        reasons = list(dict.fromkeys(self.review_reasons))

        if (
            self.quantity_kind == "UNITS"
            and self.quantity
            and self.average_cost is not None
            and self.total_cost is None
        ):
            self.total_cost = round(self.quantity * self.average_cost, 8)
        elif (
            self.quantity_kind == "UNITS"
            and self.quantity
            and self.total_cost is not None
            and self.average_cost is None
        ):
            self.average_cost = round(self.total_cost / self.quantity, 8)

        if (
            self.quantity
            and self.average_cost is not None
            and self.total_cost is not None
            and self.total_cost > 0
        ):
            calculated_total = self.quantity * self.average_cost
            if abs(calculated_total - self.total_cost) / self.total_cost > 0.01:
                reasons.append("单位成本与总成本不一致")

        if self.quantity is None:
            reasons.append("缺少持仓数量")
        if self.average_cost is None or self.total_cost is None:
            reasons.append("截图未明确显示持仓成本")
        if self.currency is None:
            reasons.append("币种不明确")
        if self.quantity_kind == "VALUE" and self.quantity_unit is None:
            reasons.append("金额数量缺少单位")
        if self.asset_type is AssetType.UNKNOWN:
            reasons.append("资产类型不明确")
        if self.confidence < 0.85:
            reasons.append("识别置信度较低")

        self.review_reasons = list(dict.fromkeys(reasons))
        self.needs_review = self.needs_review or bool(self.review_reasons)
        return self


class ModelRecognitionResult(StrictModel):
    detected_account_name: str | None = Field(default=None, max_length=200)
    detected_provider: str | None = Field(default=None, max_length=200)
    as_of_date: date | None = None
    holdings: list[RecognizedHolding]
    warnings: list[str] = Field(default_factory=list, max_length=20)
