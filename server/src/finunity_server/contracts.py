import re
from datetime import date
from decimal import Decimal, InvalidOperation
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class InstrumentType(StrEnum):
    STOCK = "STOCK"
    ETF = "ETF"
    FUND = "FUND"
    LOF = "LOF"
    REIT = "REIT"
    BOND = "BOND"
    FX = "FX"


class ResolveQuery(ContractModel):
    client_ref: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1, max_length=100)
    type_hint: InstrumentType | None = None
    market_hint: str | None = Field(default=None, max_length=20)


class ResolveRequest(ContractModel):
    queries: list[ResolveQuery] = Field(min_length=1, max_length=100)


class SyncInstrument(ContractModel):
    client_ref: str = Field(min_length=1, max_length=100)
    instrument_id: str | None = Field(default=None, max_length=100)
    input_code: str | None = Field(default=None, max_length=100)
    asset_type: InstrumentType

    @model_validator(mode="after")
    def require_identifier(self) -> "SyncInstrument":
        if not self.instrument_id and not self.input_code:
            raise ValueError("instrument_id 和 input_code 至少提供一个")
        return self


class FxPair(ContractModel):
    client_ref: str = Field(min_length=1, max_length=100)
    base: str = Field(pattern=r"^[A-Z]{3}$")
    quote: str = Field(pattern=r"^[A-Z]{3}$")

    @model_validator(mode="after")
    def reject_same_currency(self) -> "FxPair":
        if self.base == self.quote:
            raise ValueError("同币种换算应由客户端本地处理")
        return self


class MarketSyncRequest(ContractModel):
    client_request_id: str = Field(min_length=1, max_length=100)
    base_currency: str = Field(pattern=r"^[A-Z]{3}$")
    instruments: list[SyncInstrument] = Field(default_factory=list, max_length=100)
    fx_pairs: list[FxPair] = Field(default_factory=list, max_length=20)
    include: list[str] = Field(default_factory=list, max_length=20)
    allow_delayed: bool = True


class QuotesBatchRequest(ContractModel):
    instrument_ids: list[str] = Field(min_length=1, max_length=100)
    fields: list[str] = Field(default_factory=lambda: ["LAST", "PREVIOUS_CLOSE"])


class FundNavQuery(ContractModel):
    client_ref: str = Field(min_length=1, max_length=100)
    instrument_id: str | None = Field(default=None, max_length=100)
    fund_code: str | None = Field(default=None, pattern=r"^\d{6}$")

    @model_validator(mode="after")
    def require_fund_identifier(self) -> "FundNavQuery":
        if not self.instrument_id and not self.fund_code:
            raise ValueError("instrument_id 和 fund_code 至少提供一个")
        return self


class FundNavBatchRequest(ContractModel):
    funds: list[FundNavQuery] = Field(min_length=1, max_length=100)
    as_of_date: date | None = None
    include_estimate: bool = False


class FxBatchRequest(ContractModel):
    pairs: list[FxPair] = Field(min_length=1, max_length=20)
    rate_type: str = Field(default="MID", pattern=r"^(MID|CLOSE)$")


class MetricsBatchRequest(ContractModel):
    instrument_ids: list[str] = Field(min_length=1, max_length=100)


class SessionRequest(ContractModel):
    installation_id: str = Field(min_length=8, max_length=200)
    platform: str = Field(pattern=r"^ANDROID$")
    app_version: str = Field(min_length=1, max_length=30)
    version_code: int = Field(gt=0)
    device_locale: str = Field(default="zh-CN", max_length=20)
    integrity_token: str | None = Field(default=None, max_length=8192)


class RefreshRequest(ContractModel):
    refresh_token: str = Field(min_length=20, max_length=1000)


class EmailCodeRequest(ContractModel):
    email: EmailStr
    purpose: str = Field(pattern=r"^(REGISTER|LOGIN|PASSWORD_RESET)$")
    locale: str = Field(default="zh-CN", max_length=20)


class EmailAuthRequest(ContractModel):
    email: EmailStr
    code: str = Field(pattern=r"^\d{6}$")
    client_request_id: str = Field(min_length=1, max_length=100)
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    password: str | None = Field(default=None, min_length=8, max_length=128)


class PasswordLoginRequest(ContractModel):
    email: EmailStr | None = None
    username: str | None = Field(default=None, min_length=3, max_length=32)
    password: str = Field(min_length=8, max_length=128)
    client_request_id: str = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_identity(self) -> "PasswordLoginRequest":
        if bool(self.email) == bool(self.username):
            raise ValueError("提供用户名或旧账号邮箱之一")
        return self


