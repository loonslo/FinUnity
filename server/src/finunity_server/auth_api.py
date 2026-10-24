"""Public username authentication and explicit account lifecycle operations."""

import logging
import time
from typing import Annotated

from fastapi import APIRouter, Depends, Request

from finunity_server.contracts import (
    PasswordChangeRequest,
    PasswordConfirmRequest,
    RecoveryResetRequest,
    SuccessEnvelope,
    UsernameRegisterRequest,
    UsernameSetRequest,
)
from finunity_server.platform_service import SessionManager
from finunity_server.store import NotFound, SQLiteStore, StoreConflict
from finunity_server.v1_api import (
    ApiProblem,
    envelope,
    public_user,
    require_user,
    sessions,
    store,
)

router = APIRouter(tags=["account-security"])
logger = logging.getLogger(__name__)
DB = Annotated[SQLiteStore, Depends(store)]
Manager = Annotated[SessionManager, Depends(sessions)]
UserId = Annotated[str, Depends(require_user)]


def client_ip(request: Request) -> str:
    # Uvicorn resolves forwarding headers only from explicitly trusted proxy IPs.
    # Never read X-Forwarded-For ourselves.
    return request.client.host if request.client else "unknown"


def limit(db: SQLiteStore, budgets: list[tuple[str, str, int, int]]) -> None:
    remaining = db.consume_limits(budgets)
    if remaining is not None:
        raise ApiProblem(
            429,
            "RATE_LIMITED",
            "请求过于频繁，请稍后重试",
            retryable=True,
            retry_after_seconds=remaining,
        )


def auth_rate(request: Request, db: SQLiteStore, identity: str | None = None) -> None:
    settings, ip = request.app.state.settings, client_ip(request)
    limit(db, [("auth-cpu", ip, settings.auth_ip_minute_limit, 60)])
    for key in (f"ip:{ip}", identity):
        remaining = db.password_login_blocked(key) if key else None
        if remaining is not None:
            raise ApiProblem(
                429,
                "TOO_MANY_ATTEMPTS",
                "尝试次数过多，请稍后再试",
                retryable=True,
                retry_after_seconds=remaining,
            )


def auth_failure(request: Request, db: SQLiteStore, identity: str) -> None:
    db.record_password_login_failure(identity)
    db.record_password_login_failure(
        f"ip:{client_ip(request)}", request.app.state.settings.auth_ip_failure_limit
    )
    raise ApiProblem(401, "INVALID_CREDENTIALS", "用户名或密码错误")


def check_new_password(user: dict, password: str) -> None:
    if user["username"] and password.lower() == user["username"]:
        raise ApiProblem(400, "VALIDATION_ERROR", "密码不能与用户名相同")


def authenticated_result(
    request: Request,
    db: SQLiteStore,
    manager: SessionManager,
    user_id: str,
    code: str | None = None,
    auth_version: int | None = None,
) -> SuccessEnvelope:
    data = {**manager.issue(user_id, auth_version=auth_version), "user": public_user(db, user_id)}
    if code:
        data["recovery_code"] = code
    return envelope(request, data)


@router.get("/api/v1/auth/config", response_model=SuccessEnvelope)
def auth_config(request: Request) -> SuccessEnvelope:
    return envelope(
        request,
        {
            "email_auth_enabled": request.app.state.settings.email_auth_enabled,
            "ocr_enabled": request.app.state.settings.ocr_enabled,
        },
    )


