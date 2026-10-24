"""Synthetic-data checks for private baseline preparation; no personal fixtures."""

import copy
import json
import runpy
import subprocess
import zipfile
from pathlib import Path

import pytest

from finunity_server.contracts import HoldingsBatchRequest

bootstrap = runpy.run_path(str(Path(__file__).parents[1] / "tools/bootstrap_portfolio.py"))


def source_row(number, account, name, amount, *, include="是", symbol=None, bucket="进取"):
    return {
        "row": number,
        "cells": {
            "A": "test-owner",
            "B": account,
            "C": name,
            "D": "现金" if name == "cash" else "ETF",
            "E": bucket,
            "F": "USD",
            "G": amount,
            "K": amount,
            "J": include,
            "N": symbol,
            "P": "境外",
            "M": "synthetic",
        },
        "formulas": {},
    }


@pytest.fixture
def baseline():
    sheets = {
        "明细": [
            {"row": 1, "cells": {}, "formulas": {}},
            source_row(2, "汇丰", "cash", "100"),
            source_row(3, "汇丰", "VGT", "30", symbol="VGT"),
            source_row(4, "盈透", "old-position", "20", symbol="QQQ"),
            source_row(5, "盈立", "QQQ", "40", symbol="QQQ"),
            source_row(6, "汇丰", "card", "-5", bucket="防守"),
            source_row(7, "盈透", "account total", "20", include="否"),
        ],
        "持仓数量": [
            {
                "row": 2,
                "cells": {"A": "VGT", "B": "汇丰", "C": "3", "D": "USD", "E": "10", "K": "推断"},
            },
            {
                "row": 3,
                "cells": {"A": "QQQ", "B": "盈立", "C": "2", "D": "USD", "E": "20", "K": "读数"},
            },
        ],
    }
    return bootstrap["historical_positions"](sheets), sheets


@pytest.fixture
def observation():
    return {
        "positions": [
            {
                "symbol": "GOOGL",
                "quantity": "2",
                "last_price_usd": "12.5",
                "average_cost_inferred_usd": "11",
            }
        ],
        "cash_usd_approx": "50",
        "ely_positions_unchanged": {"QQQ": "2"},
    }


@pytest.fixture
def plan():
    return {
        "prepared_date": "2026-10-23",
        "estimated_october_foreign_buys": [{"symbol": "VGT", "quantity": "2"}],
    }


def test_excluded_totals_liabilities_and_source_rows_are_preserved(baseline):
    (accounts, positions, excluded), _ = baseline
    assert len(accounts) == 3 and len(positions) == 5 and len(excluded) == 1
    liability = next(p for p in positions if p["record_kind"] == "LIABILITY")
    assert liability["native_value"] == "-5"
    assert liability["source_ref"]["row"] == 6
    assert next(p for p in positions if p["symbol"] == "VGT")["quantity_provenance"] == (
        "inferred_from_market_value"
    )


def test_overlay_does_not_double_count_old_broker_cash_or_mutate_baseline(
    baseline, observation, plan
):
    (accounts, positions, _), _ = baseline
    original = copy.deepcopy(positions)
    current = bootstrap["apply_observations"](accounts, positions, observation, plan)
    assert positions == original
    hsbc = next(a["account_id"] for a in accounts if a["institution_label"] == "汇丰")
    old_cash = next(p for p in current if p["account_id"] == hsbc and p["name"] == "cash")
    assert old_cash["native_value"] is None
    assert "reconciliation_needed" in old_cash["currentness"]
    assert len([p for p in current if p["name"] == "USD现金"]) == 1
    vgt = next(p for p in current if p["symbol"] == "VGT")
    assert vgt["quantity"] == "5" and vgt["native_value"] is None
    ely = next(p for p in current if p["symbol"] == "QQQ")
    assert ely["quantity"] == "2" and ely["value_as_of"] == "2026-10-07"
    assert len(current) == len(positions) + 1


def test_preview_matches_existing_api_without_promoting_inferred_cost(baseline, observation, plan):
    (accounts, positions, _), _ = baseline
    current = bootstrap["apply_observations"](accounts, positions, observation, plan)
    result = bootstrap["api_preview"](
        {"recorded_on": "2026-10-23", "working_state": {"positions": current}}
    )
    request = HoldingsBatchRequest.model_validate(result["request"])
    assert result["preview_only"] is True
    assert len(request.items) == 2
    assert all(row.average_cost is None for row in request.items)
    assert all(row.metadata["price_as_of"] is None for row in request.items)
    assert all(row.metadata["screenshot_time_unknown"] for row in request.items)
    assert all(row.account_id is None for row in request.items)


