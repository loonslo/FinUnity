#!/usr/bin/env sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$TASK_ROOT/backend"
uv sync --locked --group dev
cd "$TASK_ROOT/frontend"
if [ ! -d node_modules ]; then npm ci --no-audit --no-fund; fi
npm run build
cd "$TASK_ROOT/backend"
printf '%s\n' 'FinUnity local prototype: http://127.0.0.1:8011'
exec uv run --no-sync uvicorn app.prototype:app --host 127.0.0.1 --port 8011 --workers 1 --no-access-log
