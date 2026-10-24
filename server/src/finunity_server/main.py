import asyncio
from contextlib import asynccontextmanager, suppress
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.formparsers import MultiPartParser

from finunity_server.auth_api import router as auth_router
from finunity_server.baidu_ocr import BaiduHoldingRecognizer
from finunity_server.cache import TTLCache
from finunity_server.config import Settings, get_settings
from finunity_server.market_refresh import HoldingPriceRefresh
from finunity_server.paddle_ocr import PaddleOCRHoldingRecognizer
from finunity_server.platform_service import (
    ReferenceDataProvider,
    SessionInvalidError,
    SessionManager,
    utc_now,
)
from finunity_server.service import OpenAIHoldingRecognizer, QwenHoldingRecognizer
from finunity_server.store import NotFound, SQLiteStore
from finunity_server.v1_api import ApiProblem
from finunity_server.v1_api import router as v1_router


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        worker = asyncio.create_task(app.state.holding_prices.run_daily())
        try:
            yield
        finally:
            worker.cancel()
            with suppress(asyncio.CancelledError):
                await worker

    app = FastAPI(
        lifespan=lifespan,
        title=resolved_settings.app_name,
        version="0.1.0",
        description="识别账户持仓截图，统一返回股票、ETF 和基金的数量与成本。",
        docs_url=None if resolved_settings.app_env == "production" else "/docs",
        redoc_url=None if resolved_settings.app_env == "production" else "/redoc",
        openapi_url=None if resolved_settings.app_env == "production" else "/openapi.json",
    )
    app.state.settings = resolved_settings
    if resolved_settings.ocr_provider == "qwen":
        app.state.recognizer = QwenHoldingRecognizer(resolved_settings)
    elif resolved_settings.ocr_provider == "openai":
        app.state.recognizer = OpenAIHoldingRecognizer(resolved_settings)
    elif resolved_settings.ocr_provider == "paddleocr":
        app.state.recognizer = PaddleOCRHoldingRecognizer(resolved_settings)
    else:
        app.state.recognizer = BaiduHoldingRecognizer(resolved_settings)
    app.state.store = SQLiteStore(resolved_settings)
    app.state.holding_prices = HoldingPriceRefresh(app.state.store)
    app.state.sessions = SessionManager(resolved_settings, app.state.store)
    app.state.data_provider = ReferenceDataProvider()
    for name in ("sync_cache", "ocr_cache", "ocr_documents"):
        setattr(
            app.state,
            name,
            TTLCache(resolved_settings.cache_capacity, resolved_settings.cache_ttl_seconds),
        )
    # Bounded OCR requests are kept in RAM, including multipart upload parsing.
    MultiPartParser.spool_max_size = resolved_settings.max_image_bytes + 1024 * 1024
    app.add_middleware(
        CORSMiddleware,
        allow_origins=resolved_settings.cors_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=[
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Request-ID",
            "Idempotency-Key",
        ],
        expose_headers=["X-Request-ID"],
    )

    @app.middleware("http")
    async def request_context(request: Request, call_next):
        request.state.request_id = request.headers.get("X-Request-ID") or f"req_{uuid4().hex}"
        if request.url.path == "/api/v1/ocr/holdings:parse":
            limit = resolved_settings.max_image_bytes + 512 * 1024
            content = bytearray()
            async for chunk in request.stream():
                if len(content) + len(chunk) > limit:
                    response = JSONResponse(
                        status_code=413,
                        content={
                            "request_id": request.state.request_id,
                            "error": {
                                "code": "IMAGE_TOO_LARGE",
                                "message": "图片超过大小限制",
                                "retryable": False,
                            },
                            "server_time": utc_now(),
                        },
                    )
                    response.headers["X-Request-ID"] = request.state.request_id
                    return response
                content.extend(chunk)
            # BaseHTTPMiddleware replays this bounded in-memory body to multipart parsing.
            request._body = bytes(content)
        response = await call_next(request)
        response.headers["X-Request-ID"] = request.state.request_id
        return response

    @app.exception_handler(ApiProblem)
    async def api_problem_handler(request: Request, exc: ApiProblem) -> JSONResponse:
        headers = {}
        if exc.retry_after_seconds is not None:
            headers["Retry-After"] = str(exc.retry_after_seconds)
        return JSONResponse(
            status_code=exc.http_status,
            headers=headers,
            content={
                "request_id": request.state.request_id,
                "error": {
                    "code": exc.code,
                    "message": exc.message,
                    "retryable": exc.retryable,
                    "retry_after_seconds": exc.retry_after_seconds,
                    "details": exc.details,
                },
                "server_time": utc_now(),
            },
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        details = [
            {"location": list(error["loc"]), "message": error["msg"], "type": error["type"]}
            for error in exc.errors()
        ]
        return JSONResponse(
            status_code=400,
            content={
                "request_id": request.state.request_id,
                "error": {
                    "code": "VALIDATION_ERROR",
                    "message": "请求字段或格式错误",
                    "retryable": False,
                    "retry_after_seconds": None,
                    "details": details,
                },
                "server_time": utc_now(),
            },
        )

    @app.exception_handler(NotFound)
    async def not_found_handler(request: Request, exc: NotFound) -> JSONResponse:
        return await api_problem_handler(
            request, ApiProblem(404, "NOT_FOUND", "记录不存在或无权访问")
        )

    @app.exception_handler(SessionInvalidError)
    async def session_invalid_handler(request: Request, exc: SessionInvalidError) -> JSONResponse:
        return await api_problem_handler(
            request, ApiProblem(401, "TOKEN_INVALID", "用户凭据已更新，请重新登录")
        )

    app.include_router(v1_router)
    app.include_router(auth_router)
    return app


app = create_app()
