"""Encrypted off-site backups of the FinUnity database, and the restore drill that proves they work.

    python3 ops/offsite_backup.py backup --database data/finunity.db --backup-dir backups
    python3 ops/offsite_backup.py drill  --log /var/log/finunity-restore-drill.jsonl

A backup that has never been restored is a hope, not a backup. ``backup`` takes a consistent copy
with deploy/database.py (SQLite online backup plus integrity check), pushes it to an encrypted
restic repository on S3-compatible storage (Cloudflare R2, Backblaze B2, ...), applies retention
there and reports to a heartbeat URL. ``drill`` restores the newest copy into a temporary directory,
audits it, checks its age against the RPO and records how long the restore took.

Configuration comes from the environment and is never committed (see ops/README.md):
OFFSITE_BACKEND=restic|dir (default restic), RESTIC_REPOSITORY, RESTIC_PASSWORD_FILE and the storage
credentials restic itself reads, OFFSITE_DIR (for the dir backend), HC_PING_URL (optional
healthchecks.io-style heartbeat). Standard library only.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.request
from collections.abc import Callable, Mapping, Sequence
from contextlib import closing
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any, Protocol

ROOT = Path(__file__).resolve().parents[1]
TAG = "finunity"
BACKUP_NAME = re.compile(r"^finunity-(\d{8}T\d{6}Z)\.db$")
# The privacy page promises business backups are kept at most 14 days: change both together.
RETENTION_DAYS = 14
KEEP_LOCAL_DAYS = RETENTION_DAYS
DEFAULT_MAX_AGE_HOURS = 36.0  # the RPO the drill enforces: a daily backup may be at most this old


class OffsiteError(RuntimeError):
    """A failure the operator needs to see; the message is safe to log."""


class Backend(Protocol):
    def push(self, path: Path) -> None: ...

    def retain(self, now: datetime) -> None: ...

    def verify_repository(self) -> None: ...

    def latest(self, workdir: Path) -> tuple[Path, datetime]: ...


def load_database_tool() -> Any:
    """deploy/database.py is an operator script, not a package: load it by path."""
    spec = importlib.util.spec_from_file_location(
        "finunity_database_ops", ROOT / "deploy" / "database.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def backup_name(taken_at: datetime) -> str:
    return f"finunity-{taken_at.astimezone(UTC).strftime('%Y%m%dT%H%M%SZ')}.db"


def audit(path: Path) -> dict[str, Any]:
    """Open a backup read-only and check what a restore depends on.

    `closing`, not `with sqlite3.connect(...)`: the latter only ends a transaction and leaves the
    file open, which keeps a restored copy of user data from being deleted on Windows."""
    with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        return {
            "users": db.execute("SELECT count(*) FROM users").fetchone()[0],
            "integrity": db.execute("PRAGMA integrity_check").fetchone()[0],
            "foreign_key_errors": len(db.execute("PRAGMA foreign_key_check").fetchall()),
        }


def require_healthy(report: Mapping[str, Any]) -> None:
    if report["integrity"] != "ok" or report["foreign_key_errors"]:
        raise OffsiteError(f"the backup failed its audit: {dict(report)}")


class DirectoryBackend:
    """Copies backups into another directory (second disk, share, rehearsal). Not encrypted."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory

    def push(self, path: Path) -> None:
        self.directory.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, self.directory / path.name)

    def retain(self, now: datetime) -> None:
        # Old copies would outlive the published retention (and a deleted account's data with them).
        prune_local(self.directory, RETENTION_DAYS, now)

    def verify_repository(self) -> None:
        if not self.directory.is_dir():
            raise OffsiteError(f"off-site directory {self.directory} does not exist")

    def latest(self, workdir: Path) -> tuple[Path, datetime]:
        names = sorted(
            path.name
            for path in self.directory.glob("finunity-*.db")
            if BACKUP_NAME.match(path.name)
        )
        if not names:
            raise OffsiteError("the off-site directory holds no backups")
        restored = workdir / names[-1]
        shutil.copy2(self.directory / names[-1], restored)
        taken_at = datetime.strptime(BACKUP_NAME.match(names[-1])[1], "%Y%m%dT%H%M%SZ").replace(
            tzinfo=UTC
        )
        return restored, taken_at


