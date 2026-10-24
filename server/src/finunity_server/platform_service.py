import base64
import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Protocol

from finunity_server.config import Settings


def utc_now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def _unb64(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


class SessionStore(Protocol):
    def save_refresh_session(
        self,
        token_hash: str,
        subject: str,
        expires_at: int,
        session_type: str,
        auth_version: int,
    ) -> None: ...

    def consume_refresh_session(self, token_hash: str, current_time: int) -> dict | None: ...

    def user(self, user_id: str) -> dict[str, Any]: ...

    def revoke_refresh_sessions(self, subject: str) -> None: ...

    def save_access_revocation(self, token_hash: str, expires_at: int) -> None: ...

    def is_access_revoked(self, token_hash: str, current_time: int) -> bool: ...


class SessionInvalidError(ValueError):
    pass


class SessionManager:
    """Small stateless access-token issuer with rotating, revocable refresh tokens."""

    def __init__(self, settings: Settings, refresh_store: SessionStore | None = None) -> None:
        self.settings = settings
        self.refresh_store = refresh_store
        self._refresh: dict[str, dict] = {}
        self._revoked_access: set[str] = set()

    def _sign(self, payload: dict[str, Any]) -> str:
        encoded = _b64(json.dumps(payload, separators=(",", ":")).encode())
        signature = hmac.new(
            self.settings.session_secret.encode(), encoded.encode(), hashlib.sha256
        ).digest()
        return f"{encoded}.{_b64(signature)}"

    def _access_token(self, subject: str, session_type: str, auth_version: int) -> str:
        now = int(time.time())
        return self._sign(
            {
                "sub": subject,
                "type": session_type,
                "ver": auth_version,
                "iat": now,
                "exp": now + self.settings.access_token_ttl_seconds,
                "jti": secrets.token_hex(12),
            }
        )

    def _new_refresh(self, subject: str, session_type: str, auth_version: int) -> str:
        token = secrets.token_urlsafe(48)
        digest = hashlib.sha256(token.encode()).hexdigest()
        expires = int(time.time()) + self.settings.refresh_token_ttl_seconds
        if not self.refresh_store:
            self._refresh = {
                key: row
                for key, row in self._refresh.items()
                if row["expires_at"] > int(time.time())
            }
            self._refresh[digest] = {
                "subject": subject,
                "expires_at": expires,
                "session_type": session_type,
                "auth_version": auth_version,
            }
        if self.refresh_store:
            self.refresh_store.save_refresh_session(
                digest,
                subject,
                expires,
                session_type,
                auth_version,
            )
        return token

    def issue(
        self,
        subject: str,
        session_type: str = "USER",
        auth_version: int | None = None,
    ) -> dict[str, Any]:
        try:
            version = (
                auth_version
                if auth_version is not None
                else (
                    self.refresh_store.user(subject)["auth_version"]
                    if (self.refresh_store and session_type == "USER")
                    else 0
                )
            )
            refresh_token = self._new_refresh(subject, session_type, version)
        except LookupError as exc:
            raise SessionInvalidError("Credentials changed during authentication") from exc
        return {
            "access_token": self._access_token(subject, session_type, version),
            "token_type": "Bearer",
            "expires_in_seconds": self.settings.access_token_ttl_seconds,
            "refresh_token": refresh_token,
            "refresh_expires_in_seconds": self.settings.refresh_token_ttl_seconds,
        }

    def rotate(self, refresh_token: str) -> dict[str, Any] | None:
        digest = hashlib.sha256(refresh_token.encode()).hexdigest()
        current_time = int(time.time())
        record = self._refresh.pop(digest, None)
        record = (
            self.refresh_store.consume_refresh_session(digest, current_time)
            if self.refresh_store
            else record
            if record and record["expires_at"] > current_time
            else None
        )
        if not record or not self._valid_identity(
            record["subject"], record["session_type"], record["auth_version"]
        ):
            return None
        try:
            return self.issue(record["subject"], record["session_type"], record["auth_version"])
        except (SessionInvalidError, LookupError):
            return None

    def _valid_identity(self, subject: str, session_type: str, version: int) -> bool:
        if session_type == "INSTALLATION":
            return subject.startswith("ins_") and version == 0
        if session_type != "USER" or not subject.startswith("usr_"):
            return False
        if self.refresh_store:
            try:
                return self.refresh_store.user(subject)["auth_version"] == version
            except LookupError:
                return False
        return True

    def verify_access(self, token: str) -> bool:
        return self.subject(token) is not None

    def subject(self, token: str) -> str | None:
        claims = self.claims(token)
        return claims["sub"] if claims else None

    def claims(self, token: str) -> dict[str, Any] | None:
        try:
            token_hash = hashlib.sha256(token.encode()).hexdigest()
            current_time = int(time.time())
            if token_hash in self._revoked_access or (
                self.refresh_store
                and self.refresh_store.is_access_revoked(token_hash, current_time)
            ):
                return None
            encoded, signature = token.split(".", 1)
            expected = hmac.new(
                self.settings.session_secret.encode(), encoded.encode(), hashlib.sha256
            ).digest()
            if not hmac.compare_digest(_unb64(signature), expected):
                return None
            payload = json.loads(_unb64(encoded))
            if not isinstance(payload, dict) or not isinstance(payload.get("sub"), str):
                return None
            if type(payload.get("exp")) is not int or payload["exp"] <= current_time:
                return None
            if type(payload.get("ver")) is not int or not self._valid_identity(
                payload["sub"], payload.get("type"), payload["ver"]
            ):
                return None
            return payload
        except (ValueError, KeyError, TypeError, UnicodeError, json.JSONDecodeError):
            return None

    def revoke(self, access_token: str, subject: str) -> None:
        token_hash = hashlib.sha256(access_token.encode()).hexdigest()
        if not self.refresh_store:
            self._revoked_access.add(token_hash)
        self._refresh = {
            digest: record
            for digest, record in self._refresh.items()
            if record["subject"] != subject
        }
        if self.refresh_store:
            self.refresh_store.revoke_refresh_sessions(subject)
            try:
                encoded = access_token.split(".", 1)[0]
                expires_at = int(json.loads(_unb64(encoded))["exp"])
            except (ValueError, KeyError, TypeError, json.JSONDecodeError):
                expires_at = int(time.time()) + self.settings.access_token_ttl_seconds
            self.refresh_store.save_access_revocation(token_hash, expires_at)


@dataclass(frozen=True)
class ReferenceInstrument:
    instrument_id: str
    symbol: str
    canonical_symbol: str
    name: str
    short_name: str
    type: str
    market: str
    currency: str
    aliases: tuple[str, ...] = ()
    fund_code: str | None = None
    fund_type: str | None = None

    def public(self) -> dict[str, Any]:
        return {
            "instrument_id": self.instrument_id,
            "symbol": self.symbol,
            "canonical_symbol": self.canonical_symbol,
            "name": self.name,
            "short_name": self.short_name,
            "type": self.type,
            "market": self.market,
            "currency": self.currency,
            "status": "ACTIVE",
            "aliases": list(self.aliases),
            "fund_code": self.fund_code,
        }


class ReferenceDataProvider:
    """Deterministic integration provider. Replace this adapter before production use."""

    source = "finunity-reference-data"

    def __init__(self) -> None:
        rows = [
            ReferenceInstrument(
                "XSHG:600519",
                "600519",
                "600519.SS",
                "贵州茅台",
                "贵州茅台",
                "STOCK",
                "XSHG",
                "CNY",
                ("600519.SH", "600519.SS"),
            ),
            ReferenceInstrument(
                "XSHE:159919",
                "159919",
                "159919.SZ",
                "沪深300ETF",
                "300ETF",
                "ETF",
                "XSHE",
                "CNY",
                ("159919.SZ",),
            ),
            ReferenceInstrument(
                "XHKG:00700",
                "00700",
                "00700.HK",
                "腾讯控股",
                "腾讯",
                "STOCK",
                "XHKG",
                "HKD",
                ("0700.HK", "00700.HK"),
            ),
            ReferenceInstrument(
                "XNAS:AAPL", "AAPL", "AAPL", "Apple Inc.", "Apple", "STOCK", "XNAS", "USD"
            ),
            ReferenceInstrument(
                "XNYS:BRK.B",
                "BRK.B",
                "BRK.B",
                "Berkshire Hathaway",
                "Berkshire",
                "STOCK",
                "XNYS",
                "USD",
                ("BRK-B",),
            ),
            ReferenceInstrument(
                "CNFUND:000001",
                "000001",
                "000001",
                "华夏成长混合",
                "华夏成长",
                "FUND",
                "CNFUND",
                "CNY",
                (),
                "000001",
                "MIXED",
            ),
            ReferenceInstrument(
                "CNFUND:161725",
                "161725",
                "161725",
                "招商中证白酒指数",
                "招商白酒",
                "LOF",
                "CNFUND",
                "CNY",
                (),
                "161725",
                "INDEX",
            ),
        ]
        self.instruments = {row.instrument_id: row for row in rows}
        self.quotes = {
            "XSHG:600519": ("1418.01", "1402.50", "1400.00", "1425.00", "1398.00", "2410000"),
            "XSHE:159919": ("4.223", "4.200", "4.205", "4.230", "4.190", "18500000"),
            "XHKG:00700": ("612.00", "606.50", "608.00", "615.00", "604.00", "18250000"),
            "XNAS:AAPL": ("229.10", "227.16", "228.20", "230.40", "227.80", "38521000"),
            "XNYS:BRK.B": ("472.20", "470.80", "471.00", "474.10", "469.50", "3200000"),
        }
        self.navs = {
            "000001": ("1.2345", "3.4567", "1.2300"),
            "161725": ("1.1023", "2.3045", "1.0988"),
        }
        self.fx = {("USD", "CNY"): "7.123456", ("HKD", "CNY"): "0.913267"}

    def search(
        self, query: str, types: set[str], markets: set[str], limit: int
    ) -> list[dict[str, Any]]:
        needle = query.strip().upper()
        matches = []
        for item in self.instruments.values():
            haystack = " ".join(
                (
                    item.instrument_id,
                    item.symbol,
                    item.canonical_symbol,
                    item.name,
                    item.short_name,
                    *item.aliases,
                )
            ).upper()
            if (
                needle not in haystack
                or (types and item.type not in types)
                or (markets and item.market not in markets)
            ):
                continue
            matches.append(item.public())
        return matches[:limit]

    def resolve(
        self, text: str, type_hint: str | None, market_hint: str | None
    ) -> list[ReferenceInstrument]:
        needle = text.strip().upper()
        candidates = []
        for item in self.instruments.values():
            keys = {
                item.instrument_id.upper(),
                item.symbol.upper(),
                item.canonical_symbol.upper(),
                *(x.upper() for x in item.aliases),
            }
            if needle not in keys:
                continue
            if (
                type_hint
                and item.type != type_hint
                and not (type_hint == "FUND" and item.type == "LOF")
            ):
                continue
            if market_hint and item.market != market_hint:
                continue
            candidates.append(item)
        return candidates

    def quote(self, instrument_id: str) -> dict[str, Any] | None:
        item = self.instruments.get(instrument_id)
        values = self.quotes.get(instrument_id)
        if not item or not values:
            return None
        last, previous, open_, high, low, volume = values
        return {
            "instrument_id": instrument_id,
            "status": "OK",
            "last": last,
            "previous_close": previous,
            "open": open_,
            "high": high,
            "low": low,
            "volume": volume,
            "currency": item.currency,
            "market_status": "CLOSED",
            "source_time": "2026-08-28T20:00:00Z",
            "quality": "STALE",
            "source": self.source,
        }

    def nav(self, code: str) -> dict[str, Any] | None:
        values = self.navs.get(code)
        instrument = self.instruments.get(f"CNFUND:{code}")
        if not values or not instrument:
            return None
        unit, accumulated, previous = values
        return {
            "instrument_id": instrument.instrument_id,
            "fund_code": code,
            "name": instrument.name,
            "unit_nav": unit,
            "accumulated_nav": accumulated,
            "previous_unit_nav": previous,
            "nav_date": "2026-08-28",
            "currency": instrument.currency,
            "value_type": "OFFICIAL_NAV",
            "estimated_nav": None,
            "estimated_at": None,
            "source_time": "2026-08-29T01:30:00Z",
            "received_at": "2026-08-29T01:30:05Z",
            "source": self.source,
        }

    def fx_rate(self, base: str, quote: str, rate_type: str) -> dict[str, Any] | None:
        rate = self.fx.get((base, quote))
        if rate is None:
            inverse = self.fx.get((quote, base))
            if inverse:
                rate = format(1 / float(inverse), ".8f").rstrip("0").rstrip(".")
        if rate is None:
            return None
        return {
            "pair": f"{base}/{quote}",
            "rate": rate,
            "rate_type": rate_type,
            "source_time": "2026-08-28T16:00:00Z",
            "received_at": "2026-08-28T16:00:02Z",
            "quality": "STALE",
            "source": self.source,
        }