class UsernameRequest(ContractModel):
    username: str = Field(min_length=3, max_length=32)

    @field_validator("username")
    @classmethod
    def normalize_username(cls, value: str) -> str:
        value = value.lower()
        if not re.fullmatch(r"[a-z0-9_-]{3,32}", value):
            raise ValueError("用户名仅允许字母、数字、下划线和短横线")
        if value in {"admin", "administrator", "root", "system", "support", "finunity", "api"}:
            raise ValueError("该用户名为系统保留名称")
        return value


class NewPasswordRequest(ContractModel):
    password: str = Field(min_length=8, max_length=128)
    password_confirmation: str = Field(min_length=8, max_length=128)

    @model_validator(mode="after")
    def validate_passwords(self) -> "NewPasswordRequest":
        if self.password != self.password_confirmation:
            raise ValueError("两次密码输入不一致")
        if self.password.lower() == getattr(self, "username", ""):
            raise ValueError("密码不能与用户名相同")
        return self


class UsernameRegisterRequest(UsernameRequest, NewPasswordRequest):
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    accept_privacy: bool
    accept_terms: bool

    @model_validator(mode="after")
    def validate_consent(self) -> "UsernameRegisterRequest":
        if not (self.accept_privacy and self.accept_terms):
            raise ValueError("请同意隐私政策和用户协议")
        return self


class RecoveryResetRequest(UsernameRequest, NewPasswordRequest):
    recovery_code: str = Field(min_length=16, max_length=200)


class PasswordConfirmRequest(ContractModel):
    password: str = Field(min_length=8, max_length=128)


class PasswordChangeRequest(NewPasswordRequest):
    old_password: str = Field(min_length=8, max_length=128)


class UsernameSetRequest(UsernameRequest, PasswordConfirmRequest):
    pass


class PasswordResetRequest(ContractModel):
    email: EmailStr
    code: str = Field(pattern=r"^\d{6}$")
    password: str = Field(min_length=8, max_length=128)
    client_request_id: str = Field(min_length=1, max_length=100)


class ProfileRequest(ContractModel):
    display_name: str = Field(max_length=100)


class PreferencesRequest(ContractModel):
    language: str = Field(pattern=r"^(zh-CN|en-US)$")
    base_currency: str = Field(pattern=r"^[A-Z]{3}$")
    theme: str = Field(default="SYSTEM", pattern=r"^(SYSTEM|LIGHT|DARK)$")
    version: int | None = Field(default=None, ge=0)


class GoalsRequest(ContractModel):
    risk_profile: str = Field(pattern=r"^(CONSERVATIVE|BALANCED|GROWTH|AGGRESSIVE)$")
    horizon: str = Field(pattern=r"^(SHORT|MEDIUM|LONG)$")
    annual_return_target: str | None = Field(default=None, pattern=r"^\d+(\.\d+)?$")
    notes: str | None = Field(default=None, max_length=1000)
    aggressive_pct: int = Field(default=30, ge=0, le=100)
    balanced_pct: int = Field(default=60, ge=0, le=100)
    defensive_pct: int = Field(default=10, ge=0, le=100)
    version: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def validate_total(self) -> "GoalsRequest":
        if self.aggressive_pct + self.balanced_pct + self.defensive_pct != 100:
            raise ValueError("三桶目标比例合计必须为 100")
        return self


class AccountRequest(ContractModel):
    account_id: str | None = Field(default=None, max_length=100)
    name: str = Field(min_length=1, max_length=100)
    institution: str | None = Field(default=None, max_length=100)
    account_type: str = Field(pattern=r"^(BROKERAGE|FUND|BANK|CRYPTO|OTHER)$")
    currency: str = Field(default="CNY", pattern=r"^[A-Z]{3}$")
    opening_balance: str = Field(default="0", pattern=r"^\d+(\.\d{1,2})?$")
    version: int | None = Field(default=None, ge=0)


class SourceRequest(ContractModel):
    source_id: str | None = Field(default=None, max_length=100)
    name: str = Field(min_length=1, max_length=100)
    version: int | None = Field(default=None, ge=0)


class ArchiveRequest(ContractModel):
    version: int = Field(ge=1)