class ResticBackend:
    """Encrypted, deduplicated copies in an S3-compatible bucket, driven through the restic CLI.

    Credentials (RESTIC_PASSWORD_FILE, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, ...) are read by
    restic from the environment; nothing secret passes through this code or its log lines."""

    def __init__(
        self,
        repository: str,
        password_file: str,
        runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
        binary: str = "restic",
    ) -> None:
        self.repository, self.password_file, self.runner, self.binary = (
            repository,
            password_file,
            runner,
            binary,
        )

    def _run(self, *args: str) -> str:
        env = {
            **os.environ,
            "RESTIC_REPOSITORY": self.repository,
            "RESTIC_PASSWORD_FILE": self.password_file,
        }
        try:
            done = self.runner(
                [self.binary, *args], env=env, capture_output=True, text=True, timeout=3600
            )
        except FileNotFoundError as error:
            raise OffsiteError(f"{self.binary} is not installed or not on PATH") from error
        if done.returncode:
            raise OffsiteError(
                f"restic {args[0]} failed (exit {done.returncode}): {done.stderr.strip()[-300:]}"
            )
        return done.stdout

    def push(self, path: Path) -> None:
        self._run("backup", str(path), "--tag", TAG, "--host", TAG)

    def retain(self, now: datetime) -> None:
        # Group by host and tag, not by path: every backup file has a new, timestamped path, so the
        # default grouping would put each snapshot in a group of its own and never prune anything.
        # Only the published retention is kept: longer history would hold deleted accounts' data.
        self._run(
            "forget",
            "--tag",
            TAG,
            "--host",
            TAG,
            "--group-by",
            "host,tags",
            "--keep-within",
            f"{RETENTION_DAYS}d",
            "--prune",
        )

    def verify_repository(self) -> None:
        self._run("check", "--read-data-subset=5%")

    def latest(self, workdir: Path) -> tuple[Path, datetime]:
        snapshots = json.loads(
            self._run("snapshots", "--tag", TAG, "--host", TAG, "--json") or "[]"
        )
        if not snapshots:
            raise OffsiteError("the repository holds no snapshots")
        taken_at = max(datetime.fromisoformat(item["time"]).astimezone(UTC) for item in snapshots)
        self._run("restore", "latest", "--tag", TAG, "--host", TAG, "--target", str(workdir))
        found = sorted(
            workdir.rglob("finunity-*.db")
        )  # restic recreates the original directory layout
        if not found:
            raise OffsiteError("the restored snapshot contains no finunity database file")
        return found[-1], taken_at


def backend_from_env(env: Mapping[str, str] = os.environ) -> Backend:
    kind = env.get("OFFSITE_BACKEND", "restic")
    if kind == "dir":
        if not env.get("OFFSITE_DIR"):
            raise OffsiteError("OFFSITE_DIR must be set for OFFSITE_BACKEND=dir")
        return DirectoryBackend(Path(env["OFFSITE_DIR"]))
    if kind == "restic":
        if not env.get("RESTIC_REPOSITORY") or not env.get("RESTIC_PASSWORD_FILE"):
            raise OffsiteError(
                "RESTIC_REPOSITORY and RESTIC_PASSWORD_FILE must be set (see ops/README.md)"
            )
        return ResticBackend(env["RESTIC_REPOSITORY"], env["RESTIC_PASSWORD_FILE"])
    raise OffsiteError(f"unknown OFFSITE_BACKEND {kind!r}")


def ping(
    url: str | None, suffix: str = "", opener: Callable[..., Any] = urllib.request.urlopen
) -> None:
    """healthchecks.io-style heartbeat: GET <url> on success, <url>/fail on failure. No raise."""
    if not url:
        return
    try:
        opener(url.rstrip("/") + suffix, timeout=10).close()
    except OSError as error:
        print(f"heartbeat not delivered: {type(error).__name__}", file=sys.stderr)


