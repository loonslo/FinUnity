"""Loopback-only entry point; legacy APIs and automatic trade catch-up stay unmounted."""

from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path

# An explicit prototype database, even if a shell inherited another project's URL.
os.environ["DATABASE_URL"] = os.getenv(
    "FINUNITY_DATABASE_URL",
    f"sqlite:///{Path(__file__).resolve().parents[1] / 'finunity-prototype.db'}",
)

from fastapi import FastAPI, Request  # noqa: E402
from fastapi.responses import JSONResponse, FileResponse  # noqa: E402
from fastapi.staticfiles import StaticFiles  # noqa: E402
from starlette.middleware.trustedhost import TrustedHostMiddleware  # noqa: E402

from app import database  # noqa: E402
from app.planner import assistant  # noqa: E402
from app.planner.router import router  # noqa: E402


@asynccontextmanager
async def lifespan(app: FastAPI):
    database.init_db()
    with database.SessionLocal() as db:
        assistant.interrupt_unfinished(db)
    yield


app = FastAPI(title="FinUnity 本机财务规划原型", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    TrustedHostMiddleware, allowed_hosts=["127.0.0.1", "localhost", "testserver"]
)


@app.middleware("http")
async def local_origin(request: Request, call_next):
    if request.method not in ("GET", "HEAD", "OPTIONS"):
        origin = request.headers.get("origin")
        if origin and origin not in (
            str(request.base_url).rstrip("/"),
            "http://localhost:5174",
            "http://127.0.0.1:5174",
        ):
            return JSONResponse(
                {
                    "detail": {
                        "code": "ORIGIN_DENIED",
                        "message": "请从本机原型页面操作",
                    }
                },
                status_code=403,
            )
    return await call_next(request)


app.include_router(router)


@app.get("/api/health")
def health():
    return {"status": "ok", "service": "finunity-local-prototype"}


dist = Path(__file__).resolve().parents[2] / "frontend" / "dist"
if dist.is_dir():
    app.mount("/assets", StaticFiles(directory=dist / "assets"), name="assets")
    if (dist / "fonts").is_dir():
        app.mount("/fonts", StaticFiles(directory=dist / "fonts"), name="fonts")

    @app.get("/finunity.svg")
    def favicon():
        return FileResponse(dist / "finunity.svg")

    @app.get("/")
    def index():
        return FileResponse(dist / "index.html")
