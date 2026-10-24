"""Off-site backup and restore drill (ops/offsite_backup.py): the whole flow runs here on a real
SQLite database through the directory backend; the restic backend is checked at the command level
because restic itself is not available in the test environment."""

import json
import subprocess
import sys
import tempfile
import threading
from datetime import UTC, datetime, timedelta
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pytest

from finunity_server.config import Settings
from finunity_server.store import SQLiteStore

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ops"))
import offsite_backup as ops  # noqa: E402

NOW = datetime(2026, 10, 5, 3, 15, tzinfo=UTC)


class Heartbeat(BaseHTTPRequestHandler):
    paths: list[str] = []

    def do_GET(self):
        Heartbeat.paths.append(self.path)
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


@pytest.fixture
def heartbeat():
    Heartbeat.paths = []
    server = HTTPServer(("127.0.0.1", 0), Heartbeat)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{server.server_port}/ping/abc"
    server.shutdown()
    server.server_close()


@pytest.fixture
def database(tmp_path):
    path = tmp_path / "live.db"
    store = SQLiteStore(Settings(database_path=str(path)))
    store.create_username_user("drill_user", "secure-test-pass-9", None)
    return path


def drill_dirs() -> set[str]:
    return {path.name for path in Path(tempfile.gettempdir()).glob("finunity-drill-*")}


def test_backup_pushes_an_audited_copy_off_site_and_reports_success(tmp_path, database, heartbeat):
    backend = ops.DirectoryBackend(tmp_path / "offsite")
    target = ops.run_backup(database, tmp_path / "backups", backend, heartbeat=heartbeat, now=NOW)
    assert target.name == "finunity-20261005T031500Z.db" and target.is_file()
    pushed = tmp_path / "offsite" / target.name
    assert pushed.read_bytes() == target.read_bytes()
    assert ops.audit(pushed) == {"users": 1, "integrity": "ok", "foreign_key_errors": 0}
    assert Heartbeat.paths == ["/ping/abc"]


def test_the_directory_backend_drops_only_expired_backups(tmp_path, database):
    """The privacy page promises 14 days: an old off-site copy must not outlive it."""
    offsite = tmp_path / "offsite"
    offsite.mkdir()
    (offsite / "finunity-20261001T031500Z.db").write_bytes(b"4 days old: kept")
    (offsite / "finunity-20260920T031500Z.db").write_bytes(b"15 days old: expired")
    (offsite / "notes.txt").write_text("not ours: never touched")
    ops.run_backup(database, tmp_path / "backups", ops.DirectoryBackend(offsite), now=NOW)
    assert sorted(path.name for path in offsite.iterdir()) == [
        "finunity-20261001T031500Z.db",
        "finunity-20261005T031500Z.db",
        "notes.txt",
    ]


def test_retention_stays_at_the_published_14_days():
    assert ops.RETENTION_DAYS == 14 and ops.KEEP_LOCAL_DAYS == 14


def test_a_failing_push_reports_failure_and_raises(tmp_path, database, heartbeat):
    class FullDisk(ops.DirectoryBackend):
        def push(self, path):
            raise ops.OffsiteError("no space left on device")

    with pytest.raises(ops.OffsiteError, match="no space"):
        ops.run_backup(
            database,
            tmp_path / "backups",
            FullDisk(tmp_path / "offsite"),
            heartbeat=heartbeat,
            now=NOW,
        )
    assert Heartbeat.paths == ["/ping/abc/fail"]  # never a success ping


def test_a_corrupt_local_backup_is_never_pushed(tmp_path, database, heartbeat):
    class GarbageTool:
        @staticmethod
        def backup(source, target):
            Path(target).parent.mkdir(parents=True, exist_ok=True)
            Path(target).write_bytes(b"this is not a database" * 100)

    pushed = []

    class Spy(ops.DirectoryBackend):
        def push(self, path):
            pushed.append(path)

    with pytest.raises(Exception):  # noqa: B017  (sqlite3.DatabaseError: file is not a database)
        ops.run_backup(
            database,
            tmp_path / "backups",
            Spy(tmp_path / "offsite"),
            heartbeat=heartbeat,
            now=NOW,
            database_tool=GarbageTool,
        )
    assert pushed == [] and Heartbeat.paths == ["/ping/abc/fail"]