@router.post("/api/v1/auth/register", response_model=SuccessEnvelope)
def register(
    request: Request, body: UsernameRegisterRequest, db: DB, manager: Manager
) -> SuccessEnvelope:
    settings, ip = request.app.state.settings, client_ip(request)
    limit(
        db,
        [
            ("register-hour", ip, settings.registration_hourly_limit, 3600),
            ("register-day", ip, settings.registration_daily_limit, 86400),
            ("auth-cpu", ip, settings.auth_ip_minute_limit, 60),
        ],
    )
    try:
        user, code = db.create_username_user(body.username, body.password, body.display_name)
    except StoreConflict as exc:
        raise ApiProblem(409, "USERNAME_REGISTERED", "该用户名已注册") from exc
    db.consume_limits([("registrations", "global", 2**31, 86400)])
    with db.connection() as conn:
        row = conn.execute(
            "SELECT count FROM usage_counters WHERE scope='registrations' AND bucket=?",
            (int(time.time()) // 86400,),
        ).fetchone()
    if row and row["count"] >= settings.registration_alert_threshold:
        logger.warning(
            "REGISTRATION_DAILY_ALERT count=%s threshold=%s",
            row["count"],
            settings.registration_alert_threshold,
        )
    return authenticated_result(request, db, manager, user["id"], code, user["auth_version"])


@router.post("/api/v1/auth/recovery/reset", response_model=SuccessEnvelope)
def reset(
    request: Request, body: RecoveryResetRequest, db: DB, manager: Manager
) -> SuccessEnvelope:
    auth_rate(request, db, body.username)
    user = db.user_by_username(body.username)
    if not user:
        auth_failure(request, db, body.username)
    try:
        code = db.replace_credentials(
            user, password=body.password, recovery_code=body.recovery_code, rotate_recovery=True
        )
    except (NotFound, StoreConflict):
        auth_failure(request, db, body.username)
    db.clear_password_login_failures(body.username)
    return authenticated_result(request, db, manager, user["id"], code, user["auth_version"] + 1)


@router.post("/api/v1/me/password/change", response_model=SuccessEnvelope)
def change_password(
    request: Request, body: PasswordChangeRequest, user_id: UserId, db: DB, manager: Manager
) -> SuccessEnvelope:
    user = db.user(user_id)
    auth_rate(request, db, user["username"] or user["email"])
    if not db.verify_password(user, body.old_password):
        auth_failure(request, db, user["username"] or user["email"])
    check_new_password(user, body.password)
    try:
        db.replace_credentials(user, password=body.password)
    except StoreConflict as exc:
        raise ApiProblem(409, "VERSION_CONFLICT", "凭据已更新，请重新登录") from exc
    # Replacement tokens keep this browser signed in while invalidating older sessions.
    return authenticated_result(
        request, db, manager, user_id, auth_version=user["auth_version"] + 1
    )


@router.post("/api/v1/me/recovery/regenerate", response_model=SuccessEnvelope)
def regenerate(
    request: Request, body: PasswordConfirmRequest, user_id: UserId, db: DB, manager: Manager
) -> SuccessEnvelope:
    user = db.user(user_id)
    auth_rate(request, db, user["username"] or user["email"])
    if not db.verify_password(user, body.password):
        auth_failure(request, db, user["username"] or user["email"])
    try:
        code = db.replace_credentials(user, rotate_recovery=True)
    except StoreConflict as exc:
        raise ApiProblem(409, "VERSION_CONFLICT", "凭据已更新，请重新登录") from exc
    return authenticated_result(request, db, manager, user_id, code, user["auth_version"] + 1)


@router.post("/api/v1/me/username", response_model=SuccessEnvelope)
def set_username(
    request: Request, body: UsernameSetRequest, user_id: UserId, db: DB, manager: Manager
) -> SuccessEnvelope:
    auth_rate(request, db)
    version = db.user(user_id)["auth_version"]
    try:
        code = db.set_username(user_id, body.username, body.password)
    except NotFound as exc:
        raise ApiProblem(401, "INVALID_CREDENTIALS", "用户名或密码错误") from exc
    except StoreConflict as exc:
        raise ApiProblem(409, "USERNAME_REGISTERED", "用户名已设置或已被使用") from exc
    return authenticated_result(request, db, manager, user_id, code, version)


@router.post("/api/v1/me:delete", response_model=SuccessEnvelope)
def delete_account(
    request: Request, body: PasswordConfirmRequest, user_id: UserId, db: DB
) -> SuccessEnvelope:
    user = db.user(user_id)
    auth_rate(request, db, user["username"] or user["email"])
    if not db.verify_password(user, body.password):
        auth_failure(request, db, user["username"] or user["email"])
    try:
        db.delete_user(user_id, user["auth_version"])
    except StoreConflict as exc:
        raise ApiProblem(409, "VERSION_CONFLICT", "凭据已更新，请重新登录") from exc
    for name in ("ocr_cache", "ocr_documents", "sync_cache"):
        getattr(request.app.state, name).remove_prefix(user_id + ":")
    return envelope(request, {"deleted": True})


@router.get("/api/v1/me/export", response_model=SuccessEnvelope)
def export(request: Request, user_id: UserId, db: DB) -> SuccessEnvelope:
    return envelope(request, db.export_user(user_id))
