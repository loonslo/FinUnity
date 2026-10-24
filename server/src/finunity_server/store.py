import hashlib
import hmac
import json
import logging
import secrets
import smtplib
import sqlite3
import threading
import time
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from email.message import EmailMessage
from pathlib import Path
from typing import Any
from uuid import uuid4

from finunity_server.config import Settings


def now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


PBKDF2_ITERATIONS = 600_000
# Newest refresh sessions kept per subject: a person may use several devices, an anonymous
# installation only ever needs its current one.
MAX_REFRESH_SESSIONS = {"USER": 20, "INSTALLATION": 3}


class StoreConflict(Exception):
    pass


class NotFound(LookupError):
    pass


class SQLiteStore:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        path = Path(settings.database_path)
        if settings.database_path != ":memory:":
            path.parent.mkdir(parents=True, exist_ok=True)
        self.path = settings.database_path
        self._lock = threading.RLock()
        self._memory_connection = (
            sqlite3.connect(":memory:", check_same_thread=False)
            if self.path == ":memory:"
            else None
        )
        if self.path != ":memory:" and path.exists():
            with sqlite3.connect(self.path) as existing:
                columns = {row[1] for row in existing.execute("PRAGMA table_info(users)")}
                if columns and "username" not in columns:
                    backup_path = path.with_name(
                        path.name + ".pre-public-v3-" + datetime.now(UTC).strftime("%Y%m%d%H%M%S%f")
                    )
                    with sqlite3.connect(backup_path) as backup:
                        backup_path.chmod(0o600)
                        existing.backup(backup)
        self._initialize()

    @contextmanager
    def connection(self) -> Iterator[sqlite3.Connection]:
        with self._lock:
            conn = self._memory_connection or sqlite3.connect(self.path)
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA foreign_keys=ON")
            try:
                yield conn
                conn.commit()
            except Exception:
                conn.rollback()
                raise
            finally:
                if self._memory_connection is None:
                    conn.close()

    def _initialize(self) -> None:
        with self.connection() as db:
            db.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS users (
                  id TEXT PRIMARY KEY, email TEXT UNIQUE, username TEXT COLLATE NOCASE UNIQUE,
                  display_name TEXT, password_hash TEXT, recovery_hash TEXT,
                  auth_version INTEGER NOT NULL DEFAULT 0,
                  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                  archived_at TEXT
                );
                CREATE TABLE IF NOT EXISTS email_codes (
                  email TEXT NOT NULL, purpose TEXT NOT NULL, code_hash TEXT NOT NULL,
                  expires_at TEXT NOT NULL, consumed_at TEXT, attempts INTEGER NOT NULL DEFAULT 0,
                  created_at TEXT NOT NULL, PRIMARY KEY(email, purpose)
                );
                CREATE TABLE IF NOT EXISTS user_settings (
                  user_id TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL,
                  version INTEGER NOT NULL, updated_at TEXT NOT NULL,
                  PRIMARY KEY(user_id, kind), FOREIGN KEY(user_id) REFERENCES users(id)
                );
                CREATE TABLE IF NOT EXISTS accounts (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL,
                  institution TEXT, account_type TEXT NOT NULL, currency TEXT NOT NULL,
                  opening_balance TEXT NOT NULL DEFAULT '0',
                  version INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                  archived_at TEXT, FOREIGN KEY(user_id) REFERENCES users(id)
                );
                CREATE TABLE IF NOT EXISTS holdings (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, account_id TEXT NOT NULL,
                  instrument_id TEXT, name TEXT NOT NULL, asset_type TEXT NOT NULL,
                  quantity TEXT NOT NULL, average_cost TEXT, currency TEXT NOT NULL,
                  metadata TEXT NOT NULL, version INTEGER NOT NULL, created_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL, archived_at TEXT,
                  FOREIGN KEY(user_id) REFERENCES users(id),
                  FOREIGN KEY(account_id) REFERENCES accounts(id)
                );
                CREATE TABLE IF NOT EXISTS transactions (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, account_id TEXT NOT NULL,
                  to_account_id TEXT, holding_id TEXT, transaction_type TEXT NOT NULL,
                  amount TEXT NOT NULL,
                  quantity TEXT, price TEXT, currency TEXT NOT NULL, occurred_at TEXT NOT NULL,
                  note TEXT, version INTEGER NOT NULL, created_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL, archived_at TEXT,
                  FOREIGN KEY(user_id) REFERENCES users(id),
                  FOREIGN KEY(account_id) REFERENCES accounts(id)
                );
                CREATE TABLE IF NOT EXISTS sources (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL,
                  version INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                  archived_at TEXT, FOREIGN KEY(user_id) REFERENCES users(id)
                );
                CREATE TABLE IF NOT EXISTS holding_imports (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, status TEXT NOT NULL,
                  payload TEXT NOT NULL, version INTEGER NOT NULL, created_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL, archived_at TEXT,
                  FOREIGN KEY(user_id) REFERENCES users(id)
                );
                CREATE TABLE IF NOT EXISTS import_references (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, batch_id TEXT NOT NULL,
                  document_id TEXT, row_id TEXT, holding_id TEXT, image_sha256 TEXT,
                  snapshot_date TEXT, decision TEXT NOT NULL, applied_at TEXT,
                  created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT,
                  FOREIGN KEY(user_id) REFERENCES users(id),
                  FOREIGN KEY(batch_id) REFERENCES holding_imports(id)
                );
                CREATE TABLE IF NOT EXISTS ocr_documents (
                  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, request_key TEXT NOT NULL,
                  image_sha256 TEXT NOT NULL, payload TEXT NOT NULL, version INTEGER NOT NULL,
                  created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT,
                  UNIQUE(user_id, request_key), FOREIGN KEY(user_id) REFERENCES users(id)
                );
                CREATE TABLE IF NOT EXISTS idempotency (
                  user_id TEXT NOT NULL, scope TEXT NOT NULL, request_id TEXT NOT NULL,
                  fingerprint TEXT NOT NULL, response TEXT NOT NULL, created_at TEXT NOT NULL,
                  PRIMARY KEY(user_id, scope, request_id)
                );
                CREATE TABLE IF NOT EXISTS password_attempts (
                  email TEXT PRIMARY KEY, failed INTEGER NOT NULL DEFAULT 0,
                  locked_until TEXT, updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS refresh_sessions (
                  token_hash TEXT PRIMARY KEY, subject TEXT NOT NULL,
                  expires_at INTEGER NOT NULL, created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS revoked_access_tokens (
                  token_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL,
                  created_at TEXT NOT NULL
                );
                -- Expiry purges and per-subject revocation run on unauthenticated paths;
                -- without these indexes each call scans the whole table.
                CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires
                  ON refresh_sessions(expires_at);
                CREATE INDEX IF NOT EXISTS idx_refresh_sessions_subject
                  ON refresh_sessions(subject);
                CREATE INDEX IF NOT EXISTS idx_revoked_access_tokens_expires
                  ON revoked_access_tokens(expires_at);
                """
            )
            columns = {row["name"] for row in db.execute("PRAGMA table_info(users)")}
            if "password_hash" not in columns:
                db.execute("ALTER TABLE users ADD COLUMN password_hash TEXT")
            if "username" not in columns:
                # Commit before switching FK enforcement; child rows retain their user IDs.
                db.commit()
                db.execute("PRAGMA foreign_keys=OFF")
                try:
                    db.executescript("""
                    BEGIN IMMEDIATE;
                    CREATE TABLE users_v3 (
                      id TEXT PRIMARY KEY, email TEXT UNIQUE,
                      username TEXT COLLATE NOCASE UNIQUE, display_name TEXT,
                      password_hash TEXT, recovery_hash TEXT,
                      auth_version INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
                    );
                    INSERT INTO users_v3(id,email,display_name,password_hash,created_at,
                                         updated_at,archived_at)
                      SELECT id,email,display_name,password_hash,created_at,updated_at,archived_at
                      FROM users;
                    DROP TABLE users;
                    ALTER TABLE users_v3 RENAME TO users;
                    COMMIT;
                    """)
                finally:
                    db.execute("PRAGMA foreign_keys=ON")
            refresh_columns = {
                row["name"] for row in db.execute("PRAGMA table_info(refresh_sessions)")
            }
            if "session_type" not in refresh_columns:
                # Old sessions have no trustworthy type and must be re-created.
                db.execute("DELETE FROM refresh_sessions")
                db.execute("ALTER TABLE refresh_sessions ADD COLUMN session_type TEXT")
                db.execute("ALTER TABLE refresh_sessions ADD COLUMN auth_version INTEGER")
            db.execute("""CREATE TABLE IF NOT EXISTS usage_counters (
                scope TEXT NOT NULL, identity TEXT NOT NULL, bucket INTEGER NOT NULL,
                count INTEGER NOT NULL, expires_at INTEGER NOT NULL,
                PRIMARY KEY(scope,identity,bucket))""")
            db.execute(
                "CREATE INDEX IF NOT EXISTS idx_usage_counters_expires "
                "ON usage_counters(expires_at)"
            )
            account_columns = {row["name"] for row in db.execute("PRAGMA table_info(accounts)")}
            if "opening_balance" not in account_columns:
                db.execute(
                    "ALTER TABLE accounts ADD COLUMN opening_balance TEXT NOT NULL DEFAULT '0'"
                )
            transaction_columns = {
                row["name"] for row in db.execute("PRAGMA table_info(transactions)")
            }
            if "to_account_id" not in transaction_columns:
                db.execute("ALTER TABLE transactions ADD COLUMN to_account_id TEXT")
            self._migrate_accountless_schema(db)

    @staticmethod
    def _migrate_accountless_schema(db: sqlite3.Connection) -> None:
        """Make ledger records user-owned while preserving existing rows."""
        holding_columns = {row["name"]: row for row in db.execute("PRAGMA table_info(holdings)")}
        transaction_columns = {
            row["name"]: row for row in db.execute("PRAGMA table_info(transactions)")
        }
        holding_account_required = bool(
            holding_columns["account_id"]["notnull"] if "account_id" in holding_columns else False
        )
        transaction_account_required = bool(
            transaction_columns["account_id"]["notnull"]
            if "account_id" in transaction_columns
            else False
        )
        needs_holdings_rebuild = (
            "source_id" not in holding_columns
            or "snapshot_date" not in holding_columns
            or holding_account_required
        )
        needs_transactions_rebuild = (
            "source_id" not in transaction_columns or transaction_account_required
        )
        if not (needs_holdings_rebuild or needs_transactions_rebuild):
            return
        db.execute("PRAGMA foreign_keys=OFF")
        try:
            if needs_holdings_rebuild:
                db.execute(
                    """CREATE TABLE holdings_v2 (
                      id TEXT PRIMARY KEY, user_id TEXT NOT NULL, account_id TEXT,
                      source_id TEXT, snapshot_date TEXT, instrument_id TEXT,
                      name TEXT NOT NULL, asset_type TEXT NOT NULL, quantity TEXT NOT NULL,
                      average_cost TEXT, currency TEXT NOT NULL, metadata TEXT NOT NULL,
                      version INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                      archived_at TEXT, FOREIGN KEY(user_id) REFERENCES users(id),
                      FOREIGN KEY(account_id) REFERENCES accounts(id),
                      FOREIGN KEY(source_id) REFERENCES sources(id)
                    )"""
                )
                db.execute(
                    """INSERT INTO holdings_v2(
                      id,user_id,account_id,source_id,snapshot_date,instrument_id,name,asset_type,
                      quantity,average_cost,currency,metadata,version,created_at,updated_at,archived_at
                    ) SELECT id,user_id,account_id,NULL,NULL,instrument_id,name,asset_type,
                      quantity,average_cost,currency,metadata,version,created_at,updated_at,archived_at
                    FROM holdings"""
                )
                db.execute("DROP TABLE holdings")
                db.execute("ALTER TABLE holdings_v2 RENAME TO holdings")
            if needs_transactions_rebuild:
                db.execute(
                    """CREATE TABLE transactions_v2 (
                      id TEXT PRIMARY KEY, user_id TEXT NOT NULL, account_id TEXT,
                      to_account_id TEXT, source_id TEXT, holding_id TEXT,
                      transaction_type TEXT NOT NULL, amount TEXT NOT NULL, quantity TEXT,
                      price TEXT, currency TEXT NOT NULL, occurred_at TEXT NOT NULL,
                      note TEXT, version INTEGER NOT NULL, created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL, archived_at TEXT,
                      FOREIGN KEY(user_id) REFERENCES users(id),
                      FOREIGN KEY(account_id) REFERENCES accounts(id),
                      FOREIGN KEY(to_account_id) REFERENCES accounts(id),
                      FOREIGN KEY(source_id) REFERENCES sources(id)
                    )"""
                )
                db.execute(
                    """INSERT INTO transactions_v2(
                      id,user_id,account_id,to_account_id,source_id,holding_id,transaction_type,
                      amount,quantity,price,currency,occurred_at,note,version,created_at,updated_at,archived_at
                    ) SELECT id,user_id,account_id,to_account_id,NULL,holding_id,transaction_type,
                      amount,quantity,price,currency,occurred_at,note,version,created_at,updated_at,archived_at
                    FROM transactions"""
                )
                db.execute("DROP TABLE transactions")
                db.execute("ALTER TABLE transactions_v2 RENAME TO transactions")
        finally:
            db.execute("PRAGMA foreign_keys=ON")

    def _code_hash(self, email: str, purpose: str, code: str) -> str:
        message = f"{email}:{purpose}:{code}".encode()
        return hmac.new(
            self.settings.email_code_pepper.encode(), message, hashlib.sha256
        ).hexdigest()

    def issue_code(self, email: str, purpose: str) -> str:
        code = f"{secrets.randbelow(1_000_000):06d}"
        created = datetime.now(UTC)
        expires = created + timedelta(seconds=self.settings.email_code_ttl_seconds)
        with self.connection() as db:
            db.execute(
                """INSERT INTO email_codes(
                email,purpose,code_hash,expires_at,consumed_at,attempts,created_at)
                VALUES(?,?,?,?,NULL,0,?) ON CONFLICT(email,purpose) DO UPDATE SET
                code_hash=excluded.code_hash,expires_at=excluded.expires_at,consumed_at=NULL,
                attempts=0,created_at=excluded.created_at""",
                (
                    email,
                    purpose,
                    self._code_hash(email, purpose, code),
                    expires.isoformat(),
                    created.isoformat(),
                ),
            )
        return code

    def consume_code(self, email: str, purpose: str, code: str) -> bool:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM email_codes WHERE email=? AND purpose=?", (email, purpose)
            ).fetchone()
            if not row or row["consumed_at"] or row["attempts"] >= 5:
                return False
            db.execute(
                "UPDATE email_codes SET attempts=attempts+1 WHERE email=? AND purpose=?",
                (email, purpose),
            )
            valid = datetime.fromisoformat(row["expires_at"]) > datetime.now(
                UTC
            ) and hmac.compare_digest(row["code_hash"], self._code_hash(email, purpose, code))
            if valid:
                db.execute(
                    "UPDATE email_codes SET consumed_at=? WHERE email=? AND purpose=?",
                    (now(), email, purpose),
                )
            return valid

    def send_code(self, email: str, code: str, locale: str) -> None:
        if not self.settings.smtp_host:
            return
        message = EmailMessage()
        message["Subject"] = "FinUnity verification code"
        message["From"] = self.settings.smtp_from_email or self.settings.smtp_username
        message["To"] = email
        message.set_content(
            f"FinUnity 验证码：{code}，{self.settings.email_code_ttl_seconds // 60} 分钟内有效。"
            if locale.startswith("zh")
            else f"Your FinUnity verification code is {code}."
        )
        smtp_client = smtplib.SMTP_SSL if self.settings.smtp_use_ssl else smtplib.SMTP
        with smtp_client(self.settings.smtp_host, self.settings.smtp_port, timeout=10) as smtp:
            if self.settings.smtp_use_tls and not self.settings.smtp_use_ssl:
                smtp.starttls()
            if self.settings.smtp_username and self.settings.smtp_password:
                smtp.login(self.settings.smtp_username, self.settings.smtp_password)
            smtp.send_message(message)

    def user_by_email(self, email: str) -> dict[str, Any] | None:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM users WHERE email=? AND archived_at IS NULL", (email,)
            ).fetchone()
            return dict(row) if row else None

    def create_user(self, email: str | None, display_name: str | None) -> dict[str, Any]:
        timestamp, user_id = now(), f"usr_{uuid4().hex}"
        try:
            with self.connection() as db:
                db.execute(
                    """INSERT INTO users(id,email,display_name,password_hash,
                    created_at,updated_at,archived_at) VALUES(?,?,?,?,?,?,NULL)""",
                    (user_id, email, display_name, None, timestamp, timestamp),
                )
        except sqlite3.IntegrityError as exc:
            raise StoreConflict from exc
        return self.user(user_id)

    def user(self, user_id: str) -> dict[str, Any]:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM users WHERE id=? AND archived_at IS NULL", (user_id,)
            ).fetchone()
            if not row:
                raise NotFound
            return dict(row)

    def set_display_name(self, user_id: str, display_name: str) -> None:
        with self.connection() as db:
            result = db.execute(
                "UPDATE users SET display_name=?, updated_at=? WHERE id=? AND archived_at IS NULL",
                (display_name.strip() or None, now(), user_id),
            )
            if result.rowcount != 1:
                raise NotFound

    def user_by_username(self, username: str) -> dict[str, Any] | None:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM users WHERE username=? COLLATE NOCASE AND archived_at IS NULL",
                (username,),
            ).fetchone()
            return dict(row) if row else None

    def _recovery_hash(self, code: str) -> str:
        return hmac.new(
            self.settings.password_pepper.encode(),
            ("recovery:" + code.strip()).encode(),
            hashlib.sha256,
        ).hexdigest()

    def create_username_user(
        self,
        username: str,
        password: str,
        display_name: str | None,
    ) -> tuple[dict, str]:
        user_id, timestamp = f"usr_{uuid4().hex}", now()
        code = secrets.token_urlsafe(24)
        password_hash = self._hash_password(password)
        try:
            with self.connection() as db:
                db.execute(
                    """INSERT INTO users(id,username,display_name,password_hash,
                    recovery_hash,created_at,updated_at) VALUES(?,?,?,?,?,?,?)""",
                    (
                        user_id,
                        username.lower(),
                        display_name,
                        password_hash,
                        self._recovery_hash(code),
                        timestamp,
                        timestamp,
                    ),
                )
        except sqlite3.IntegrityError as exc:
            raise StoreConflict from exc
        return self.user(user_id), code

    def verify_password(self, user: dict | None, password: str) -> bool:
        stored = user.get("password_hash") if user else None
        if not stored:
            self._password_derive(password, b"dummy-login-salt", PBKDF2_ITERATIONS)
            return False
        _, iterations, salt_hex, digest_hex = stored.split("$")
        digest = self._password_derive(password, bytes.fromhex(salt_hex), int(iterations))
        return hmac.compare_digest(digest, bytes.fromhex(digest_hex))

    def replace_credentials(
        self,
        user: dict,
        *,
        password: str | None = None,
        recovery_code: str | None = None,
        rotate_recovery: bool = False,
    ) -> str | None:
        code = secrets.token_urlsafe(24) if rotate_recovery else None
        password_hash = self._hash_password(password) if password else user["password_hash"]
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            current = db.execute(
                "SELECT * FROM users WHERE id=? AND archived_at IS NULL", (user["id"],)
            ).fetchone()
            if not current or current["auth_version"] != user["auth_version"]:
                raise StoreConflict
            if recovery_code is not None and (
                not current["recovery_hash"]
                or not hmac.compare_digest(
                    current["recovery_hash"], self._recovery_hash(recovery_code)
                )
            ):
                raise NotFound
            db.execute(
                """UPDATE users SET password_hash=?,recovery_hash=?,
                auth_version=auth_version+1,updated_at=? WHERE id=?""",
                (
                    password_hash,
                    self._recovery_hash(code) if code else current["recovery_hash"],
                    now(),
                    user["id"],
                ),
            )
            db.execute("DELETE FROM refresh_sessions WHERE subject=?", (user["id"],))
        return code

    def set_username(self, user_id: str, username: str, password: str) -> str:
        code = secrets.token_urlsafe(24)
        user = self.user(user_id)
        if user["username"]:
            raise StoreConflict
        if not self.verify_password(user, password):
            raise NotFound
        try:
            with self.connection() as db:
                changed = db.execute(
                    """UPDATE users SET username=?,recovery_hash=?,updated_at=?
                    WHERE id=? AND username IS NULL AND auth_version=?""",
                    (username, self._recovery_hash(code), now(), user_id, user["auth_version"]),
                )
                if changed.rowcount != 1:
                    raise StoreConflict
        except sqlite3.IntegrityError as exc:
            raise StoreConflict from exc
        return code

    def consume_limits(self, limits: list[tuple[str, str, int, int]]) -> int | None:
        """Reserve all budgets atomically. Failed reservations consume nothing."""
        current = int(time.time())
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM usage_counters WHERE expires_at<=?", (current,))
            reservations = []
            for scope, identity, maximum, window in limits:
                bucket = current // window
                expires = (bucket + 1) * window
                row = db.execute(
                    "SELECT count FROM usage_counters WHERE scope=? AND identity=? AND bucket=?",
                    (scope, identity, bucket),
                ).fetchone()
                if row and row["count"] >= maximum:
                    return expires - current
                reservations.append((scope, identity, bucket, expires))
            for scope, identity, bucket, expires in reservations:
                db.execute(
                    """INSERT INTO usage_counters VALUES(?,?,?,1,?)
                    ON CONFLICT(scope,identity,bucket) DO UPDATE SET count=count+1""",
                    (scope, identity, bucket, expires),
                )
            for scope, identity, maximum, window in limits:
                if scope.startswith("ocr-"):
                    count = db.execute(
                        "SELECT count FROM usage_counters WHERE scope=?"
                        " AND identity=? AND bucket=?",
                        (scope, identity, current // window),
                    ).fetchone()["count"]
                    if count >= max(1, int(maximum * 0.8)):
                        logging.getLogger(__name__).warning(
                            "OCR_QUOTA_ALERT scope=%s count=%s limit=%s", scope, count, maximum
                        )
        return None

    def delete_user(self, user_id: str, auth_version: int | None = None) -> None:
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            user = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
            if not user:
                raise NotFound
            if auth_version is not None and user["auth_version"] != auth_version:
                raise StoreConflict
            for table in (
                "import_references",
                "holding_imports",
                "transactions",
                "holdings",
                "accounts",
                "sources",
                "ocr_documents",
                "user_settings",
                "idempotency",
            ):
                db.execute(f"DELETE FROM {table} WHERE user_id=?", (user_id,))
            db.execute("DELETE FROM refresh_sessions WHERE subject=?", (user_id,))
            db.execute(
                "DELETE FROM password_attempts WHERE email IN (?,?)",
                (user["email"], user["username"]),
            )
            if user["email"]:
                db.execute("DELETE FROM email_codes WHERE email=?", (user["email"],))
            db.execute("DELETE FROM users WHERE id=?", (user_id,))

    def export_user(self, user_id: str) -> dict:
        with self.connection() as db:
            result = {"exported_at": now(), "format_version": 1}
            for table in ("accounts", "holdings", "transactions"):
                result[table] = [
                    self._decode_row(row)
                    for row in db.execute(f"SELECT * FROM {table} WHERE user_id=?", (user_id,))
                ]
            return result

    # --- password credentials ---

    def _password_derive(self, password: str, salt: bytes, iterations: int) -> bytes:
        pepper = self.settings.password_pepper.encode()
        mix = hmac.new(pepper, password.encode(), hashlib.sha256).digest()
        return hashlib.pbkdf2_hmac("sha256", mix, salt, iterations)

    def _hash_password(self, password: str) -> str:
        salt = secrets.token_bytes(16)
        digest = self._password_derive(password, salt, PBKDF2_ITERATIONS)
        return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"

    def set_user_password(self, user_id: str, password: str) -> None:
        with self.connection() as db:
            db.execute(
                "UPDATE users SET password_hash=?, updated_at=? WHERE id=?",
                (self._hash_password(password), now(), user_id),
            )

    def verify_user_password(self, email: str, password: str) -> bool:
        with self.connection() as db:
            row = db.execute(
                "SELECT password_hash FROM users WHERE email=? AND archived_at IS NULL",
                (email,),
            ).fetchone()
        stored = row["password_hash"] if row else None
        if not stored:
            # 无密码账号与密码错误保持相近耗时，避免时序枚举
            self._password_derive(password, secrets.token_bytes(16), PBKDF2_ITERATIONS)
            return False
        _, iterations, salt_hex, digest_hex = stored.split("$")
        digest = self._password_derive(password, bytes.fromhex(salt_hex), int(iterations))
        return hmac.compare_digest(digest, bytes.fromhex(digest_hex))

    def password_login_blocked(self, email: str) -> int | None:
        with self.connection() as db:
            cutoff = (datetime.now(UTC) - timedelta(days=1)).isoformat()
            db.execute("DELETE FROM password_attempts WHERE updated_at<?", (cutoff,))
            row = db.execute(
                "SELECT locked_until FROM password_attempts WHERE email=?", (email,)
            ).fetchone()
        if not row or not row["locked_until"]:
            return None
        remaining = datetime.fromisoformat(row["locked_until"]) - datetime.now(UTC)
        if remaining <= timedelta(0):
            return None
        return int(remaining.total_seconds()) + 1

    def record_password_login_failure(self, email: str, maximum: int | None = None) -> None:
        timestamp = now()
        with self.connection() as db:
            row = db.execute(
                "SELECT failed, locked_until FROM password_attempts WHERE email=?",
                (email,),
            ).fetchone()
            if not row or (
                row["locked_until"]
                and datetime.fromisoformat(row["locked_until"]) <= datetime.now(UTC)
            ):
                failed = 1
            else:
                failed = row["failed"] + 1
            if failed >= (maximum or self.settings.password_max_attempts):
                lock_until = datetime.now(UTC) + timedelta(
                    seconds=self.settings.password_lockout_seconds
                )
                db.execute(
                    """INSERT INTO password_attempts VALUES(?,?,?,?)
                    ON CONFLICT(email) DO UPDATE SET failed=0,
                    locked_until=excluded.locked_until,
                    updated_at=excluded.updated_at""",
                    (email, failed, lock_until.isoformat(), timestamp),
                )
            else:
                db.execute(
                    """INSERT INTO password_attempts VALUES(?,?,NULL,?)
                    ON CONFLICT(email) DO UPDATE SET failed=excluded.failed,
                    locked_until=NULL, updated_at=excluded.updated_at""",
                    (email, failed, timestamp),
                )

    def clear_password_login_failures(self, email: str) -> None:
        with self.connection() as db:
            db.execute("DELETE FROM password_attempts WHERE email=?", (email,))

    def save_refresh_session(
        self,
        token_hash: str,
        subject: str,
        expires_at: int,
        session_type: str,
        auth_version: int,
    ) -> None:
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            if session_type == "USER":
                user = db.execute(
                    "SELECT auth_version FROM users WHERE id=? AND archived_at IS NULL", (subject,)
                ).fetchone()
                if not user or user["auth_version"] != auth_version:
                    raise NotFound
            db.execute("DELETE FROM refresh_sessions WHERE expires_at<=?", (int(time.time()),))
            db.execute(
                """INSERT INTO refresh_sessions(token_hash,subject,expires_at,created_at,
                   session_type,auth_version) VALUES(?,?,?,?,?,?)""",
                (token_hash, subject, expires_at, now(), session_type, auth_version),
            )
            # Keep only the newest sessions per subject so one identity cannot grow the table.
            db.execute(
                """DELETE FROM refresh_sessions WHERE subject=? AND token_hash NOT IN (
                   SELECT token_hash FROM refresh_sessions WHERE subject=?
                   ORDER BY rowid DESC LIMIT ?)""",
                (subject, subject, MAX_REFRESH_SESSIONS.get(session_type, 20)),
            )

    def consume_refresh_session(self, token_hash: str, current_time: int) -> dict | None:
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute(
                "SELECT * FROM refresh_sessions WHERE token_hash=?",
                (token_hash,),
            ).fetchone()
            db.execute("DELETE FROM refresh_sessions WHERE token_hash=?", (token_hash,))
            db.execute("DELETE FROM refresh_sessions WHERE expires_at<=?", (current_time,))
        if not row or row["expires_at"] <= current_time:
            return None
        return dict(row)

    def revoke_refresh_sessions(self, subject: str) -> None:
        with self.connection() as db:
            db.execute("DELETE FROM refresh_sessions WHERE subject=?", (subject,))

    def save_access_revocation(self, token_hash: str, expires_at: int) -> None:
        with self.connection() as db:
            db.execute(
                "INSERT OR REPLACE INTO revoked_access_tokens VALUES(?,?,?)",
                (token_hash, expires_at, now()),
            )

    def is_access_revoked(self, token_hash: str, current_time: int) -> bool:
        with self.connection() as db:
            db.execute("DELETE FROM revoked_access_tokens WHERE expires_at<=?", (current_time,))
            return (
                db.execute(
                    "SELECT 1 FROM revoked_access_tokens WHERE token_hash=?",
                    (token_hash,),
                ).fetchone()
                is not None
            )

    def setting(self, user_id: str, kind: str) -> dict[str, Any] | None:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM user_settings WHERE user_id=? AND kind=?", (user_id, kind)
            ).fetchone()
            return (
                None
                if not row
                else {
                    **json.loads(row["payload"]),
                    "version": row["version"],
                    "updated_at": row["updated_at"],
                }
            )

    def save_setting(
        self, user_id: str, kind: str, payload: dict[str, Any], expected_version: int | None
    ) -> dict[str, Any]:
        current = self.setting(user_id, kind)
        if current and expected_version != current["version"]:
            raise StoreConflict
        if not current and expected_version not in (None, 0):
            raise StoreConflict
        version, timestamp = (current["version"] + 1 if current else 1), now()
        clean = {k: v for k, v in payload.items() if k != "version"}
        with self.connection() as db:
            db.execute(
                """INSERT INTO user_settings VALUES(?,?,?,?,?) ON CONFLICT(user_id,kind)
                DO UPDATE SET payload=excluded.payload,version=excluded.version,
                updated_at=excluded.updated_at""",
                (user_id, kind, json.dumps(clean, ensure_ascii=False), version, timestamp),
            )
        return {**clean, "version": version, "updated_at": timestamp}

    def list_rows(self, table: str, user_id: str, **filters: str | None) -> list[dict[str, Any]]:
        allowed = {
            "accounts",
            "holdings",
            "transactions",
            "sources",
            "holding_imports",
            "import_references",
            "ocr_documents",
        }
        if table not in allowed:
            raise ValueError(table)
        clauses, values = ["user_id=?", "archived_at IS NULL"], [user_id]
        for key, value in filters.items():
            if value is not None:
                clauses.append(f"{key}=?")
                values.append(value)
        with self.connection() as db:
            rows = db.execute(
                f"SELECT * FROM {table} WHERE {' AND '.join(clauses)} ORDER BY created_at", values
            ).fetchall()
        output = []
        for row in rows:
            item = dict(row)
            for key in ("metadata", "payload"):
                if key in item:
                    item[key] = json.loads(item[key])
            output.append(item)
        return output

    def ledger_snapshot(self, user_id: str) -> dict[str, Any]:
        """Read all ledger inputs from one SQLite transaction, including across workers."""
        with self.connection() as db:
            db.execute("BEGIN")
            result = {
                table: [
                    self._decode_row(row)
                    for row in db.execute(
                        f"SELECT * FROM {table} WHERE user_id=? AND archived_at IS NULL "
                        "ORDER BY created_at",
                        (user_id,),
                    ).fetchall()
                ]
                for table in ("accounts", "holdings", "transactions", "sources")
            }
            for row in db.execute("SELECT * FROM user_settings WHERE user_id=?", (user_id,)):
                result[row["kind"]] = {**json.loads(row["payload"]), "version": row["version"]}
            return result

    def owns(self, table: str, user_id: str, row_id: str) -> bool:
        if table not in {
            "accounts",
            "holdings",
            "transactions",
            "sources",
            "holding_imports",
            "import_references",
            "ocr_documents",
        }:
            raise ValueError(table)
        with self.connection() as db:
            return (
                db.execute(
                    f"SELECT 1 FROM {table} WHERE id=? AND user_id=? AND archived_at IS NULL",
                    (row_id, user_id),
                ).fetchone()
                is not None
            )

    @staticmethod
    def _upsert_schema(table: str) -> tuple[str, list[str]]:
        schemas = {
            "accounts": (
                "acc",
                ["name", "institution", "account_type", "currency", "opening_balance"],
            ),
            "holdings": (
                "hol",
                [
                    "account_id",
                    "source_id",
                    "snapshot_date",
                    "instrument_id",
                    "name",
                    "asset_type",
                    "quantity",
                    "average_cost",
                    "currency",
                    "metadata",
                ],
            ),
            "transactions": (
                "txn",
                [
                    "account_id",
                    "to_account_id",
                    "source_id",
                    "holding_id",
                    "transaction_type",
                    "amount",
                    "quantity",
                    "price",
                    "currency",
                    "occurred_at",
                    "note",
                ],
            ),
            "sources": ("src", ["name"]),
            "holding_imports": ("imp", ["status", "payload"]),
            "import_references": (
                "ref",
                [
                    "batch_id",
                    "document_id",
                    "row_id",
                    "holding_id",
                    "image_sha256",
                    "snapshot_date",
                    "decision",
                    "applied_at",
                ],
            ),
        }
        return schemas[table]

    @staticmethod
    def _decode_row(row: sqlite3.Row) -> dict[str, Any]:
        item = dict(row)
        for key in ("metadata", "payload"):
            if key in item:
                item[key] = json.loads(item[key])
        return item

    def _upsert_with_connection(
        self,
        db: sqlite3.Connection,
        table: str,
        user_id: str,
        data: dict[str, Any],
    ) -> dict[str, Any]:
        prefix, fields = self._upsert_schema(table)
        row_id = data.get(f"{table[:-1]}_id") or data.get("id") or f"{prefix}_{uuid4().hex}"
        current = db.execute(
            f"SELECT * FROM {table} WHERE id=? AND user_id=?", (row_id, user_id)
        ).fetchone()
        if not current and db.execute(f"SELECT 1 FROM {table} WHERE id=?", (row_id,)).fetchone():
            raise NotFound
        expected = data.get("version")
        if current and expected != current["version"]:
            raise StoreConflict
        if not current and expected not in (None, 0):
            raise StoreConflict
        timestamp = now()
        version = current["version"] + 1 if current else 1
        values = []
        for field in fields:
            value = data.get(field)
            if field == "metadata":
                incoming = value or {}
                previous = json.loads(current["metadata"]) if current else {}
                metadata = {**previous, **incoming}
                # Clients may edit prices, but cannot claim a provider source or timestamp.
                new_price = metadata.get("current_price")
                old_price = previous.get("current_price")
                same_price = (new_price is None and old_price is None) or (
                    new_price is not None
                    and old_price is not None
                    and Decimal(str(new_price)) == Decimal(str(old_price))
                )
                if current and (
                    current["instrument_id"] != data.get("instrument_id")
                    or current["currency"] != data.get("currency")
                ):
                    same_price = False
                if not same_price:
                    metadata["market_source"] = "manual"
                    metadata["price_as_of"] = (
                        timestamp if incoming.get("current_price") is not None else None
                    )
                else:
                    metadata["market_source"] = previous.get("market_source", "manual")
                    metadata["price_as_of"] = previous.get("price_as_of")
                value = json.dumps(metadata, ensure_ascii=False)
            elif field == "payload":
                value = json.dumps(value or {}, ensure_ascii=False)
            values.append(value)
        if current:
            assignments = ",".join(f"{field}=?" for field in fields)
            db.execute(
                f"UPDATE {table} SET {assignments},version=?,updated_at=?,"
                "archived_at=NULL WHERE id=? AND user_id=?",
                (*values, version, timestamp, row_id, user_id),
            )
        else:
            columns = ",".join(fields)
            placeholders = ",".join("?" for _ in fields)
            db.execute(
                f"INSERT INTO {table}(id,user_id,{columns},version,created_at,"
                f"updated_at,archived_at) VALUES(?,?,{placeholders},?,?,?,NULL)",
                (row_id, user_id, *values, version, timestamp, timestamp),
            )
        saved = db.execute(
            f"SELECT * FROM {table} WHERE id=? AND user_id=?", (row_id, user_id)
        ).fetchone()
        return self._decode_row(saved)

    def upsert(self, table: str, user_id: str, data: dict[str, Any]) -> dict[str, Any]:
        with self.connection() as db:
            return self._upsert_with_connection(db, table, user_id, data)

    def idempotent_batch_upsert(
        self,
        table: str,
        user_id: str,
        scope: str,
        request_id: str,
        body: dict[str, Any],
        items: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        """Write a whole import and its idempotency record in one transaction."""
        fingerprint = hashlib.sha256(
            json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest()
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM idempotency WHERE user_id=? AND scope=? AND request_id=?",
                (user_id, scope, request_id),
            ).fetchone()
            if row:
                if row["fingerprint"] != fingerprint:
                    raise StoreConflict
                return json.loads(row["response"])
            saved = [self._upsert_with_connection(db, table, user_id, item) for item in items]
            db.execute(
                "INSERT INTO idempotency VALUES(?,?,?,?,?,?)",
                (
                    user_id,
                    scope,
                    request_id,
                    fingerprint,
                    json.dumps(saved, ensure_ascii=False),
                    now(),
                ),
            )
            return saved

    def commit_holding_import(
        self,
        user_id: str,
        batch_id: str,
        expected_version: int,
        request_id: str,
        body: dict[str, Any],
        items: list[dict[str, Any]],
        references: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """Commit selected import rows, references and batch state atomically."""
        fingerprint = hashlib.sha256(
            json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest()
        with self.connection() as db:
            existing_request = db.execute(
                "SELECT * FROM idempotency WHERE user_id=? AND scope=? AND request_id=?",
                (user_id, "holding-imports", request_id),
            ).fetchone()
            if existing_request:
                if existing_request["fingerprint"] != fingerprint:
                    raise StoreConflict
                return json.loads(existing_request["response"])
            batch = db.execute(
                "SELECT * FROM holding_imports WHERE id=? AND user_id=? AND archived_at IS NULL",
                (batch_id, user_id),
            ).fetchone()
            if not batch:
                raise NotFound
            if batch["version"] != expected_version:
                raise StoreConflict
            if batch["status"] not in {"READY", "PARTIAL"}:
                raise StoreConflict
            saved = [self._upsert_with_connection(db, "holdings", user_id, item) for item in items]
            saved_index = 0
            for reference in references:
                if reference.get("decision") != "SKIP":
                    reference["holding_id"] = saved[saved_index]["id"]
                    saved_index += 1
            timestamp = now()
            for reference in references:
                db.execute(
                    """INSERT INTO import_references(
                      id,user_id,batch_id,document_id,row_id,holding_id,image_sha256,
                      snapshot_date,decision,applied_at,created_at,updated_at,archived_at
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,NULL)""",
                    (
                        reference.get("id") or f"ref_{uuid4().hex}",
                        user_id,
                        batch_id,
                        reference.get("document_id"),
                        reference.get("row_id"),
                        reference.get("holding_id"),
                        reference.get("image_sha256"),
                        reference.get("snapshot_date"),
                        reference.get("decision", "ADD"),
                        timestamp,
                        timestamp,
                        timestamp,
                    ),
                )
            payload = json.loads(batch["payload"])
            payload["rows"] = body.get("rows", payload.get("rows", []))
            payload["status"] = (
                "COMPLETED"
                if all(
                    reference.get("decision") == "SKIP" or reference.get("holding_id")
                    for reference in references
                )
                else "PARTIAL"
            )
            new_version = batch["version"] + 1
            db.execute(
                "UPDATE holding_imports SET status=?,payload=?,version=?,updated_at=? "
                "WHERE id=? AND user_id=?",
                (
                    payload["status"],
                    json.dumps(payload, ensure_ascii=False),
                    new_version,
                    timestamp,
                    batch_id,
                    user_id,
                ),
            )
            response = {
                "items": saved,
                "batch_id": batch_id,
                "version": new_version,
                "status": payload["status"],
            }
            db.execute(
                "INSERT INTO idempotency VALUES(?,?,?,?,?,?)",
                (
                    user_id,
                    "holding-imports",
                    request_id,
                    fingerprint,
                    json.dumps(response, ensure_ascii=False),
                    timestamp,
                ),
            )
            return response

    def archive(self, table: str, user_id: str, row_id: str, version: int | None) -> dict[str, Any]:
        with self.connection() as db:
            row = db.execute(
                f"SELECT version FROM {table} WHERE id=? AND user_id=? AND archived_at IS NULL",
                (row_id, user_id),
            ).fetchone()
            if not row:
                raise NotFound
            if version != row["version"]:
                raise StoreConflict
            timestamp = now()
            db.execute(
                f"UPDATE {table} SET archived_at=?,updated_at=?,version=version+1 "
                "WHERE id=? AND user_id=?",
                (timestamp, timestamp, row_id, user_id),
            )
            return {
                "id": row_id,
                "archived": True,
                "version": row["version"] + 1,
                "updated_at": timestamp,
            }

    def idempotent(
        self, user_id: str, scope: str, request_id: str, body: dict[str, Any], operation: Any
    ) -> Any:
        fingerprint = hashlib.sha256(
            json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest()
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM idempotency WHERE user_id=? AND scope=? AND request_id=?",
                (user_id, scope, request_id),
            ).fetchone()
        if row:
            if row["fingerprint"] != fingerprint:
                raise StoreConflict
            return json.loads(row["response"])
        response = operation()
        with self.connection() as db:
            db.execute(
                "INSERT INTO idempotency VALUES(?,?,?,?,?,?)",
                (
                    user_id,
                    scope,
                    request_id,
                    fingerprint,
                    json.dumps(response, ensure_ascii=False),
                    now(),
                ),
            )
        return response

    def save_ocr(
        self, user_id: str, request_key: str, digest: str, payload: dict[str, Any]
    ) -> dict[str, Any]:
        timestamp = now()
        with self.connection() as db:
            existing = db.execute(
                "SELECT * FROM ocr_documents WHERE user_id=? AND request_key=?",
                (user_id, request_key),
            ).fetchone()
            if existing:
                return json.loads(existing["payload"])
            db.execute(
                "INSERT INTO ocr_documents VALUES(?,?,?,?,?,1,?,?,NULL)",
                (
                    payload["document_id"],
                    user_id,
                    request_key,
                    digest,
                    json.dumps(payload, ensure_ascii=False),
                    timestamp,
                    timestamp,
                ),
            )
        return payload

    def ocr_result(self, user_id: str, request_key: str, digest: str) -> dict | None:
        with self.connection() as db:
            row = db.execute(
                "SELECT * FROM ocr_documents WHERE user_id=? AND request_key=?",
                (user_id, request_key),
            ).fetchone()
            if not row:
                return None
            if row["archived_at"]:
                raise NotFound
            if row["image_sha256"] != digest:
                raise StoreConflict
            return json.loads(row["payload"])