def test_bundle_keeps_success_observation_separate_from_fills_and_future(
    tmp_path, monkeypatch, baseline, observation, plan
):
    _, sheets = baseline
    workbook = tmp_path / "source.xlsx"
    snapshot = tmp_path / "snapshot.json"
    month_plan = tmp_path / "plan.json"
    workbook.write_bytes(b"synthetic source workbook")
    snapshot.write_text(json.dumps(observation), encoding="utf-8")
    plan["dca_success_user_statement"] = "scheduled deductions succeeded"
    plan["elapsed_dca_computed_cny"] = {"total": "320"}
    month_plan.write_text(json.dumps(plan), encoding="utf-8")
    build = bootstrap["build_bundle"]
    monkeypatch.setitem(build.__globals__, "read_workbook", lambda _: sheets)
    result = build(workbook, snapshot, month_plan)
    assert result["executed_transactions"] == []
    assert result["working_state"]["total_current_net_assets_cny"] is None
    assert result["observations"][0]["future_deductions_are_completed"] is False
    assert result["observations"][0]["amounts_are_derived_not_imported_fills"] is True
    assert result["plan"]["activation_state"].startswith("research_proposal")
    assert result["historical_baseline"]["net_assets_cny"] == "185"
    assert len(result["sources"]) == 3


def test_unknown_inclusion_marker_fails_instead_of_silently_dropping_asset(baseline):
    _, sheets = baseline
    sheets["明细"][1]["cells"]["J"] = "unknown"
    with pytest.raises(ValueError, match="inclusion marker"):
        bootstrap["historical_positions"](sheets)


def test_ambiguous_quantity_match_fails(baseline):
    _, sheets = baseline
    sheets["明细"].append(source_row(8, "汇丰", "VGT", "40", symbol="VGT"))
    with pytest.raises(ValueError, match="Ambiguous quantity"):
        bootstrap["historical_positions"](sheets)


def workbook_fixture(path, *, uncached_formula=False):
    value = "<f>1+1</f>" if uncached_formula else "<f>1+1</f><v>2</v>"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(
            "xl/workbook.xml",
            """<workbook
        xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets><sheet name="sample" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        )
        archive.writestr(
            "xl/_rels/workbook.xml.rels",
            """<Relationships>
        <Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
        )
        archive.writestr(
            "xl/worksheets/sheet1.xml",
            f"""<worksheet
        xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        <row r="1"><c r="A1" t="inlineStr"><is><t>source</t></is></c>
        <c r="B1" t="inlineStr"/><c r="C1">{value}</c></row></sheetData></worksheet>""",
        )


def test_workbook_reader_preserves_source_formula_and_empty_inline_cell(tmp_path):
    path = tmp_path / "source.xlsx"
    workbook_fixture(path)
    row = bootstrap["read_workbook"](path)["sample"][0]
    assert row["cells"] == {"A": "source", "B": None, "C": "2"}
    assert row["formulas"] == {"C": "1+1"}


def test_uncached_formula_is_not_silently_read_as_zero(tmp_path):
    path = tmp_path / "source.xlsx"
    workbook_fixture(path, uncached_formula=True)
    with pytest.raises(ValueError, match="Uncached formula"):
        bootstrap["read_workbook"](path)


@pytest.fixture
def private_project(tmp_path):
    project = tmp_path / "project"
    project.mkdir()
    subprocess.run(["git", "init", "--quiet", str(project)], check=True)
    (project / ".gitignore").write_text("data/\n", encoding="utf-8")
    return project


def test_identical_repeat_does_not_append_or_rewrite_personal_state(private_project):
    output = private_project / "data/initial-state/sample"
    docs = {"baseline.json": {"executed_transactions": [], "value": "1.01"}}
    write = bootstrap["write_private_output"]
    assert write(output, docs, private_project)
    before = (output / "baseline.json").stat().st_mtime_ns
    assert not write(output, docs, private_project)
    assert (output / "baseline.json").stat().st_mtime_ns == before
    with pytest.raises(FileExistsError):
        write(output, {"baseline.json": {"value": "2"}}, private_project)
    assert json.loads((output / "baseline.json").read_text())["value"] == "1.01"


def test_public_or_nonignored_destination_is_rejected(private_project):
    write = bootstrap["write_private_output"]
    with pytest.raises(ValueError, match="data directory"):
        write(private_project / "public", {"baseline.json": {}}, private_project)
    (private_project / ".gitignore").write_text("", encoding="utf-8")
    with pytest.raises(ValueError, match="Git-ignored"):
        write(private_project / "data/private", {"baseline.json": {}}, private_project)


@pytest.mark.parametrize("value", ["NaN", "Infinity", "-Infinity"])
def test_nonfinite_amount_is_rejected(value):
    with pytest.raises(ValueError, match="finite"):
        bootstrap["decimal_text"](value)