def test_local_pruning_only_removes_expired_backups_with_this_tools_naming(tmp_path):
    keep = {
        "finunity-20261004T000000Z.db": b"recent",
        "finunity-manual-before-upgrade.db": b"an operator's own file",
        "notes.txt": b"unrelated",
    }
    for name, data in keep.items():
        (tmp_path / name).write_bytes(data)
    (tmp_path / "finunity-20260101T000000Z.db").write_bytes(b"expired")
    (tmp_path / "finunity-20250101T000000Z.db").mkdir()  # a directory with a matching name stays
    assert ops.prune_local(tmp_path, 14, NOW) == ["finunity-20260101T000000Z.db"]
    assert {path.name for path in tmp_path.iterdir()} == set(keep) | {
        "finunity-20250101T000000Z.db"
    }


def test_drill_restores_audits_records_and_cleans_up(tmp_path, database, heartbeat):
    backend = ops.DirectoryBackend(tmp_path / "offsite")
    ops.run_backup(database, tmp_path / "backups", backend, now=NOW)
    before = drill_dirs()
    log = tmp_path / "logs" / "drill.jsonl"
    result = ops.run_drill(backend, log=log, heartbeat=heartbeat, now=NOW + timedelta(hours=1))
    assert (
        result["users"] == 1 and result["integrity"] == "ok" and result["foreign_key_errors"] == 0
    )
    assert result["snapshot_age_hours"] == 1.0 and result["restored_bytes"] > 0
    assert result["restore_seconds"] >= 0
    assert json.loads(log.read_text(encoding="utf-8").splitlines()[-1])["users"] == 1
    assert drill_dirs() == before  # the restored copy (it holds user data) is removed
    assert Heartbeat.paths == ["/ping/abc"]


def test_drill_fails_when_the_newest_backup_is_too_old_to_meet_the_rpo(
    tmp_path, database, heartbeat
):
    backend = ops.DirectoryBackend(tmp_path / "offsite")
    ops.run_backup(database, tmp_path / "backups", backend, now=NOW)
    with pytest.raises(ops.OffsiteError, match="recovery point objective"):
        ops.run_drill(backend, heartbeat=heartbeat, now=NOW + timedelta(days=3))
    assert Heartbeat.paths == ["/ping/abc/fail"]


def test_drill_detects_a_corrupt_off_site_copy(tmp_path, database, heartbeat):
    backend = ops.DirectoryBackend(tmp_path / "offsite")
    target = ops.run_backup(database, tmp_path / "backups", backend, now=NOW)
    (tmp_path / "offsite" / target.name).write_bytes(b"bit rot" * 200)
    before = drill_dirs()
    with pytest.raises(Exception):  # noqa: B017
        ops.run_drill(backend, heartbeat=heartbeat, now=NOW)
    assert Heartbeat.paths == ["/ping/abc/fail"] and drill_dirs() == before


def test_drill_with_nothing_off_site_fails_clearly(tmp_path):
    (tmp_path / "offsite").mkdir()
    with pytest.raises(ops.OffsiteError, match="holds no backups"):
        ops.run_drill(ops.DirectoryBackend(tmp_path / "offsite"), now=NOW)


# --- restic backend: command construction and parsing ---------------------------------------------


class FakeRestic:
    def __init__(
        self, workdir_file: Path | None = None, snapshots: str = "[]", returncode: int = 0
    ):
        self.calls, self.snapshots, self.returncode, self.workdir_file = (
            [],
            snapshots,
            returncode,
            workdir_file,
        )

    def __call__(self, argv, **kwargs):
        self.calls.append((argv, kwargs["env"]))
        stdout = self.snapshots if argv[1] == "snapshots" else ""
        if argv[1] == "restore" and self.workdir_file:
            target = (
                Path(argv[argv.index("--target") + 1])
                / "srv/finunity/backups"
                / self.workdir_file.name
            )
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(self.workdir_file.read_bytes())
        return subprocess.CompletedProcess(
            argv, self.returncode, stdout, "repository is locked" * 40
        )


