"""Disposable loopback server for browser regression; never opens the daily database.

Run from backend: uv run --no-sync python tests/planner_ui_server.py
The test-only clock/reset endpoints exist only in this process, on port 8012.
"""

from __future__ import annotations

import os
from datetime import date
from pathlib import Path
import sys
from tempfile import TemporaryDirectory


def main() -> None:
    import uvicorn

    with TemporaryDirectory(prefix="finunity-ui-audit-") as folder:
        url = "sqlite:///" + (Path(folder) / "ui.db").as_posix()
        os.environ.update(
            DATABASE_URL=url,
            FINUNITY_DATABASE_URL=url,
            AURELIO_SKIP_MIGRATIONS="1",
            PYTHON_DOTENV_DISABLED="1",
            OPENROUTER_API_KEY="",
        )
        sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
        from app import database
        from app.planner import schemas, service
        from app.planner.models import PlanningDocument, PlanningState
        from app.prototype import app
        from fastapi import Body
        from sqlalchemy import delete

        assert str(database.engine.url) == url
        current = [date(2026, 10, 19)]
        schemas.today = lambda: current[0]
        service.today = lambda: current[0]

        @app.post("/__test__/clock")
        def clock(value: dict = Body(...)):
            current[0] = date.fromisoformat(value["date"])
            return {"today": current[0].isoformat()}

        @app.post("/__test__/reset")
        def reset():
            assert str(database.engine.url) == url
            with database.SessionLocal() as db:
                db.execute(delete(PlanningDocument))
                db.execute(delete(PlanningState))
                db.commit()
            current[0] = date(2026, 10, 19)
            return {"reset": True}

        print("Disposable browser validation: http://127.0.0.1:8012", flush=True)
        uvicorn.run(app, host="127.0.0.1", port=8012, workers=1, log_level="warning")


if __name__ == "__main__":
    main()
