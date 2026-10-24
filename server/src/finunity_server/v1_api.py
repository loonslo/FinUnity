import hashlib
import io
import json
import os
import sqlite3
from datetime import date, datetime
from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated, Any
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, Query, Request, Response, UploadFile, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from PIL import Image, UnidentifiedImageError
from pydantic import ValidationError

from finunity_server.config import Settings
from finunity_server.contracts import (
    AccountRequest,
    ArchiveRequest,
    DeleteResponse,
    EmailAuthRequest,
    EmailCodeRequest,
    FundNavBatchRequest,
    FxBatchRequest,
    GoalsRequest,
    HoldingImportCommitRequest,
    HoldingImportCreateRequest,
    HoldingImportPreviewRequest,
    HoldingItem,
    HoldingsBatchRequest,
    MarketSyncRequest,
    MetricsBatchRequest,
    PasswordLoginRequest,
    PasswordResetRequest,
    PreferencesRequest,
    ProfileRequest,
    QuotesBatchRequest,
    RefreshRequest,
    ResolveRequest,
    SessionRequest,
    SourceRequest,
    SuccessEnvelope,
    TransactionsBatchRequest,
)
from finunity_server.market_refresh import SHANGHAI
from finunity_server.platform_service import ReferenceDataProvider, SessionManager, utc_now
from finunity_server.service import (
    HoldingRecognizer,
    RecognitionInvalidOutputError,
    RecognitionNotConfiguredError,
    RecognitionTimeoutError,
    RecognitionUpstreamError,
)
from finunity_server.store import NotFound, SQLiteStore, StoreConflict

router = APIRouter()
IMAGE_TYPES = {"image/jpeg": "JPEG", "image/png": "PNG", "image/webp": "WEBP"}
CASH_INCREASE_TYPES = {"DEPOSIT", "DIVIDEND", "INTEREST", "SELL"}
CASH_DECREASE_TYPES = {"WITHDRAWAL", "FEE", "BUY", "TRANSFER"}
bearer_scheme = HTTPBearer(auto_error=False)


class ApiProblem(Exception):
    def __init__(
        self,
        http_status: int,
        code: str,
        message: str,
        *,
        retryable: bool = False,
        retry_after_seconds: int | None = None,
        details: list[Any] | None = None,
    ) -> None:
        self.http_status = http_status
        self.code = code
        self.message = message
        self.retryable = retryable
        self.retry_after_seconds = retry_after_seconds
        self.details = details or []
        super().__init__(message)


def envelope(
    request: Request, data: Any, *, warnings: list[Any] | None = None, partial: bool = False
) -> SuccessEnvelope:
    return SuccessEnvelope(
        request_id=request.state.request_id,
        data=data,
        warnings=warnings or [],
        partial=partial,
        server_time=utc_now(),
    )


def sessions(request: Request) -> SessionManager:
    return request.app.state.sessions


def provider(request: Request) -> ReferenceDataProvider:
    return request.app.state.data_provider


def recognizer(request: Request) -> HoldingRecognizer:
    return request.app.state.recognizer


def store(request: Request) -> SQLiteStore:
    return request.app.state.store


async def require_bearer(
    manager: Annotated[SessionManager, Depends(sessions)],
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)] = None,
) -> None:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise ApiProblem(status.HTTP_401_UNAUTHORIZED, "UNAUTHORIZED", "缺少 Bearer Token")
    if not manager.verify_access(credentials.credentials):
        raise ApiProblem(status.HTTP_401_UNAUTHORIZED, "TOKEN_INVALID", "Token 无效或已过期")


protected = [Depends(require_bearer)]


async def require_user(
    manager: Annotated[SessionManager, Depends(sessions)],
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)] = None,
) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise ApiProblem(401, "UNAUTHORIZED", "缺少 Bearer Token")
    claims = manager.claims(credentials.credentials)
    if not claims or claims["type"] != "USER":
        raise ApiProblem(401, "TOKEN_INVALID", "用户会话无效或已过期")
    return claims["sub"]


async def require_subject(
    manager: Annotated[SessionManager, Depends(sessions)],
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)] = None,
) -> str:
    subject = manager.subject(credentials.credentials) if credentials else None
    if not subject:
        raise ApiProblem(401, "TOKEN_INVALID", "会话无效或已过期")
    return subject


def require_email_enabled(request: Request) -> None:
    if not request.app.state.settings.email_auth_enabled:
        raise ApiProblem(404, "NOT_FOUND", "接口未开放")


def store_problem(exc: Exception) -> ApiProblem:
    if isinstance(exc, StoreConflict):
        return ApiProblem(409, "VERSION_CONFLICT", "数据已在其他设备更新，请刷新后重试")
    return ApiProblem(404, "NOT_FOUND", "记录不存在或无权访问")


def public_user(db: SQLiteStore, user_id: str) -> dict[str, Any]:
    row = db.user(user_id)
    preferences = db.setting(user_id, "preferences") or {}
    return {
        "user_id": row["id"],
        "email": row["email"],
        "username": row["username"],
        "requires_username": row["username"] is None,
        "display_name": row["display_name"],
        "language": preferences.get("language", "zh-CN"),
        "base_currency": preferences.get("base_currency", "CNY"),
        "preferences_version": preferences.get("version", 0),
    }


def public_account(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "account_id": row["id"],
        "name": row["name"],
        "institution": row["institution"],
        "account_type": row["account_type"].lower(),
        "currency": row["currency"],
        "opening_balance_minor": money_minor(row.get("opening_balance") or "0"),
        "status": "active",
        "version": row["version"],
        "updated_at": row["updated_at"],
    }


def public_holding(row: dict[str, Any]) -> dict[str, Any]:
    metadata = row.get("metadata") or {}
    quantity = Decimal(row["quantity"])
    is_cash = row["asset_type"] == "CASH"
    quantity_kind = str(metadata.get("quantity_kind") or "UNITS").upper()
    quantity_unit = metadata.get("quantity_unit")
    cost = Decimal(row["average_cost"] or ("1" if is_cash else "0"))
    price = (metadata.get("current_price") or 1) if is_cash else metadata.get("current_price")
    if quantity_kind == "VALUE":
        value_minor = money_minor(quantity)
        raw_cost_value = metadata.get("cost_value")
        cost_minor = money_minor(raw_cost_value) if raw_cost_value is not None else 0
        unrealized_profit_minor = value_minor - cost_minor if raw_cost_value is not None else None
    else:
        value_minor = money_minor(quantity * Decimal(str(price if price is not None else cost)))
        cost_minor = money_minor(quantity * cost)
        unrealized_profit_minor = (
            value_minor - cost_minor if row["average_cost"] is not None else None
        )
    return {
        "market_value_minor": value_minor,
        "cost_minor": cost_minor,
        "unrealized_profit_minor": unrealized_profit_minor,
        "valuation_status": "priced" if price is not None else "estimated",
        "holding_id": row["id"],
        "account_id": row["account_id"],
        "source_id": row.get("source_id"),
        "snapshot_date": row.get("snapshot_date"),
        "instrument_id": row["instrument_id"],
        "name": row["name"],
        "asset_type": row["asset_type"].lower(),
        "quantity": float(row["quantity"]),
        "quantity_unit": quantity_unit,
        "quantity_kind": quantity_kind,
        "average_cost": float(row["average_cost"]) if row["average_cost"] is not None else None,
        "current_price": float(price) if price is not None else None,
        "currency": row["currency"],
        "risk_bucket": metadata.get("risk_bucket", "BALANCED"),
        "market_source": metadata.get("market_source", "manual"),
        "price_as_of": metadata.get("price_as_of"),
        "version": row["version"],
        "updated_at": row["updated_at"],
    }


def public_transaction(row: dict[str, Any]) -> dict[str, Any]:
    try:
        details = json.loads(row.get("note") or "{}")
    except (TypeError, json.JSONDecodeError):
        details = {"title": row.get("note") or row["transaction_type"]}
    kind = row["transaction_type"].lower()
    if details.get("to_account_id") and kind == "other":
        kind = "transfer"
    return {
        "transaction_id": row["id"],
        "account_id": row["account_id"],
        "to_account_id": row.get("to_account_id") or details.get("to_account_id"),
        "source_id": row.get("source_id"),
        "transaction_date": row["occurred_at"][:10],
        "transaction_type": kind,
        "category": details.get("category"),
        "title": details.get("title", row["transaction_type"]),
        "amount_minor": money_minor(row["amount"]),
        "currency": row["currency"],
        "note": details.get("note"),
        "version": row["version"],
        "updated_at": row["updated_at"],
    }