def test_restic_commands_and_environment(tmp_path, database):
    source = tmp_path / "finunity-20261005T031500Z.db"
    source.write_bytes(database.read_bytes())
    snapshots = json.dumps(  # not sorted: the newest one must be chosen, whatever the order
        [
            {"time": "2026-10-03T03:15:01+08:00"},
            {"time": "2026-10-05T03:15:01.123456789+08:00"},
            {"time": "2026-10-04T03:15:01+08:00"},
        ]
    )
    runner = FakeRestic(workdir_file=source, snapshots=snapshots)
    backend = ops.ResticBackend(
        "s3:https://example.invalid/bucket", "/etc/restic.pass", runner=runner
    )
    backend.push(source)
    backend.retain(NOW)
    backend.verify_repository()
    restored, taken_at = backend.latest(tmp_path / "work")
    commands = [argv[1:] for argv, _ in runner.calls]
    assert commands[0] == ["backup", str(source), "--tag", "finunity", "--host", "finunity"]
    assert commands[1] == [
        "forget",
        "--tag",
        "finunity",
        "--host",
        "finunity",
        "--group-by",
        "host,tags",
        "--keep-within",
        "14d",  # the privacy page: business backups are kept at most 14 days
        "--prune",
    ]
    assert commands[2] == ["check", "--read-data-subset=5%"]
    assert commands[3] == ["snapshots", "--tag", "finunity", "--host", "finunity", "--json"]
    assert commands[4][:2] == ["restore", "latest"] and commands[4][-2] == "--target"
    assert restored.name == source.name and restored.read_bytes() == source.read_bytes()
    assert taken_at == datetime(
        2026, 10, 4, 19, 15, 1, 123456, tzinfo=UTC
    )  # +08:00 converted to UTC
    for _, env in runner.calls:
        assert env["RESTIC_REPOSITORY"] == "s3:https://example.invalid/bucket"
        assert env["RESTIC_PASSWORD_FILE"] == "/etc/restic.pass"


def test_restic_failures_are_reported_briefly(tmp_path):
    backend = ops.ResticBackend("repo", "pass", runner=FakeRestic(returncode=1))
    with pytest.raises(ops.OffsiteError) as raised:
        backend.push(tmp_path / "x.db")
    message = str(raised.value)
    assert "restic backup failed (exit 1)" in message and len(message) < 400


def test_a_missing_restic_binary_is_explained(tmp_path):
    def missing(argv, **kwargs):
        raise FileNotFoundError(argv[0])

    with pytest.raises(ops.OffsiteError, match="not installed"):
        ops.ResticBackend("repo", "pass", runner=missing).push(tmp_path / "x.db")


def test_an_empty_repository_has_no_snapshot_to_restore(tmp_path):
    with pytest.raises(ops.OffsiteError, match="no snapshots"):
        ops.ResticBackend("repo", "pass", runner=FakeRestic(snapshots="[]")).latest(tmp_path)


# --- configuration and command line ---------------------------------------------------------


def test_backend_selection_from_the_environment(tmp_path):
    assert isinstance(
        ops.backend_from_env({"OFFSITE_BACKEND": "dir", "OFFSITE_DIR": str(tmp_path)}),
        ops.DirectoryBackend,
    )
    restic = ops.backend_from_env({"RESTIC_REPOSITORY": "r", "RESTIC_PASSWORD_FILE": "p"})
    assert isinstance(restic, ops.ResticBackend)
    with pytest.raises(ops.OffsiteError, match="RESTIC_REPOSITORY"):
        ops.backend_from_env({})
    with pytest.raises(ops.OffsiteError, match="OFFSITE_DIR"):
        ops.backend_from_env({"OFFSITE_BACKEND": "dir"})
    with pytest.raises(ops.OffsiteError, match="unknown"):
        ops.backend_from_env({"OFFSITE_BACKEND": "tape"})


def test_command_line_end_to_end(tmp_path, database, monkeypatch, capsys):
    monkeypatch.setenv("OFFSITE_BACKEND", "dir")
    monkeypatch.setenv("OFFSITE_DIR", str(tmp_path / "offsite"))
    monkeypatch.delenv("HC_PING_URL", raising=False)
    assert (
        ops.main(["backup", "--database", str(database), "--backup-dir", str(tmp_path / "backups")])
        == 0
    )
    assert ops.main(["drill", "--log", str(tmp_path / "drill.jsonl")]) == 0
    assert json.loads(capsys.readouterr().out.splitlines()[-1])["users"] == 1
    assert (
        ops.main(
            [
                "backup",
                "--database",
                str(tmp_path / "missing.db"),
                "--backup-dir",
                str(tmp_path / "b"),
            ]
        )
        == 1
    )


def test_an_unreachable_heartbeat_never_fails_the_job(capsys):
    ops.ping("http://127.0.0.1:9/nobody")
    assert "heartbeat not delivered" in capsys.readouterr().err
    ops.ping(None)  # no heartbeat configured: nothing to do
