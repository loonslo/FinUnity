# Server monorepo integration acceptance

Date: 2026-10-24

The Server module is located at `server/` and remains an independently installed, tested, and deployed FastAPI service. `tests/browser_acceptance.py` now resolves the sibling Web production assets from `../web/dist` in this monorepo.

## Local verification

- `uv run --extra dev pytest -q --basetemp=.pytest-public-launch-monorepo`: 140 passed, one Starlette/httpx deprecation warning.
- `uv run --extra dev ruff check .`: passed.
- After the Web production build, `uv run python tests/browser_acceptance.py` served the landing page and an unknown client route with HTTP 200; the SPA root element and Content-Security-Policy header were present. The temporary local server was stopped after the smoke check.

The default Windows pytest temporary directory was inaccessible, so the successful test run used an ignored temporary directory inside this module. No production deployment or external service configuration changed.
