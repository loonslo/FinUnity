"""Run a local, disposable same-origin production Web acceptance environment.

uv run python tests/browser_acceptance.py
All accounts and sessions disappear when this process stops. No upstream OCR calls.
"""

from pathlib import Path
from unittest.mock import patch

import uvicorn
from fastapi.responses import FileResponse

from finunity_server.config import Settings

# main also creates the default ASGI app at import. Keep that bootstrap disposable.
with patch.dict("os.environ", {"DATABASE_PATH": ":memory:"}):
    from finunity_server.main import create_app

site = Path(__file__).resolve().parents[2] / "web" / "dist"
app = create_app(
    Settings(
        database_path=":memory:",
        app_env="production",
        session_secret="local-test-session-" * 3,
        password_pepper="local-test-password-" * 3,
        cors_origins=[],
    )
)
CSP = (
    "default-src 'self'; connect-src 'self'; img-src 'self' data: blob:; "
    "style-src 'self' 'unsafe-inline'; script-src 'self'; object-src 'none'; "
    "base-uri 'self'; frame-ancestors 'self'; form-action 'self'"
)


@app.middleware("http")
async def security_headers(request, call_next):
    response = await call_next(request)
    response.headers["Content-Security-Policy"] = CSP
    return response


@app.get("/{path:path}")
def web(path: str):
    candidate = (site / path).resolve()
    if not candidate.is_relative_to(site.resolve()):
        return FileResponse(site / "index.html")
    if candidate.is_dir():
        candidate = candidate / "index.html"
    return FileResponse(candidate if candidate.is_file() else site / "index.html")


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=18000)