def prune_local(backup_dir: Path, keep_days: int, now: datetime) -> list[str]:
    """Delete only this tool's own expired backups: a file must match the naming pattern exactly."""
    removed = []
    for path in sorted(backup_dir.iterdir()):
        match = BACKUP_NAME.match(path.name)
        if not match or not path.is_file():
            continue
        taken_at = datetime.strptime(match[1], "%Y%m%dT%H%M%SZ").replace(tzinfo=UTC)
        if now - taken_at > timedelta(days=keep_days):
            path.unlink()
            removed.append(path.name)
    return removed


def run_backup(
    database: Path,
    backup_dir: Path,
    backend: Backend,
    *,
    heartbeat: str | None = None,
    now: datetime | None = None,
    keep_local_days: int = KEEP_LOCAL_DAYS,
    database_tool: Any = None,
) -> Path:
    now = now or datetime.now(UTC)
    try:
        target = backup_dir / backup_name(now)
        (database_tool or load_database_tool()).backup(
            database, target
        )  # online backup + integrity check
        require_healthy(audit(target))
        backend.push(target)
        backend.retain(now)
        prune_local(backup_dir, keep_local_days, now)
    except Exception:
        ping(heartbeat, "/fail")
        raise
    ping(heartbeat)
    return target


def run_drill(
    backend: Backend,
    *,
    max_age_hours: float = DEFAULT_MAX_AGE_HOURS,
    log: Path | None = None,
    heartbeat: str | None = None,
    now: datetime | None = None,
    check_repository: bool = True,
) -> dict[str, Any]:
    """Restore the newest off-site copy into a temp directory, audit it, and time the restore."""
    now = now or datetime.now(UTC)
    workdir = Path(tempfile.mkdtemp(prefix="finunity-drill-"))
    started = time.monotonic()
    try:
        if check_repository:
            backend.verify_repository()
        restored, taken_at = backend.latest(workdir)
        report = audit(restored)
        require_healthy(report)
        age_hours = (now - taken_at).total_seconds() / 3600
        if age_hours > max_age_hours:
            raise OffsiteError(
                f"the newest off-site backup is {age_hours:.1f} h old (limit {max_age_hours:g} h): "
                "the recovery point objective is not being met"
            )
        result = {
            "at": now.isoformat(timespec="seconds"),
            "restore_seconds": round(time.monotonic() - started, 1),
            "restored_bytes": restored.stat().st_size,
            "snapshot_age_hours": round(age_hours, 1),
            **report,
        }
        if log:
            log.parent.mkdir(parents=True, exist_ok=True)
            with log.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(result) + "\n")
    except Exception:
        ping(heartbeat, "/fail")
        raise
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
    ping(heartbeat)
    return result


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    backup = commands.add_parser("backup", help="back up, push off-site, apply retention")
    backup.add_argument("--database", type=Path, required=True)
    backup.add_argument("--backup-dir", type=Path, required=True)
    backup.add_argument("--keep-local-days", type=int, default=KEEP_LOCAL_DAYS)
    drill = commands.add_parser("drill", help="restore the newest off-site copy and audit it")
    drill.add_argument("--max-age-hours", type=float, default=DEFAULT_MAX_AGE_HOURS)
    drill.add_argument("--log", type=Path)
    drill.add_argument("--skip-repository-check", action="store_true")
    args = parser.parse_args(argv)
    heartbeat = os.environ.get("HC_PING_URL")
    try:
        backend = backend_from_env()
        if args.command == "backup":
            path = run_backup(
                args.database,
                args.backup_dir,
                backend,
                heartbeat=heartbeat,
                keep_local_days=args.keep_local_days,
            )
            print(f"backed up and pushed off-site: {path.name}")
        else:
            result = run_drill(
                backend,
                max_age_hours=args.max_age_hours,
                log=args.log,
                heartbeat=heartbeat,
                check_repository=not args.skip_repository_check,
            )
            print(json.dumps(result))
    except (OffsiteError, ValueError, OSError, sqlite3.Error) as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