def money_minor(value: str | int | float | Decimal) -> int:
    return int((Decimal(str(value)) * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def transaction_cash_effect_minor(item: dict[str, Any], account_id: str) -> int:
    """Return a transaction's cash effect for one account in minor units."""
    amount = abs(money_minor(item["amount"]))
    kind = item["transaction_type"]
    if item.get("to_account_id") == account_id and kind == "TRANSFER":
        return amount
    if item.get("account_id") != account_id:
        return 0
    if kind in CASH_INCREASE_TYPES:
        return amount
    if kind in CASH_DECREASE_TYPES:
        return -amount
    return money_minor(item["amount"])


def validate_transaction_cash(
    accounts: dict[str, dict[str, Any]],
    existing_transactions: list[dict[str, Any]],
    items: list[dict[str, Any]],
) -> None:
    """Reject new cash outflows that would leave an account below zero."""
    balances = {
        account_id: money_minor(account.get("opening_balance") or "0")
        for account_id, account in accounts.items()
    }

    normalized_items = [
        item.model_dump() if hasattr(item, "model_dump") else item for item in items
    ]
    replacing_ids = {
        item["transaction_id"] for item in normalized_items if item.get("transaction_id")
    }
    for transaction in existing_transactions:
        if transaction["id"] in replacing_ids:
            continue
        for account_id in balances:
            balances[account_id] += transaction_cash_effect_minor(transaction, account_id)

    for item_dict in normalized_items:
        for account_id in balances:
            balances[account_id] += transaction_cash_effect_minor(item_dict, account_id)
        if any(
            balances[account_id] < 0 and transaction_cash_effect_minor(item_dict, account_id) < 0
            for account_id in balances
        ):
            raise ApiProblem(
                400,
                "INSUFFICIENT_FUNDS",
                "流出金额超过账户可用余额，保存后余额不能为负数",
            )


def public_source(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "source_id": row["id"],
        "name": row["name"],
        "version": row["version"],
        "updated_at": row["updated_at"],
    }


def public_import(row: dict[str, Any]) -> dict[str, Any]:
    payload = row.get("payload") or {}
    return {
        "batch_id": row["id"],
        "status": row["status"],
        "documents": payload.get("document_ids", []),
        "rows": payload.get("rows", []),
        "plan": payload.get("plan", []),
        "version": row["version"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }


def ledger_summary(db: SQLiteStore, user_id: str, month: str) -> dict[str, Any]:
    snapshot = db.ledger_snapshot(user_id)
    holdings = snapshot["holdings"]
    transactions = snapshot["transactions"]
    sources = snapshot.get("sources", [])
    preferences = snapshot.get("preferences", {})
    goals = snapshot.get(
        "goals",
        {
            "aggressive_pct": 30,
            "balanced_pct": 60,
            "defensive_pct": 10,
            "version": 0,
        },
    )
    valued_holdings = [public_holding(row) for row in holdings]
    base_currency = preferences.get("base_currency", "CNY")
    totals: dict[str, dict[str, int]] = {}

    def currency_totals(currency: str) -> dict[str, int]:
        return totals.setdefault(
            currency,
            {
                "cash_balance_minor": 0,
                "holdings_value_minor": 0,
                "holdings_cost_minor": 0,
                "unrealized_profit_minor": 0,
                "total_assets_minor": 0,
                "income_minor": 0,
                "expense_minor": 0,
                "net_cashflow_minor": 0,
            },
        )

    # A holding is the source of truth for current assets. Cash snapshots are
    # holdings with a unit price of one; transactions never mutate them.
    for raw_holding, holding in zip(holdings, valued_holdings, strict=True):
        aggregate = currency_totals(holding["currency"])
        if raw_holding["asset_type"] == "CASH":
            aggregate["cash_balance_minor"] += holding["market_value_minor"]
        else:
            aggregate["holdings_value_minor"] += holding["market_value_minor"]
            aggregate["holdings_cost_minor"] += holding["cost_minor"]
            aggregate["unrealized_profit_minor"] += holding["unrealized_profit_minor"] or 0
        aggregate["total_assets_minor"] += holding["market_value_minor"]

    for item in transactions:
        if not item["occurred_at"].startswith(month):
            continue
        aggregate = currency_totals(item["currency"])
        amount = abs(money_minor(item["amount"]))
        if item["transaction_type"] in {"DEPOSIT", "DIVIDEND", "INTEREST"}:
            aggregate["income_minor"] += amount
        elif item["transaction_type"] in {"WITHDRAWAL", "FEE"}:
            aggregate["expense_minor"] += amount
    for aggregate in totals.values():
        aggregate["net_cashflow_minor"] = aggregate["income_minor"] - aggregate["expense_minor"]
    base = currency_totals(base_currency)
    allocation_valid = base["total_assets_minor"] > 0
    allocation = []
    for bucket in ("AGGRESSIVE", "BALANCED", "DEFENSIVE"):
        value = sum(
            h["market_value_minor"]
            for h, raw in zip(valued_holdings, holdings, strict=True)
            if raw["asset_type"] != "CASH"
            and h["currency"] == base_currency
            and h["risk_bucket"] == bucket
        )
        if bucket == "DEFENSIVE":
            value += base["cash_balance_minor"]
        share = value / base["total_assets_minor"] * 100 if allocation_valid else None
        target = goals[f"{bucket.lower()}_pct"]
        allocation.append(
            {
                "key": bucket,
                "value": value,
                "share_pct": share,
                "target_pct": target,
                "gap_minor": int(
                    (Decimal(base["total_assets_minor"]) * Decimal(target) / 100).quantize(
                        Decimal("1"), rounding=ROUND_HALF_UP
                    )
                ) - value if allocation_valid else None,
                "deviation_pct": share - target if share is not None else None,
            }
        )
    drift = sum(abs(row["deviation_pct"]) for row in allocation) if allocation_valid else None
    categories: dict[str, int] = {}
    for item in transactions:
        if (
            item["currency"] == base_currency
            and item["occurred_at"].startswith(month)
            and item["transaction_type"] in {"DEPOSIT", "DIVIDEND", "INTEREST", "WITHDRAWAL", "FEE"}
        ):
            name = public_transaction(item).get("category") or "Other"
            categories[name] = categories.get(name, 0) + abs(money_minor(item["amount"]))
    return {
        "generated_at": utc_now(),
        "records": {
            "accounts": [],
            "holdings": valued_holdings,
            "transactions": [public_transaction(row) for row in transactions],
            "sources": [public_source(row) for row in sources],
        },
        "goals": goals,
        "allocation": {
            "items": allocation,
            "valid": allocation_valid,
            "drift_pct": drift,
            "score": max(0, round(100 - drift / 2)) if drift is not None else None,
            "largest_gap_pct": max(abs(row["deviation_pct"]) for row in allocation)
            if allocation_valid
            else None,
        },
        "categories": sorted(categories.items(), key=lambda item: item[1], reverse=True)[:6],
        "month": month,
        "base_currency": base_currency,
        "base": currency_totals(base_currency),
        "by_currency": totals,
        "accounts": [],
        "excluded_currencies": sorted(currency for currency in totals if currency != base_currency),
    }


def item_error(code: str, message: str, retryable: bool = False) -> dict[str, Any]:
    return {"code": code, "message": message, "retryable": retryable, "details": []}


@router.post(
    "/api/v1/auth/email/code",
    response_model=SuccessEnvelope,
    tags=["web-auth"],
    dependencies=[Depends(require_email_enabled)],
)
async def issue_email_code(
    request: Request, body: EmailCodeRequest, db: Annotated[SQLiteStore, Depends(store)]
) -> SuccessEnvelope:
    email, purpose = str(body.email).strip().lower(), body.purpose
    settings = request.app.state.settings
    if not settings.smtp_host and not settings.expose_verification_code:
        raise ApiProblem(
            503,
            "EMAIL_SERVICE_NOT_CONFIGURED",
            "邮件服务尚未配置，请联系管理员",
            retryable=False,
        )
    exists = db.user_by_email(email) is not None
    if purpose == "REGISTER" and exists:
        raise ApiProblem(409, "EMAIL_REGISTERED", "该邮箱已注册，请直接登录")
    if purpose in {"LOGIN", "PASSWORD_RESET"} and not exists:
        raise ApiProblem(404, "USER_NOT_FOUND", "该邮箱尚未注册")
    code = db.issue_code(email, purpose)
    try:
        db.send_code(email, code, body.locale)
    except OSError as exc:
        raise ApiProblem(
            502, "EMAIL_DELIVERY_FAILED", "验证码邮件发送失败，请稍后重试", retryable=True
        ) from exc
    data: dict[str, Any] = {"expires_in": settings.email_code_ttl_seconds}
    if settings.expose_verification_code:
        data["dev_code"] = code
    return envelope(request, data)


@router.post(
    "/api/v1/auth/email/register",
    response_model=SuccessEnvelope,
    tags=["web-auth"],
    dependencies=[Depends(require_email_enabled)],
)
async def email_register(
    request: Request,
    body: EmailAuthRequest,
    db: Annotated[SQLiteStore, Depends(store)],
    manager: Annotated[SessionManager, Depends(sessions)],
) -> SuccessEnvelope:
    email = str(body.email).strip().lower()
    if not db.consume_code(email, "REGISTER", body.code):
        raise ApiProblem(400, "VERIFICATION_CODE_INVALID", "验证码无效或已过期")
    try:
        user = db.create_user(email, body.display_name)
    except StoreConflict as exc:
        raise store_problem(exc) from exc
    if body.password:
        db.set_user_password(user["id"], body.password)
    tokens = manager.issue(user["id"], auth_version=user["auth_version"])
    return envelope(request, {**tokens, "user": public_user(db, user["id"])})


@router.post(
    "/api/v1/auth/login",
    response_model=SuccessEnvelope,
    tags=["web-auth"],
    dependencies=[Depends(require_email_enabled)],
)
async def email_login(
    request: Request,
    body: EmailAuthRequest,
    db: Annotated[SQLiteStore, Depends(store)],
    manager: Annotated[SessionManager, Depends(sessions)],
) -> SuccessEnvelope:
    email = str(body.email).strip().lower()
    user = db.user_by_email(email)
    if not user or not db.consume_code(email, "LOGIN", body.code):
        raise ApiProblem(400, "VERIFICATION_CODE_INVALID", "验证码无效或已过期")
    tokens = manager.issue(user["id"], auth_version=user["auth_version"])
    return envelope(request, {**tokens, "user": public_user(db, user["id"])})


@router.post(
    "/api/v1/auth/password/login",
    response_model=SuccessEnvelope,
    tags=["web-auth"],
)
def password_login(
    request: Request,
    body: PasswordLoginRequest,
    db: Annotated[SQLiteStore, Depends(store)],
    manager: Annotated[SessionManager, Depends(sessions)],
) -> SuccessEnvelope:
    from finunity_server.auth_api import auth_failure, auth_rate

    identity = (body.username or str(body.email)).strip().lower()
    auth_rate(request, db, identity)
    user = db.user_by_username(identity) if body.username else db.user_by_email(identity)
    # Existing email accounts can migrate without enabling email delivery.
    if (
        body.email
        and user
        and user["username"]
        and not request.app.state.settings.email_auth_enabled
    ):
        user = None
    if not db.verify_password(user, body.password):
        auth_failure(request, db, identity)
    db.clear_password_login_failures(identity)
    tokens = manager.issue(user["id"], auth_version=user["auth_version"])
    return envelope(request, {**tokens, "user": public_user(db, user["id"])})


@router.post(
    "/api/v1/auth/password/set",
    response_model=SuccessEnvelope,
    tags=["web-auth"],
    dependencies=[Depends(require_email_enabled)],
)
async def set_password(
    request: Request,
    body: PasswordResetRequest,
    db: Annotated[SQLiteStore, Depends(store)],
    manager: Annotated[SessionManager, Depends(sessions)],
) -> SuccessEnvelope:
    """用邮箱验证码设置/重置登录密码：首次设置与忘记密码共用此流程。"""
    email = str(body.email).strip().lower()
    user = db.user_by_email(email)
    if not user:
        raise ApiProblem(404, "USER_NOT_FOUND", "该邮箱尚未注册")
    if not db.consume_code(email, "PASSWORD_RESET", body.code):
        raise ApiProblem(400, "VERIFICATION_CODE_INVALID", "验证码无效或已过期")
    db.replace_credentials(user, password=body.password)
    tokens = manager.issue(user["id"], auth_version=user["auth_version"] + 1)
    return envelope(request, {**tokens, "user": public_user(db, user["id"])})


@router.post("/api/v1/auth/logout", response_model=SuccessEnvelope, tags=["web-auth"])
async def email_logout(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    manager: Annotated[SessionManager, Depends(sessions)],
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
) -> SuccessEnvelope:
    if credentials is None:
        raise ApiProblem(401, "UNAUTHORIZED", "缺少 Bearer Token")
    manager.revoke(credentials.credentials, user_id)
    return envelope(request, {"logged_out": True, "user_id": user_id})


@router.get("/api/v1/me", response_model=SuccessEnvelope, tags=["web-user"])
async def get_me(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    return envelope(request, public_user(db, user_id))


@router.post("/api/v1/me/profile", response_model=SuccessEnvelope, tags=["web-user"])
async def save_profile(
    request: Request,
    body: ProfileRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    db.set_display_name(user_id, body.display_name)
    return envelope(request, public_user(db, user_id))


@router.post("/api/v1/me/preferences", response_model=SuccessEnvelope, tags=["web-user"])
async def save_preferences(
    request: Request,
    body: PreferencesRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    try:
        saved = db.save_setting(user_id, "preferences", body.model_dump(), body.version)
    except StoreConflict as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.post("/api/v1/me/goals", response_model=SuccessEnvelope, tags=["web-user"])
async def save_goals(
    request: Request,
    body: GoalsRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    try:
        saved = db.save_setting(user_id, "goals", body.model_dump(), body.version)
    except StoreConflict as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.get("/api/v1/me/goals", response_model=SuccessEnvelope, tags=["web-user"])
async def get_goals(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    current = db.setting(user_id, "goals") or {
        "risk_profile": "BALANCED",
        "horizon": "LONG",
        "aggressive_pct": 30,
        "balanced_pct": 60,
        "defensive_pct": 10,
        "version": 0,
    }
    return envelope(request, current)


@router.get("/api/v1/sources", response_model=SuccessEnvelope, tags=["web-data"])
async def list_sources(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    return envelope(
        request, {"items": [public_source(x) for x in db.list_rows("sources", user_id)]}
    )


@router.post("/api/v1/sources", response_model=SuccessEnvelope, tags=["web-data"])
async def upsert_source(
    request: Request,
    body: SourceRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    name = body.name.strip()
    if not name:
        raise ApiProblem(422, "SOURCE_NAME_REQUIRED", "来源名称不能为空")
    if body.source_id is None:
        existing = next(
            (item for item in db.list_rows("sources", user_id) if item["name"] == name),
            None,
        )
        if existing:
            return envelope(request, public_source(existing))
    normalized = body.model_copy(update={"name": name})
    try:
        saved = db.upsert("sources", user_id, normalized.model_dump())
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, public_source(saved))


@router.post("/api/v1/holding-imports", response_model=SuccessEnvelope, tags=["web-data"])
async def create_holding_import(
    request: Request,
    body: HoldingImportCreateRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    for document_id in body.document_ids:
        if not db.owns("ocr_documents", user_id, document_id):
            raise ApiProblem(404, "NOT_FOUND", "记录不存在或无权访问")

    def operation() -> dict[str, Any]:
        saved = db.upsert(
            "holding_imports",
            user_id,
            {
                "id": f"imp_{uuid4().hex}",
                "status": "DRAFT",
                "payload": {"document_ids": body.document_ids, "rows": body.rows, "plan": []},
            },
        )
        return public_import(saved)

    try:
        saved = db.idempotent(
            user_id,
            "holding-import-create",
            body.client_request_id,
            body.model_dump(mode="json"),
            operation,
        )
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.get("/api/v1/holding-imports/{batch_id}", response_model=SuccessEnvelope, tags=["web-data"])
async def get_holding_import(
    request: Request,
    batch_id: str,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    row = next(
        (item for item in db.list_rows("holding_imports", user_id) if item["id"] == batch_id),
        None,
    )
    if row is None:
        raise NotFound
    return envelope(request, public_import(row))


@router.post(
    "/api/v1/holding-imports/{batch_id}:preview",
    response_model=SuccessEnvelope,
    tags=["web-data"],
)
async def preview_holding_import(
    request: Request,
    batch_id: str,
    body: HoldingImportPreviewRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    row = next(
        (item for item in db.list_rows("holding_imports", user_id) if item["id"] == batch_id),
        None,
    )
    if row is None:
        raise NotFound
    if row["version"] != body.version:
        raise store_problem(StoreConflict())
    current_holdings = db.list_rows("holdings", user_id)
    applied_references = {
        (item.get("document_id"), item.get("row_id")): item
        for item in db.list_rows("import_references", user_id)
        if item.get("document_id") and item.get("row_id")
    }
    plan = []
    for index, import_row in enumerate(body.rows, 1):
        row_id = import_row.get("row_id") or f"row-{index}"
        applied = applied_references.get((import_row.get("document_id"), row_id))
        if applied:
            plan.append(
                {
                    "row_id": row_id,
                    "action": "SKIP",
                    "candidate_holding_ids": [applied["holding_id"]]
                    if applied.get("holding_id")
                    else [],
                    "needs_confirmation": False,
                    "already_applied": True,
                }
            )
            continue
        instrument_id = import_row.get("instrument_id")
        currency = str(import_row.get("currency") or "").upper()
        candidates = [
            holding["id"]
            for holding in current_holdings
            if instrument_id
            and holding.get("instrument_id") == instrument_id
            and holding.get("currency") == currency
        ]
        plan.append(
            {
                "row_id": row_id,
                "action": str(import_row.get("action") or "ADD").upper(),
                "candidate_holding_ids": candidates,
                "needs_confirmation": len(candidates) > 0,
                "already_applied": False,
            }
        )
    payload = {
        **(row.get("payload") or {}),
        "rows": body.rows,
        "plan": plan,
        "status": "READY",
    }
    try:
        saved = db.upsert(
            "holding_imports",
            user_id,
            {"id": batch_id, "status": "READY", "payload": payload, "version": body.version},
        )
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, public_import(saved))


@router.post(
    "/api/v1/holding-imports/{batch_id}:commit",
    response_model=SuccessEnvelope,
    tags=["web-data"],
)
async def commit_holding_import(
    request: Request,
    batch_id: str,
    body: HoldingImportCommitRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    batch = next(
        (item for item in db.list_rows("holding_imports", user_id) if item["id"] == batch_id),
        None,
    )
    if batch is None:
        raise NotFound
    owned_sources = {item["id"] for item in db.list_rows("sources", user_id)}
    owned_accounts = {item["id"] for item in db.list_rows("accounts", user_id)}
    owned_documents = {item["id"] for item in db.list_rows("ocr_documents", user_id)}
    items: list[dict[str, Any]] = []
    references: list[dict[str, Any]] = []
    for import_row in body.rows:
        action = str(import_row.get("action") or "ADD").upper()
        row_id = str(import_row.get("row_id") or f"row-{len(references) + 1}")
        if action not in {"ADD", "UPDATE", "SKIP"}:
            raise ApiProblem(422, "IMPORT_DECISION_INVALID", "导入行决定无效")
        document_id = import_row.get("document_id")
        if document_id and document_id not in owned_documents:
            raise NotFound
        if action == "SKIP":
            references.append(
                {
                    "document_id": document_id,
                    "row_id": row_id,
                    "image_sha256": import_row.get("image_sha256"),
                    "snapshot_date": import_row.get("snapshot_date"),
                    "decision": "SKIP",
                }
            )
            continue
        if action == "UPDATE" and not import_row.get("holding_id"):
            raise ApiProblem(422, "IMPORT_DECISION_REQUIRED", "更新持仓必须指定目标记录")
        if import_row.get("source_id") and import_row["source_id"] not in owned_sources:
            raise NotFound
        if import_row.get("account_id") and import_row["account_id"] not in owned_accounts:
            raise NotFound
        if action == "UPDATE" and not db.owns("holdings", user_id, str(import_row["holding_id"])):
            raise NotFound
        data = {
            "holding_id": import_row.get("holding_id") if action == "UPDATE" else None,
            "account_id": import_row.get("account_id"),
            "source_id": import_row.get("source_id"),
            "snapshot_date": import_row.get("snapshot_date"),
            "instrument_id": import_row.get("instrument_id"),
            "name": import_row.get("name"),
            "asset_type": str(import_row.get("asset_type") or "OTHER").upper(),
            "quantity": str(import_row.get("quantity")),
            "average_cost": (
                None if import_row.get("average_cost") is None else str(import_row["average_cost"])
            ),
            "currency": str(import_row.get("currency") or "").upper(),
            "metadata": dict(import_row.get("metadata") or {}),
            "version": import_row.get("version"),
        }
        if import_row.get("current_price") is not None:
            data["metadata"]["current_price"] = import_row["current_price"]
        if import_row.get("risk_bucket") is not None:
            data["metadata"]["risk_bucket"] = import_row["risk_bucket"]
        try:
            item = HoldingItem.model_validate(data)
        except ValidationError as exc:
            raise ApiProblem(
                422,
                "IMPORT_ROW_INVALID",
                "导入行信息不完整或格式无效",
                details=exc.errors(),
            ) from exc
        items.append(item.model_dump())
        references.append(
            {
                "document_id": document_id,
                "row_id": row_id,
                "holding_id": item.holding_id,
                "image_sha256": import_row.get("image_sha256"),
                "snapshot_date": item.snapshot_date,
                "decision": action,
            }
        )
    try:
        result = db.commit_holding_import(
            user_id,
            batch_id,
            body.version,
            body.client_request_id,
            body.model_dump(mode="json"),
            items,
            references,
        )
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(
        request,
        {
            "batch_id": result["batch_id"],
            "status": result["status"],
            "version": result["version"],
            "items": [public_holding(item) for item in result["items"]],
        },
    )


@router.get("/api/v1/accounts", response_model=SuccessEnvelope, tags=["web-data"])
async def list_accounts(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    return envelope(
        request, {"items": [public_account(x) for x in db.list_rows("accounts", user_id)]}
    )


@router.post("/api/v1/accounts", response_model=SuccessEnvelope, tags=["web-data"])
async def upsert_account(
    request: Request,
    body: AccountRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    try:
        if body.account_id and not db.owns("accounts", user_id, body.account_id):
            raise NotFound
        if body.account_id:
            current = next(
                item for item in db.list_rows("accounts", user_id) if item["id"] == body.account_id
            )
            has_dependencies = bool(
                db.list_rows("holdings", user_id, account_id=body.account_id)
            ) or any(
                item["account_id"] == body.account_id
                or item.get("to_account_id") == body.account_id
                for item in db.list_rows("transactions", user_id)
            )
            if has_dependencies and current["currency"] != body.currency:
                raise ApiProblem(
                    409,
                    "ACCOUNT_CURRENCY_LOCKED",
                    "账户已有持仓或流水，不能修改币种",
                )
        saved = db.upsert("accounts", user_id, body.model_dump())
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, public_account(saved))


@router.post(
    "/api/v1/accounts/{account_id}:archive", response_model=SuccessEnvelope, tags=["web-data"]
)
async def archive_account(
    request: Request,
    account_id: str,
    body: ArchiveRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    if db.list_rows("holdings", user_id, account_id=account_id):
        raise ApiProblem(409, "ACCOUNT_HAS_HOLDINGS", "请先归档该账户下的持仓")
    if any(
        item["account_id"] == account_id or item.get("to_account_id") == account_id
        for item in db.list_rows("transactions", user_id)
    ):
        raise ApiProblem(409, "ACCOUNT_HAS_TRANSACTIONS", "请先归档该账户关联的流水")
    try:
        saved = db.archive("accounts", user_id, account_id, body.version)
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.get("/api/v1/holdings", response_model=SuccessEnvelope, tags=["web-data"])
async def list_holdings(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
    account_id: str | None = None,
) -> SuccessEnvelope:
    return envelope(
        request,
        {
            "items": [
                public_holding(x) for x in db.list_rows("holdings", user_id, account_id=account_id)
            ]
        },
    )


@router.post("/api/v1/holdings:batch-upsert", response_model=SuccessEnvelope, tags=["web-data"])
async def upsert_holdings(
    request: Request,
    body: HoldingsBatchRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    def operation() -> dict[str, Any]:
        accounts = {item["id"]: item for item in db.list_rows("accounts", user_id)}
        sources = {item["id"]: item for item in db.list_rows("sources", user_id)}
        for item in body.items:
            if item.account_id is not None and item.account_id not in accounts:
                raise NotFound
            if item.source_id is not None and item.source_id not in sources:
                raise NotFound
            if item.holding_id and not db.owns("holdings", user_id, item.holding_id):
                raise NotFound
        saved = db.idempotent_batch_upsert(
            "holdings",
            user_id,
            "holdings",
            body.client_request_id,
            body.model_dump(mode="json"),
            [item.model_dump() for item in body.items],
        )
        return {"items": [public_holding(item) for item in saved]}

    try:
        saved = operation()
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.post(
    "/api/v1/holdings/{holding_id}:archive", response_model=SuccessEnvelope, tags=["web-data"]
)
async def archive_holding(
    request: Request,
    holding_id: str,
    body: ArchiveRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    if any(item.get("holding_id") == holding_id for item in db.list_rows("transactions", user_id)):
        raise ApiProblem(409, "HOLDING_HAS_TRANSACTIONS", "请先归档该持仓关联的流水")
    try:
        saved = db.archive("holdings", user_id, holding_id, body.version)
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.get("/api/v1/transactions", response_model=SuccessEnvelope, tags=["web-data"])
async def list_transactions(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
    account_id: str | None = None,
) -> SuccessEnvelope:
    return envelope(
        request,
        {
            "items": [
                public_transaction(x)
                for x in db.list_rows("transactions", user_id, account_id=account_id)
            ]
        },
    )


@router.post("/api/v1/transactions:batch-upsert", response_model=SuccessEnvelope, tags=["web-data"])
async def upsert_transactions(
    request: Request,
    body: TransactionsBatchRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    def operation() -> dict[str, Any]:
        accounts = {item["id"]: item for item in db.list_rows("accounts", user_id)}
        holdings = {item["id"]: item for item in db.list_rows("holdings", user_id)}
        sources = {item["id"]: item for item in db.list_rows("sources", user_id)}
        for item in body.items:
            source = accounts.get(item.account_id) if item.account_id else None
            if item.account_id and source is None:
                raise NotFound
            if item.transaction_id and not db.owns("transactions", user_id, item.transaction_id):
                raise NotFound
            if item.source_id is not None and item.source_id not in sources:
                raise NotFound
            if item.holding_id:
                linked_holding = holdings.get(item.holding_id)
                if linked_holding is None:
                    raise ApiProblem(
                        400,
                        "HOLDING_NOT_FOUND",
                        "交易关联的持仓不存在",
                    )
            if item.transaction_type == "TRANSFER":
                target = accounts.get(item.to_account_id or "")
                if target is None:
                    raise NotFound
                if source is None or target["currency"] != source["currency"]:
                    raise ApiProblem(
                        400, "TRANSFER_CURRENCY_MISMATCH", "暂不支持不同币种账户间直接转账"
                    )
        saved = db.idempotent_batch_upsert(
            "transactions",
            user_id,
            "transactions",
            body.client_request_id,
            body.model_dump(mode="json"),
            [item.model_dump() for item in body.items],
        )
        return {"items": [public_transaction(item) for item in saved]}

    try:
        saved = operation()
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


@router.get("/api/v1/ledger/summary", response_model=SuccessEnvelope, tags=["web-data"])
async def get_ledger_summary(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
    month: Annotated[str | None, Query(pattern=r"^\d{4}-(0[1-9]|1[0-2])$")] = None,
) -> SuccessEnvelope:
    selected_month = month or datetime.now(SHANGHAI).strftime("%Y-%m")
    result = ledger_summary(db, user_id, selected_month)
    result["price_refresh"] = request.app.state.holding_prices.status(user_id)
    return envelope(request, result)


@router.post("/api/v1/holdings:refresh", response_model=SuccessEnvelope, tags=["web-data"])
async def refresh_holding_prices(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
) -> SuccessEnvelope:
    return envelope(request, await request.app.state.holding_prices.refresh(user_id))


@router.post(
    "/api/v1/transactions/{transaction_id}:archive",
    response_model=SuccessEnvelope,
    tags=["web-data"],
)
async def archive_transaction(
    request: Request,
    transaction_id: str,
    body: ArchiveRequest,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    try:
        saved = db.archive("transactions", user_id, transaction_id, body.version)
    except (StoreConflict, NotFound) as exc:
        raise store_problem(exc) from exc
    return envelope(request, saved)


# These two endpoints take no credentials and write to SQLite, so they are plain `def` routes
# (run in the threadpool, never on the event loop) and are throttled before touching the database.
@router.post("/api/v1/auth/session", response_model=SuccessEnvelope, tags=["auth"])
def create_session(
    request: Request,
    body: SessionRequest,
    manager: Annotated[SessionManager, Depends(sessions)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    from finunity_server.auth_api import client_ip, limit

    settings = request.app.state.settings
    if settings.maintenance:
        raise ApiProblem(503, "MAINTENANCE", "服务维护中", retryable=True)
    subject = "ins_" + hashlib.sha256(body.installation_id.encode()).hexdigest()
    limit(
        db,
        [
            ("session-ip", client_ip(request), settings.session_ip_hourly_limit, 3600),
            ("session-installation", subject, settings.session_installation_hourly_limit, 3600),
            ("session-global", "global", settings.session_global_daily_limit, 86400),
        ],
    )
    return envelope(request, manager.issue(subject, "INSTALLATION"))


@router.post("/api/v1/auth/session:refresh", response_model=SuccessEnvelope, tags=["auth"])
def refresh_session(
    request: Request,
    body: RefreshRequest,
    manager: Annotated[SessionManager, Depends(sessions)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    from finunity_server.auth_api import client_ip, limit

    limit(
        db,
        [
            (
                "session-refresh-ip",
                client_ip(request),
                request.app.state.settings.session_refresh_ip_hourly_limit,
                3600,
            )
        ],
    )
    data = manager.rotate(body.refresh_token)
    if data is None:
        raise ApiProblem(401, "REFRESH_TOKEN_INVALID", "Refresh Token 无效、过期或已使用")
    return envelope(request, data)


@router.get(
    "/api/v1/app/config", response_model=SuccessEnvelope, dependencies=protected, tags=["app"]
)
async def app_config(
    request: Request,
    platform: Annotated[str, Query(pattern="^ANDROID$")],
    version_code: Annotated[int, Query(gt=0)],
) -> SuccessEnvelope:
    settings = request.app.state.settings
    warnings = []
    if version_code < settings.minimum_supported_version_code:
        warnings.append({"code": "CLIENT_UPDATE_REQUIRED", "message": "客户端版本过低"})
    return envelope(
        request,
        {
            "minimum_supported_version_code": settings.minimum_supported_version_code,
            "latest_version_code": settings.latest_version_code,
            "maintenance": settings.maintenance,
            "features": {
                "market_sync": True,
                "fund_nav": True,
                "remote_ocr": settings.ocr_enabled,
                "market_history": True,
                "market_metrics": True,
            },
            "limits": {
                "market_sync_instruments": 100,
                "ocr_image_bytes": settings.max_image_bytes,
                "ocr_image_megapixels": settings.max_image_megapixels,
            },
            "privacy_policy_url": settings.privacy_policy_url,
        },
        warnings=warnings,
    )


@router.get(
    "/api/v1/instruments/search",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["instruments"],
)
async def search_instruments(
    request: Request,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
    q: Annotated[str, Query(min_length=1, max_length=100)],
    types: str = "",
    markets: str = "",
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
    cursor: str | None = None,
) -> SuccessEnvelope:
    items = data_provider.search(
        q, set(filter(None, types.split(","))), set(filter(None, markets.split(","))), limit
    )
    return envelope(request, {"items": items, "next_cursor": None})


@router.post(
    "/api/v1/instruments/resolve",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["instruments"],
)
async def resolve_instruments(
    request: Request,
    body: ResolveRequest,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    results = []
    partial = False
    for query in body.queries:
        candidates = data_provider.resolve(
            query.text,
            query.type_hint.value if query.type_hint else None,
            query.market_hint,
        )
        if len(candidates) == 1:
            results.append(
                {
                    "client_ref": query.client_ref,
                    "status": "RESOLVED",
                    "instrument": candidates[0].public(),
                    "confidence": 1.0,
                    "candidates": [],
                    "review_reasons": [],
                }
            )
        elif len(candidates) > 1:
            partial = True
            results.append(
                {
                    "client_ref": query.client_ref,
                    "status": "AMBIGUOUS",
                    "instrument": None,
                    "confidence": 0.0,
                    "candidates": [x.public() for x in candidates],
                    "review_reasons": ["代码对应多个资产，请确认市场或类型"],
                }
            )
        else:
            partial = True
            results.append(
                {
                    "client_ref": query.client_ref,
                    "status": "NOT_FOUND",
                    "instrument": None,
                    "confidence": 0.0,
                    "candidates": [],
                    "review_reasons": ["未找到匹配证券"],
                }
            )
    return envelope(request, {"items": results}, partial=partial)


def quote_items(
    ids: list[str], data_provider: ReferenceDataProvider
) -> tuple[list[dict[str, Any]], bool]:
    items = []
    partial = False
    for instrument_id in ids:
        quote = data_provider.quote(instrument_id)
        if quote:
            items.append(quote)
        else:
            partial = True
            items.append(
                {
                    "instrument_id": instrument_id,
                    "status": "NOT_FOUND",
                    "last": None,
                    "previous_close": None,
                    "error": item_error("NOT_FOUND", "未找到行情"),
                }
            )
    return items, partial


@router.post(
    "/api/v1/market/quotes/batch",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["market"],
)
async def quotes_batch(
    request: Request,
    body: QuotesBatchRequest,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    items, partial = quote_items(body.instrument_ids, data_provider)
    return envelope(request, {"items": items}, partial=partial)


def nav_items(
    body: FundNavBatchRequest, data_provider: ReferenceDataProvider
) -> tuple[list[dict[str, Any]], bool]:
    results = []
    partial = False
    for fund in body.funds:
        code = fund.fund_code or (fund.instrument_id or "").removeprefix("CNFUND:")
        nav = data_provider.nav(code)
        if nav:
            results.append({"client_ref": fund.client_ref, "status": "OK", **nav, "error": None})
        else:
            partial = True
            results.append(
                {
                    "client_ref": fund.client_ref,
                    "status": "NOT_FOUND",
                    "instrument_id": fund.instrument_id,
                    "fund_code": code,
                    "unit_nav": None,
                    "error": item_error("NOT_FOUND", "未找到基金净值"),
                }
            )
    return results, partial


@router.post(
    "/api/v1/funds/nav/batch",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["funds"],
)
async def funds_nav_batch(
    request: Request,
    body: FundNavBatchRequest,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    items, partial = nav_items(body, data_provider)
    return envelope(request, {"items": items}, partial=partial)


@router.get(
    "/api/v1/funds/{fund_code}",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["funds"],
)
async def fund_detail(
    request: Request,
    fund_code: str,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    instrument = data_provider.instruments.get(f"CNFUND:{fund_code}")
    if not instrument:
        raise ApiProblem(404, "FUND_NOT_FOUND", "未找到基金")
    return envelope(
        request,
        {
            **instrument.public(),
            "fund_type": instrument.fund_type or "OTHER",
            "qdii": False,
            "exchange_traded": instrument.type == "LOF",
            "benchmark": None,
            "management_company": None,
            "inception_date": None,
            "purchase_status": "UNKNOWN",
            "purchase_limit": None,
            "suggested_bucket": "BALANCED",
            "source": data_provider.source,
            "as_of_date": "2026-08-28",
        },
    )


def fx_items(
    body: FxBatchRequest, data_provider: ReferenceDataProvider
) -> tuple[list[dict[str, Any]], bool]:
    results = []
    partial = False
    for pair in body.pairs:
        rate = data_provider.fx_rate(pair.base, pair.quote, body.rate_type)
        if rate:
            results.append({"client_ref": pair.client_ref, "status": "OK", **rate, "error": None})
        else:
            partial = True
            results.append(
                {
                    "client_ref": pair.client_ref,
                    "status": "NOT_FOUND",
                    "pair": f"{pair.base}/{pair.quote}",
                    "rate": None,
                    "error": item_error("FX_RATE_NOT_FOUND", "缺少汇率，禁止按 1:1 换算"),
                }
            )
    return results, partial


@router.post(
    "/api/v1/fx/rates/batch", response_model=SuccessEnvelope, dependencies=protected, tags=["fx"]
)
async def fx_batch(
    request: Request,
    body: FxBatchRequest,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    items, partial = fx_items(body, data_provider)
    return envelope(request, {"items": items}, partial=partial)


@router.post(
    "/api/v1/market/sync", response_model=SuccessEnvelope, dependencies=protected, tags=["market"]
)
async def market_sync(
    request: Request,
    body: MarketSyncRequest,
    data_provider: Annotated[ReferenceDataProvider, Depends(provider)],
) -> SuccessEnvelope:
    credentials = request.headers.get("Authorization", "").split(" ", 1)[-1]
    cache_key = f"{sessions(request).subject(credentials)}:{body.client_request_id}"
    fingerprint = hashlib.sha256(body.model_dump_json().encode()).hexdigest()
    cached = request.app.state.sync_cache.get(cache_key)
    if cached is not None:
        if cached["fingerprint"] != fingerprint:
            raise ApiProblem(409, "IDEMPOTENCY_CONFLICT", "相同 client_request_id 的请求内容不同")
        return envelope(
            request, cached["data"], warnings=cached["warnings"], partial=cached["partial"]
        )
    instruments = []
    partial = False
    for requested in body.instruments:
        instrument = data_provider.instruments.get(requested.instrument_id or "")
        if instrument is None and requested.input_code:
            matches = data_provider.resolve(requested.input_code, requested.asset_type.value, None)
            instrument = matches[0] if len(matches) == 1 else None
        if instrument is None:
            partial = True
            instruments.append(
                {
                    "client_ref": requested.client_ref,
                    "status": "NOT_FOUND",
                    "instrument": None,
                    "value": None,
                    "error": item_error("NOT_FOUND", "无法解析资产或取得数据"),
                }
            )
            continue
        if instrument.type in {"FUND", "LOF"} and instrument.fund_code:
            value = data_provider.nav(instrument.fund_code)
            normalized = (
                None
                if value is None
                else {
                    "value_type": "OFFICIAL_NAV",
                    "current": value["unit_nav"],
                    "previous_close": value["previous_unit_nav"],
                    "value_date": value["nav_date"],
                    "source_time": value["source_time"],
                    "received_at": value["received_at"],
                    "quality": "OFFICIAL",
                    "delay_seconds": None,
                    "source": value["source"],
                }
            )
        else:
            quote = data_provider.quote(instrument.instrument_id)
            normalized = (
                None
                if quote is None
                else {
                    "value_type": "MARKET_PRICE",
                    "current": quote["last"],
                    "previous_close": quote["previous_close"],
                    "value_date": "2026-08-28",
                    "source_time": quote["source_time"],
                    "received_at": quote["source_time"],
                    "quality": quote["quality"],
                    "delay_seconds": None,
                    "source": quote["source"],
                }
            )
        if normalized is None:
            partial = True
            instruments.append(
                {
                    "client_ref": requested.client_ref,
                    "status": "PROVIDER_ERROR",
                    "instrument": instrument.public(),
                    "value": None,
                    "error": item_error("PROVIDER_ERROR", "数据提供器未返回数据", True),
                }
            )
        else:
            instruments.append(
                {
                    "client_ref": requested.client_ref,
                    "status": "OK",
                    "instrument": instrument.public(),
                    "value": normalized,
                    "error": None,
                }
            )
    fx_request = FxBatchRequest(pairs=body.fx_pairs, rate_type="MID")
    rates, fx_partial = fx_items(fx_request, data_provider) if body.fx_pairs else ([], False)
    partial = partial or fx_partial
    data = {"instruments": instruments, "fx_rates": rates}
    warnings = [{"code": "REFERENCE_DATA", "message": "当前使用内置联调数据，不可用于生产交易决策"}]
    request.app.state.sync_cache[cache_key] = {
        "fingerprint": fingerprint,
        "data": data,
        "warnings": warnings,
        "partial": partial,
    }
    return envelope(request, data, warnings=warnings, partial=partial)


def decimal_string(value: float | None) -> str | None:
    if value is None:
        return None
    return format(Decimal(str(value)), "f")


def check_image(content: bytes, media_type: str, settings: Settings) -> None:
    """Validate an upload from its header before decoding any pixels.

    A tiny file can declare enormous dimensions (a 236 KB PNG may be 81 megapixels), so the
    format, minimum size and pixel budget are judged from the header; only then is the
    image decoded once, to reject truncated or corrupt files.
    """
    try:
        with Image.open(io.BytesIO(content)) as loaded:
            width, height = loaded.size  # header only, nothing is decoded yet
            if loaded.format != IMAGE_TYPES[media_type]:
                raise ApiProblem(400, "IMAGE_TYPE_MISMATCH", "图片内容与 Content-Type 不一致")
            if min(width, height) < settings.minimum_image_dimension:
                raise ApiProblem(422, "IMAGE_TOO_SMALL", "图片尺寸小于 480×480")
            if width * height > settings.max_image_megapixels * 1_000_000:
                raise ApiProblem(413, "IMAGE_TOO_LARGE", "图片像素超过限制")
            loaded.load()
    except Image.DecompressionBombError as exc:
        raise ApiProblem(413, "IMAGE_TOO_LARGE", "图片像素超过限制") from exc
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError) as exc:
        raise ApiProblem(400, "INVALID_IMAGE", "上传内容不是有效图片") from exc


@router.post(
    "/api/v1/ocr/holdings:parse",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["ocr"],
)
async def parse_holdings(
    request: Request,
    image: Annotated[UploadFile, File()],
    client_request_id: Annotated[str, Form(min_length=1, max_length=100)],
    parser: Annotated[HoldingRecognizer, Depends(recognizer)],
    user_id: Annotated[str, Depends(require_subject)],
    db: Annotated[SQLiteStore, Depends(store)],
    locale: Annotated[str, Form(max_length=20)] = "zh-CN",
    default_currency: Annotated[str | None, Form(pattern=r"^[A-Z]{3}$")] = None,
    broker_hint: Annotated[str | None, Form(max_length=200)] = None,
    parse_mode: Annotated[str, Form(pattern=r"^HOLDINGS$")] = "HOLDINGS",
) -> SuccessEnvelope:
    settings = request.app.state.settings
    if not settings.ocr_enabled:
        await image.close()
        raise ApiProblem(503, "OCR_DISABLED", "截图识别暂不可用", retryable=False)
    media_type = (image.content_type or "").lower()
    if media_type not in IMAGE_TYPES:
        raise ApiProblem(415, "UNSUPPORTED_MEDIA_TYPE", "仅支持 JPEG、PNG 和 WebP")
    content = await image.read(settings.max_image_bytes + 1)
    await image.close()
    if len(content) > settings.max_image_bytes:
        raise ApiProblem(413, "IMAGE_TOO_LARGE", "图片超过大小限制")
    digest = hashlib.sha256(content).hexdigest()
    fingerprint = hashlib.sha256(
        json.dumps([digest, locale, default_currency, broker_hint, parse_mode]).encode()
    ).hexdigest()
    cache_key = f"{user_id}:{client_request_id}:{fingerprint}"
    cached = request.app.state.ocr_cache.get(cache_key)
    if cached:
        return envelope(request, cached)
    if user_id.startswith("usr_"):
        try:
            persisted = db.ocr_result(user_id, client_request_id, digest)
        except (StoreConflict, NotFound) as exc:
            raise store_problem(exc) from exc
        if persisted:
            return envelope(request, persisted)
    check_image(content, media_type, settings)
    from finunity_server.auth_api import client_ip

    if user_id.startswith("usr_"):
        budgets = [
            ("ocr-user", user_id, settings.ocr_user_daily_limit, 86400),
            ("ocr-user-global", "global", settings.ocr_user_global_daily_limit, 86400),
        ]
    else:
        budgets = [
            ("ocr-install", user_id, settings.ocr_installation_daily_limit, 86400),
            ("ocr-ip", client_ip(request), settings.ocr_ip_daily_limit, 86400),
            ("ocr-anonymous-global", "global", settings.ocr_anonymous_global_daily_limit, 86400),
        ]
    remaining = db.consume_limits(budgets)
    if remaining is not None:
        raise ApiProblem(
            429,
            "OCR_QUOTA_EXCEEDED",
            "今日识别次数已用完，请明日再试",
            retryable=True,
            retry_after_seconds=remaining,
        )
    try:
        result = await parser.recognize(
            image_bytes=content,
            media_type=media_type,
            account_name=broker_hint,
            currency_hint=default_currency,
        )
    except RecognitionNotConfiguredError as exc:
        raise ApiProblem(503, "MODEL_NOT_CONFIGURED", str(exc), retryable=False) from exc
    except RecognitionTimeoutError as exc:
        raise ApiProblem(504, "MODEL_TIMEOUT", str(exc), retryable=True) from exc
    except (RecognitionUpstreamError, RecognitionInvalidOutputError) as exc:
        raise ApiProblem(502, "MODEL_ERROR", str(exc), retryable=True) from exc
    if not result.holdings:
        raise ApiProblem(422, "HOLDINGS_NOT_FOUND", "图片可读取，但无法解析出持仓")
    rows = []
    data_provider = provider(request)
    for index, holding in enumerate(result.holdings, 1):
        matches = (
            data_provider.resolve(holding.security_code or "", None, None)
            if holding.security_code
            else []
        )
        resolved = matches[0] if len(matches) == 1 else None
        security_status = (
            "RESOLVED" if resolved else ("AMBIGUOUS" if len(matches) > 1 else "NOT_FOUND")
        )
        review_reasons = list(holding.review_reasons)
        if (
            holding.quantity
            and holding.current_price is not None
            and holding.market_value is not None
        ):
            expected = Decimal(str(holding.quantity)) * Decimal(str(holding.current_price))
            actual_value = Decimal(str(holding.market_value))
            if actual_value and abs(expected - actual_value) / abs(actual_value) > Decimal("0.01"):
                review_reasons.append("VALUE_MISMATCH")
        rows.append(
            {
                "row_id": f"row-{index}",
                "raw_text": None,
                "name": holding.name,
                "security_code": {
                    "raw": holding.security_code,
                    "normalized": resolved.canonical_symbol if resolved else holding.security_code,
                    "instrument_id": resolved.instrument_id if resolved else None,
                    "market": resolved.market if resolved else None,
                    "status": security_status,
                    "confidence": holding.confidence if resolved else 0.0,
                    "candidates": [x.public() for x in matches] if len(matches) > 1 else [],
                    "needs_confirmation": resolved is None,
                },
                "asset_type": holding.asset_type.value,
                "quantity": decimal_string(holding.quantity),
                "quantity_unit": holding.quantity_unit,
                "quantity_kind": holding.quantity_kind,
                "current_price": decimal_string(holding.current_price),
                "market_value": decimal_string(holding.market_value),
                "total_cost": decimal_string(holding.total_cost)
                if holding.cost_source.value != "NOT_SHOWN"
                else None,
                "currency": holding.currency,
                "confidence": holding.confidence,
                "field_confidence": {},
                "review_reasons": list(dict.fromkeys(review_reasons)),
                "bounding_box": None,
            }
        )
    document_id = f"doc_{uuid4().hex}"
    payload = {
        "document_id": document_id,
        "document_type": "HOLDINGS",
        "image_sha256": digest,
        "broker": {
            "name": result.detected_provider or broker_hint,
            "confidence": 0.8 if result.detected_provider else 0.0,
        },
        "as_of_date": result.as_of_date.isoformat() if result.as_of_date else None,
        "rows": rows,
        "unparsed_blocks": [],
        "warnings": result.warnings,
    }
    if user_id.startswith("usr_"):
        payload = db.save_ocr(user_id, client_request_id, digest, payload)
    request.app.state.ocr_cache[cache_key] = payload
    request.app.state.ocr_documents[f"{user_id}:{document_id}"] = cache_key
    return envelope(
        request, payload, partial=any(x["security_code"]["needs_confirmation"] for x in rows)
    )


@router.post(
    "/api/v1/ocr/documents/{document_id}:archive",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["ocr"],
)
async def archive_ocr_document(
    request: Request,
    document_id: str,
    user_id: Annotated[str, Depends(require_subject)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    cache_key = request.app.state.ocr_documents.pop(f"{user_id}:{document_id}", None)
    if cache_key:
        request.app.state.ocr_cache.pop(cache_key, None)
    stored = next(
        (item for item in db.list_rows("ocr_documents", user_id) if item["id"] == document_id),
        None,
    )
    if not stored and not cache_key:
        raise ApiProblem(404, "NOT_FOUND", "记录不存在或无权访问")
    if stored:
        db.archive("ocr_documents", user_id, document_id, stored["version"])
    return envelope(
        request, DeleteResponse(deleted=stored is not None or cache_key is not None).model_dump()
    )


@router.get(
    "/api/v1/ocr/documents",
    response_model=SuccessEnvelope,
    tags=["ocr"],
)
async def list_ocr_documents(
    request: Request,
    user_id: Annotated[str, Depends(require_user)],
    db: Annotated[SQLiteStore, Depends(store)],
) -> SuccessEnvelope:
    items = []
    for row in reversed(db.list_rows("ocr_documents", user_id)):
        payload = row["payload"]
        items.append(
            {
                "document_id": row["id"],
                "created_at": row["created_at"],
                "as_of_date": payload.get("as_of_date"),
                "broker": payload.get("broker"),
                "row_count": len(payload.get("rows") or []),
                "warnings": payload.get("warnings") or [],
                "version": row["version"],
            }
        )
    return envelope(request, {"items": items})


@router.get(
    "/api/v1/market/history",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["market"],
)
async def market_history(
    request: Request,
    instrument_id: str,
    start_date: date,
    end_date: date,
    interval: str = "1d",
    adjustment: str = "TOTAL_RETURN",
    cursor: str | None = None,
) -> SuccessEnvelope:
    if start_date > end_date:
        raise ApiProblem(400, "INVALID_DATE_RANGE", "start_date 不能晚于 end_date")
    if adjustment not in {"NONE", "SPLIT_ADJUSTED", "TOTAL_RETURN"}:
        raise ApiProblem(400, "INVALID_ADJUSTMENT", "不支持的复权口径")
    quote = provider(request).quote(instrument_id)
    reference_date = date(2026, 8, 28)
    points = (
        []
        if not quote or not start_date <= reference_date <= end_date
        else [
            {
                "date": "2026-08-28",
                "open": quote["open"],
                "high": quote["high"],
                "low": quote["low"],
                "close": quote["last"],
                "previous_close": quote["previous_close"],
                "adjusted_close": quote["last"],
                "volume": quote["volume"],
                "currency": quote["currency"],
                "source": quote["source"],
            }
        ]
    )
    return envelope(
        request,
        {
            "instrument_id": instrument_id,
            "interval": interval,
            "adjustment": adjustment,
            "items": points,
            "next_cursor": None,
        },
    )


@router.post(
    "/api/v1/market/metrics/batch",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["market"],
)
async def market_metrics(request: Request, body: MetricsBatchRequest) -> SuccessEnvelope:
    items = []
    partial = False
    data_provider = provider(request)
    for instrument_id in body.instrument_ids:
        quote = data_provider.quote(instrument_id)
        if not quote:
            partial = True
            items.append(
                {
                    "instrument_id": instrument_id,
                    "status": "NOT_FOUND",
                    "error": item_error("NOT_FOUND", "未找到指标"),
                }
            )
        else:
            items.append(
                {
                    "instrument_id": instrument_id,
                    "status": "OK",
                    "as_of": quote["source_time"],
                    "pe_ttm": None,
                    "pb": None,
                    "dividend_yield": None,
                    "market_price": quote["last"],
                    "iopv_or_nav": None,
                    "premium_rate": None,
                    "purchase_status": None,
                    "purchase_limit": None,
                    "source": data_provider.source,
                    "quality": "STALE",
                    "warnings": [],
                }
            )
    return envelope(request, {"items": items}, partial=partial)


@router.get(
    "/api/v1/corporate-actions",
    response_model=SuccessEnvelope,
    dependencies=protected,
    tags=["market"],
)
async def corporate_actions(
    request: Request, instrument_id: str, since: date, cursor: str | None = None
) -> SuccessEnvelope:
    return envelope(request, {"instrument_id": instrument_id, "items": [], "next_cursor": None})


@router.get(
    "/api/v1/status", response_model=SuccessEnvelope, dependencies=protected, tags=["system"]
)
async def service_status(request: Request) -> SuccessEnvelope:
    settings = request.app.state.settings
    ocr_available = (
        (settings.ocr_provider == "qwen" and bool(settings.qwen_api_key))
        or (
            settings.ocr_provider == "baidu"
            and bool(settings.baidu_ocr_api_key)
            and bool(settings.baidu_ocr_secret_key)
        )
        or (settings.ocr_provider == "openai" and bool(settings.openai_api_key))
        or (
            settings.ocr_provider == "paddleocr"
            and bool(getattr(request.app.state.recognizer, "available", False))
        )
    )
    return envelope(
        request,
        {
            "status": "DEGRADED",
            "capabilities": {
                "stock_quotes": "DEGRADED",
                "fund_nav": "DEGRADED",
                "fx": "DEGRADED",
                "ocr": "AVAILABLE" if ocr_available else "UNAVAILABLE",
                "history": "DEGRADED",
            },
            "incidents": [
                {
                    "code": "REFERENCE_DATA_PROVIDER",
                    "message": "市场数据当前为内置联调数据，生产提供器尚未配置",
                    "started_at": utc_now(),
                }
            ],
        },
    )


@router.get("/health/live", tags=["system"])
async def live() -> dict[str, str]:
    return {"status": "ok"}


def ocr_configured(settings: Settings) -> bool:
    """True when the selected OCR provider has the credentials it needs (nothing is called)."""
    provider = settings.ocr_provider
    if provider == "qwen":
        return bool(settings.qwen_api_key)
    if provider == "openai":
        return bool(settings.openai_api_key)
    if provider == "baidu":
        return bool(settings.baidu_ocr_api_key and settings.baidu_ocr_secret_key)
    return True  # paddleocr runs locally


@router.get("/health/ready", tags=["system"])
def ready(request: Request, response: Response) -> dict[str, Any]:
    """Ready means this process can read and write its database.

    The upstream OCR provider is never called here because every call costs money: a missing
    key only degrades the service, an unusable database makes it not ready (HTTP 503).
    """
    settings = request.app.state.settings
    checks = {"application": "ok"}
    try:
        with request.app.state.store.connection() as conn:
            conn.execute("SELECT count(*) FROM sqlite_master").fetchone()
            conn.execute("BEGIN IMMEDIATE")  # the write lock is obtainable: not read-only or wedged
            conn.rollback()
        checks["database"] = "ok"
    except sqlite3.Error:
        checks["database"] = "error"
    if settings.ocr_enabled:
        checks["ocr"] = "configured" if ocr_configured(settings) else "not_configured"
    if checks["database"] != "ok":
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "not_ready", "checks": checks}
    degraded = checks.get("ocr") == "not_configured"
    return {"status": "degraded" if degraded else "ready", "checks": checks}


@router.get("/health/version", tags=["system"])
def version(request: Request) -> dict[str, str]:
    """Which build is running: the git sha and build time are injected when the image is built."""
    return {
        "service": "finunity-server",
        "version": request.app.version,
        "git_sha": os.environ.get("GIT_SHA", "unknown"),
        "build_time": os.environ.get("BUILD_TIME", "unknown"),
    }