class HoldingItem(ContractModel):
    holding_id: str | None = Field(default=None, max_length=100)
    account_id: str | None = Field(default=None, max_length=100)
    source_id: str | None = Field(default=None, max_length=100)
    snapshot_date: str | None = Field(default=None, min_length=10, max_length=40)
    instrument_id: str | None = Field(default=None, max_length=100)
    name: str = Field(min_length=1, max_length=200)
    asset_type: str = Field(pattern=r"^(STOCK|ETF|FUND|LOF|REIT|BOND|CASH|CRYPTO|OTHER)$")
    quantity: str = Field(pattern=r"^\d+(\.\d+)?$")
    average_cost: str | None = Field(default=None, pattern=r"^\d+(\.\d+)?$")
    currency: str = Field(pattern=r"^[A-Z]{3,5}$")
    metadata: dict[str, Any] = Field(default_factory=dict)
    version: int | None = Field(default=None, ge=0)

    @field_validator("metadata")
    @classmethod
    def validate_price_metadata(cls, value: dict[str, Any]) -> dict[str, Any]:
        if value.get("risk_bucket", "BALANCED") not in {"AGGRESSIVE", "BALANCED", "DEFENSIVE"}:
            raise ValueError("Invalid risk bucket")
        price = value.get("current_price")
        if price is not None:
            try:
                parsed = Decimal(str(price))
                if not parsed.is_finite() or parsed < 0:
                    raise ValueError("Price must be finite and nonnegative")
            except InvalidOperation as exc:
                raise ValueError("Invalid price") from exc
        return value


class HoldingsBatchRequest(ContractModel):
    client_request_id: str = Field(min_length=1, max_length=100)
    items: list[HoldingItem] = Field(min_length=1, max_length=500)


class TransactionItem(ContractModel):
    transaction_id: str | None = Field(default=None, max_length=100)
    account_id: str | None = Field(default=None, max_length=100)
    to_account_id: str | None = Field(default=None, min_length=1, max_length=100)
    holding_id: str | None = Field(default=None, max_length=100)
    source_id: str | None = Field(default=None, max_length=100)
    transaction_type: str = Field(
        pattern=r"^(BUY|SELL|DIVIDEND|DEPOSIT|WITHDRAWAL|FEE|INTEREST|TRANSFER|OTHER)$"
    )
    amount: str = Field(pattern=r"^-?\d+(\.\d+)?$")
    quantity: str | None = Field(default=None, pattern=r"^\d+(\.\d+)?$")
    price: str | None = Field(default=None, pattern=r"^\d+(\.\d+)?$")
    currency: str = Field(pattern=r"^[A-Z]{3,5}$")
    occurred_at: str = Field(min_length=10, max_length=40)
    note: str | None = Field(default=None, max_length=500)
    version: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def validate_transfer(self) -> "TransactionItem":
        if self.transaction_type == "TRANSFER":
            if not self.account_id or not self.to_account_id:
                raise ValueError("转账必须提供转入账户")
            if self.to_account_id == self.account_id:
                raise ValueError("转入账户不能与转出账户相同")
        elif self.to_account_id is not None:
            raise ValueError("只有转账可以提供转入账户")
        return self


class TransactionsBatchRequest(ContractModel):
    client_request_id: str = Field(min_length=1, max_length=100)
    items: list[TransactionItem] = Field(min_length=1, max_length=500)


class HoldingImportCreateRequest(ContractModel):
    client_request_id: str = Field(min_length=1, max_length=100)
    document_ids: list[str] = Field(default_factory=list, max_length=20)
    rows: list[dict[str, Any]] = Field(default_factory=list, max_length=500)


class HoldingImportPreviewRequest(ContractModel):
    version: int = Field(ge=1)
    rows: list[dict[str, Any]] = Field(min_length=1, max_length=500)


class HoldingImportCommitRequest(ContractModel):
    client_request_id: str = Field(min_length=1, max_length=100)
    version: int = Field(ge=1)
    rows: list[dict[str, Any]] = Field(min_length=1, max_length=500)


class ApiError(ContractModel):
    code: str
    message: str
    retryable: bool = False
    retry_after_seconds: int | None = None
    details: list[Any] = Field(default_factory=list)


class ErrorEnvelope(ContractModel):
    request_id: str
    error: ApiError
    server_time: str


class SuccessEnvelope(ContractModel):
    request_id: str
    data: Any
    warnings: list[Any] = Field(default_factory=list)
    partial: bool = False
    server_time: str


class DeleteResponse(ContractModel):
    deleted: bool


class CorporateActionQuery(ContractModel):
    instrument_id: str
    since: date
    cursor: str | None = None


class HistoryQuery(ContractModel):
    instrument_id: str
    start_date: date
    end_date: date
    interval: str = "1d"
    adjustment: str = "TOTAL_RETURN"
    cursor: str | None = None

    @field_validator("adjustment")
    @classmethod
    def validate_adjustment(cls, value: str) -> str:
        if value not in {"NONE", "SPLIT_ADJUSTED", "TOTAL_RETURN"}:
            raise ValueError("不支持的复权口径")
        return value

    @model_validator(mode="after")
    def validate_dates(self) -> "HistoryQuery":
        if self.start_date > self.end_date:
            raise ValueError("start_date 不能晚于 end_date")
        return self
