"""Operator-only SQLite audit/backup/session reset. Does not load application secrets."""

import argparse
import json
import sqlite3
from pathlib import Path


def backup(source: Path, target: Path) -> None:
    if not source.is_file():
        raise ValueError("Database does not exist")
    if target.exists() or source.resolve() == target.resolve():
        raise ValueError("Backup target must be a new, distinct path")
    target.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(source.resolve().as_uri() + "?mode=ro", uri=True) as src:
        with sqlite3.connect(target) as dst:
            target.chmod(0o600)
            src.backup(dst)
            if dst.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("Backup integrity check failed")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["audit", "backup", "revoke-sessions"])
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.action == "backup":
        if not args.output:
            parser.error("backup requires --output")
        backup(args.database, args.output)
        print("Backup created and integrity checked")
        return
    if not args.database.is_file():
        parser.error("database does not exist")
    mode = "ro" if args.action == "audit" else "rw"
    with sqlite3.connect(args.database.resolve().as_uri() + f"?mode={mode}", uri=True) as db:
        if args.action == "audit":
            columns = {row[1] for row in db.execute("PRAGMA table_info(users)")}
            data = {"users": db.execute("SELECT count(*) FROM users").fetchone()[0]}
            data["without_username"] = (
                db.execute("SELECT count(*) FROM users WHERE username IS NULL").fetchone()[0]
                if "username" in columns
                else data["users"]
            )
            data["integrity"] = db.execute("PRAGMA integrity_check").fetchone()[0]
            data["foreign_key_errors"] = len(db.execute("PRAGMA foreign_key_check").fetchall())
            print(json.dumps(data))
        else:
            db.execute("DELETE FROM refresh_sessions")
            db.commit()
            print("Refresh sessions cleared; rotate SESSION_SECRET to invalidate old access tokens")


if __name__ == "__main__":
    main()
