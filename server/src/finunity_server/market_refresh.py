"""Daily persisted holding prices. Never use the reference/demo provider for ledger data."""

import asyncio
import json
import logging
from datetime import UTC, datetime, timedelta, timezone
from decimal import Decimal

import httpx

from finunity_server.store import SQLiteStore, now

SHANGHAI = timezone(timedelta(hours=8))
logger = logging.getLogger(__name__)


class HoldingPriceRefresh:
    def __init__(self, db: SQLiteStore):
        self.db = db
        self.lock = asyncio.Lock()
        with db.connection() as conn:
            conn.execute("""CREATE TABLE IF NOT EXISTS market_refresh_runs (
                user_id TEXT PRIMARY KEY, attempted_at TEXT NOT NULL, payload TEXT NOT NULL
            )""")

    def status(self, user_id: str) -> dict:
        if not self.db.settings.holding_prices_url:
            return {"status": "unconfigured", "updated_count": 0}
        with self.db.connection() as conn:
            row = conn.execute(
                "SELECT payload FROM market_refresh_runs WHERE user_id=?", (user_id,)
            ).fetchone()
        result = json.loads(row["payload"]) if row else {"status": "pending", "updated_count": 0}
        if (
            result.get("run_date") != datetime.now(SHANGHAI).date().isoformat()
            and result["status"] == "success"
        ):
            result["status"] = "pending"
        return result

    async def refresh(self, user_id: str) -> dict:
        if not self.db.settings.holding_prices_url:
            return self.status(user_id)
        async with self.lock:
            # The persisted lease also serializes refreshes across server processes.
            with self.db.connection() as conn:
                conn.execute("BEGIN IMMEDIATE")
                row = conn.execute(
                    "SELECT payload FROM market_refresh_runs WHERE user_id=?", (user_id,)
                ).fetchone()
                previous = json.loads(row["payload"]) if row else {}
                if previous.get("attempted_at"):
                    attempted = datetime.fromisoformat(previous["attempted_at"])
                    local = datetime.now(SHANGHAI)
                    elapsed = datetime.now(UTC) - attempted
                    same_day = previous.get("run_date") == local.date().isoformat()
                    after_close = (
                        local.hour < self.db.settings.holding_prices_hour
                        or attempted.astimezone(SHANGHAI).hour
                        >= self.db.settings.holding_prices_hour
                    )
                    if elapsed.total_seconds() < (
                        300 if previous["status"] == "pending" else 60
                    ) or (same_day and after_close and previous["status"] == "success"):
                        return previous
                lease = {"status": "pending", "attempted_at": now(), "updated_count": 0}
                conn.execute(
                    "INSERT OR REPLACE INTO market_refresh_runs VALUES(?,?,?)",
                    (user_id, lease["attempted_at"], json.dumps(lease)),
                )
            holdings = self.db.list_rows("holdings", user_id)
            instruments = sorted(
                {(h["instrument_id"], h["currency"]) for h in holdings if h.get("instrument_id")}
            )
            timestamp = now()
            result = {
                "status": "success",
                "attempted_at": timestamp,
                "run_date": datetime.now(SHANGHAI).date().isoformat(),
                "updated_count": 0,
                "unavailable_count": len(holdings),
            }
            try:
                quotes = {}
                if instruments:
                    headers = {}
                    if self.db.settings.holding_prices_token:
                        headers["Authorization"] = f"Bearer {self.db.settings.holding_prices_token}"
                    async with httpx.AsyncClient(timeout=30) as client:
                        response = await client.post(
                            self.db.settings.holding_prices_url,
                            headers=headers,
                            json={
                                "instruments": [
                                    {"instrument_id": key, "currency": currency}
                                    for key, currency in instruments
                                ]
                            },
                        )
                        response.raise_for_status()
                        for quote in response.json()["items"]:
                            key = (quote["instrument_id"], quote["currency"])
                            price = Decimal(str(quote["price"]))
                            as_of = datetime.fromisoformat(quote["as_of"].replace("Z", "+00:00"))
                            if (
                                key not in instruments
                                or not price.is_finite()
                                or price < 0
                                or as_of.tzinfo is None
                                or not isinstance(quote.get("source"), str)
                                or not quote["source"].strip()
                                or not timedelta(0)
                                <= datetime.now(UTC) - as_of
                                <= timedelta(days=7)
                            ):
                                continue
                            if key not in quotes or as_of > quotes[key][1]:
                                quotes[key] = (price, as_of, quote["source"])
                # Read again inside the write transaction: preserve concurrent edits and metadata.
                with self.db.connection() as conn:
                    conn.execute("BEGIN IMMEDIATE")
                    rows = conn.execute(
                        "SELECT * FROM holdings WHERE user_id=? AND archived_at IS NULL", (user_id,)
                    ).fetchall()
                    result["unavailable_count"] = len(rows)
                    for row in rows:
                        quote = quotes.get((row["instrument_id"], row["currency"]))
                        if quote is None:
                            continue
                        price, as_of, source = quote
                        metadata = json.loads(row["metadata"])
                        old_time = metadata.get("price_as_of")
                        if (
                            old_time
                            and datetime.fromisoformat(old_time.replace("Z", "+00:00")) > as_of
                        ):
                            continue
                        metadata.update(
                            current_price=str(price),
                            price_as_of=as_of.isoformat(),
                            market_source=source,
                        )
                        conn.execute(
                            "UPDATE holdings SET metadata=?, version=version+1, updated_at=? "
                            "WHERE id=? AND user_id=?",
                            (json.dumps(metadata), timestamp, row["id"], user_id),
                        )
                        result["updated_count"] += 1
                        result["unavailable_count"] -= 1
                    if result["unavailable_count"]:
                        result["status"] = "partial"
                    conn.execute(
                        "INSERT OR REPLACE INTO market_refresh_runs VALUES(?,?,?)",
                        (user_id, timestamp, json.dumps(result)),
                    )
            except (
                httpx.HTTPError,
                ValueError,
                KeyError,
                TypeError,
                AttributeError,
                ArithmeticError,
            ):
                # Keep every previous price intact on malformed/provider failures.
                result.update(status="failed", updated_count=0, unavailable_count=len(holdings))
                with self.db.connection() as conn:
                    conn.execute(
                        "INSERT OR REPLACE INTO market_refresh_runs VALUES(?,?,?)",
                        (user_id, timestamp, json.dumps(result)),
                    )
            return result

    async def run_daily(self):
        while True:
            try:
                local = datetime.now(SHANGHAI)
                if (
                    self.db.settings.holding_prices_url
                    and local.hour >= self.db.settings.holding_prices_hour
                ):
                    with self.db.connection() as conn:
                        users = [
                            row[0]
                            for row in conn.execute(
                                "SELECT DISTINCT user_id FROM holdings WHERE archived_at IS NULL"
                            )
                        ]
                    for user_id in users:
                        await self.refresh(user_id)
            except Exception:
                logger.exception("Daily holding refresh failed; retrying next hour")
            await asyncio.sleep(3600)
