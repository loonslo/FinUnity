"""Build a private, source-preserving portfolio baseline without accessing a live ledger.

Only explicit input files are read. The output directory must be inside the server's
Git-ignored data directory. No account, transaction, notification or network is created.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import subprocess
import zipfile
from datetime import date
from decimal import Decimal
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_ID = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
BUCKETS = {"进取": "AGGRESSIVE", "稳健": "BALANCED", "防守": "DEFENSIVE"}


def decimal_text(value: Any) -> str:
    number = Decimal(str(value))
    if not number.is_finite():
        raise ValueError("Amounts must be finite")
    return format(number, "f")


def stable_id(*parts: str) -> str:
    return hashlib.sha256("\x1f".join(parts).encode()).hexdigest()[:24]


def read_workbook(path: Path) -> dict[str, list[dict[str, Any]]]:
    """Read cached source cells and preserve formulas; never calculate or edit Excel."""
    sheets: dict[str, list[dict[str, Any]]] = {}
    with zipfile.ZipFile(path) as archive:
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            shared = [
                "".join(node.itertext())
                for node in ElementTree.fromstring(archive.read("xl/sharedStrings.xml")).findall(
                    "s:si", NS
                )
            ]
        relationships = {
            item.attrib["Id"]: item.attrib["Target"]
            for item in ElementTree.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        }
        workbook = ElementTree.fromstring(archive.read("xl/workbook.xml"))
        for sheet in workbook.findall("s:sheets/s:sheet", NS):
            target = relationships[sheet.attrib[REL_ID]].lstrip("/")
            target = target if target.startswith("xl/") else "xl/" + target
            if ".." in Path(target).parts:
                raise ValueError("Unexpected worksheet path")
            rows = []
            for row in ElementTree.fromstring(archive.read(target)).findall(
                "s:sheetData/s:row", NS
            ):
                cells = {}
                formulas = {}
                for cell in row.findall("s:c", NS):
                    column = re.sub(r"\d", "", cell.attrib["r"])
                    node = cell.find("s:v", NS)
                    value = node.text if node is not None else None
                    if cell.attrib.get("t") == "s" and value is not None:
                        value = shared[int(value)]
                    elif cell.attrib.get("t") == "inlineStr":
                        inline = cell.find("s:is", NS)
                        value = "".join(inline.itertext()) if inline is not None else None
                    formula = cell.find("s:f", NS)
                    if formula is not None:
                        formulas[column] = formula.text
                        if value is None:
                            raise ValueError(
                                f"Uncached formula: {sheet.attrib['name']}!{cell.attrib['r']}"
                            )
                    cells[column] = value
                rows.append({"row": int(row.attrib["r"]), "cells": cells, "formulas": formulas})
            sheets[sheet.attrib["name"]] = rows
    return sheets


def historical_positions(sheets: dict[str, list[dict[str, Any]]]) -> tuple[list, list, list]:
    positions, excluded = [], []
    accounts = {}
    for row in sheets["明细"]:
        if row["row"] == 1:
            continue
        cells = row["cells"]
        if not cells.get("C"):
            continue
        if cells.get("J") not in {"是", "否"}:
            raise ValueError(f"Unknown inclusion marker at detail row {row['row']}")
        if cells["J"] == "否":
            excluded.append(row)
            continue
        account_id = stable_id(cells["A"], cells["B"])
        accounts[account_id] = {
            "account_id": account_id,
            "owner_label": cells["A"],
            "institution_label": cells["B"],
            "location": cells["P"],
            "cash_pool_id": "overseas-existing-capital" if cells["P"] == "境外" else None,
        }
        native_value = decimal_text(cells["G"])
        positions.append(
            {
                "position_id": stable_id(account_id, cells["C"], cells["F"]),
                "account_id": account_id,
                "name": cells["C"],
                "symbol": cells.get("N"),
                "asset_category": cells["D"],
                "risk_bucket": BUCKETS[cells["E"]],
                "record_kind": "LIABILITY" if Decimal(native_value) < 0 else "ASSET",
                "currency": cells["F"],
                "quantity": None,
                "quantity_provenance": "not_provided",
                "quantity_as_of": None,
                "native_value": native_value,
                "baseline_cny_value": decimal_text(cells["K"]),
                "value_as_of": "2026-10-07",
                "value_quality": "historical_user_ledger",
                "current_price": None,
                "average_cost": None,
                "source_ref": {"source_id": "workbook", "sheet": "明细", "row": row["row"]},
                "notes": cells.get("M"),
            }
        )
    for row in sheets.get("持仓数量", []):
        cells = row["cells"]
        if not cells.get("C") or cells.get("D") not in {"CNY", "USD", "HKD"}:
            continue
        matches = [
            p
            for p in positions
            if accounts[p["account_id"]]["institution_label"] == cells["B"]
            and (p["symbol"] == cells["A"] or p["name"].startswith(cells["A"]))
        ]
        if len(matches) != 1:
            raise ValueError(f"Ambiguous quantity source at row {row['row']}")
        position = matches[0]
        position["quantity"] = decimal_text(cells["C"])
        position["quantity_provenance"] = (
            "inferred_from_market_value" if "推断" in str(cells.get("K")) else "user_statement"
        )
        position["quantity_as_of"] = "2026-10-07"
        position["quantity_source_ref"] = {
            "source_id": "workbook",
            "sheet": "持仓数量",
            "row": row["row"],
        }
        position["current_price"] = decimal_text(cells["E"])
        position["price_source_note"] = cells.get("F")
    return list(accounts.values()), positions, excluded


def apply_observations(accounts: list, positions: list, snapshot: dict, plan: dict) -> list:
    """Overlay quantities/balances; never invent historical fills or transfer amounts."""
    current = copy.deepcopy(positions)
    by_institution = {a["institution_label"]: a["account_id"] for a in accounts}
    ibkr = by_institution["盈透"]
    hsbc = by_institution["汇丰"]
    ely = by_institution["盈立"]
    for position in current:
        position["currentness"] = "historical_baseline_not_currently_reconciled"
    current = [p for p in current if p["account_id"] != ibkr]
    quote_rows = next(
        value
        for value in snapshot.values()
        if isinstance(value, list)
        and value
        and isinstance(value[0], dict)
        and "last_price_usd" in value[0]
    )
    for row in quote_rows:
        symbol = row["symbol"]
        current.append(
            {
                "position_id": stable_id(ibkr, symbol, "USD"),
                "account_id": ibkr,
                "name": symbol,
                "symbol": symbol,
                "record_kind": "ASSET",
                "asset_category": "STOCK" if symbol in {"MSFT", "NVDA", "GOOGL"} else "ETF",
                "risk_bucket": "BALANCED" if symbol == "VOO" else "AGGRESSIVE",
                "currency": "USD",
                "quantity": decimal_text(row["quantity"]),
                "quantity_provenance": "user_screenshot",
                "quantity_confirmed_on": plan["prepared_date"],
                "quantity_as_of": None,
                "current_price": decimal_text(row["last_price_usd"]),
                "price_as_of": None,
                "native_value": decimal_text(
                    Decimal(row["quantity"]) * Decimal(row["last_price_usd"])
                ),
                "value_as_of": None,
                "value_quality": "last_visible_screenshot_capture_time_unknown",
                "average_cost": row.get("average_cost_inferred_usd"),
                "cost_provenance": "inferred_from_unrealized_pnl_not_confirmed_fill",
                "currentness": "latest_user_observation",
                "source_ref": {"source_id": "ibkr-screenshot-extraction", "symbol": symbol},
            }
        )
    current.append(
        {
            "position_id": stable_id(ibkr, "USD现金", "USD"),
            "account_id": ibkr,
            "name": "USD现金",
            "symbol": None,
            "record_kind": "ASSET",
            "asset_category": "CASH",
            "risk_bucket": "DEFENSIVE",
            "currency": "USD",
            "quantity": snapshot["cash_usd_approx"],
            "quantity_provenance": "rounded_user_screenshot",
            "quantity_as_of": None,
            "current_price": "1",
            "native_value": snapshot["cash_usd_approx"],
            "value_as_of": None,
            "value_quality": "approximate_4.48K_not_exact_settled_cash",
            "average_cost": None,
            "currentness": "latest_user_observation",
            "source_ref": {"source_id": "ibkr-screenshot-extraction", "field": "cash_usd_display"},
        }
    )
    for position in current:
        if (
            position["account_id"] == ely
            and position["symbol"] in snapshot["ely_positions_unchanged"]
        ):
            position["quantity"] = snapshot["ely_positions_unchanged"][position["symbol"]]
            position["quantity_confirmed_on"] = plan["prepared_date"]
            position["quantity_provenance"] = "user_confirms_no_trades_since_baseline"
            position["currentness"] = "quantity_confirmed_quote_historical"
        if position["account_id"] != hsbc:
            continue
        if position["name"] == "VGT":
            prior = next(
                trade
                for trade in plan["estimated_october_foreign_buys"]
                if trade["symbol"] == "VGT"
            )
            position["quantity"] = decimal_text(
                Decimal(position["quantity"]) + Decimal(prior["quantity"])
            )
            position["quantity_provenance"] = "inferred_baseline_quantity_plus_reported_purchase"
            position["native_value"] = None
            position["current_price"] = None
            position["value_quality"] = "requires_new_valuation_after_quantity_change"
        if position["asset_category"] == "现金" and position["record_kind"] == "ASSET":
            position["native_value"] = None
            position["current_price"] = None
            position["currentness"] = "cash_reconciliation_needed_after_internal_transfer"
            position["value_quality"] = "unknown_do_not_add_old_cash_to_new_ibkr_cash"
    return current


def build_bundle(workbook: Path, snapshot_path: Path, plan_path: Path) -> dict[str, Any]:
    sources = []
    for source_id, path in [
        ("workbook", workbook),
        ("ibkr-screenshot-extraction", snapshot_path),
        ("month-plan", plan_path),
    ]:
        sources.append(
            {
                "source_id": source_id,
                "filename": path.name,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
        )
    sheets = read_workbook(workbook)
    snapshot = json.loads(snapshot_path.read_text(encoding="utf-8-sig"))
    plan = json.loads(plan_path.read_text(encoding="utf-8-sig"))
    date.fromisoformat(plan["prepared_date"])
    accounts, baseline, excluded = historical_positions(sheets)
    current = apply_observations(accounts, baseline, snapshot, plan)
    if len({p["position_id"] for p in current}) != len(current):
        raise ValueError("Duplicate working positions")
    baseline_total = sum((Decimal(p["baseline_cny_value"]) for p in baseline), Decimal(0))
    return {
        "schema_version": 1,
        "recorded_on": plan["prepared_date"],
        "timezone": "Asia/Shanghai",
        "sources": sources,
        "accounts": accounts,
        "historical_baseline": {
            "as_of": "2026-10-07",
            "net_assets_cny": decimal_text(baseline_total),
            "positions": baseline,
            "excluded_rows": excluded,
            "summary_rows_are_not_additional_assets": True,
        },
        "working_state": {
            "as_of": "mixed_dates_not_a_current_complete_valuation",
            "positions": current,
            "total_current_net_assets_cny": None,
            "total_current_net_assets_unavailable_reason": (
                "Historical domestic values, unsettled fund unit details, rounded cash and "
                "post-transfer HSBC cash have not been reconciled to one valuation date."
            ),
            "cash_pools": [
                {
                    "pool_id": "overseas-existing-capital",
                    "new_principal_allowed": False,
                    "internal_transfers_are_income": False,
                    "sale_proceeds_are_new_principal": False,
                    "historical_net_assets_are_not_spendable_cash": True,
                }
            ],
        },
        "plan": {
            "activation_state": "research_proposal_not_activated_for_automatic_execution",
            "rules_source": "month-plan",
            "parameters": plan,
            "confirmed_constraints": {
                "overseas_new_principal_allowed": False,
                "internal_transfers_do_not_increase_principal": True,
            },
            "proposed_parameters_require_plan_versioning": True,
        },
        "observations": [
            {
                "observation_id": "october-dca-success-through-2026-10-23",
                "kind": "DCA_EXECUTION_STATUS",
                "source": "user_confirmation",
                "statement": plan.get("dca_success_user_statement"),
                "confirmed_through": plan["prepared_date"],
                "computed_amounts": plan.get("elapsed_dca_computed_cny"),
                "amounts_are_derived_not_imported_fills": True,
                "future_deductions_are_completed": False,
                "fund_units_price_fees_and_settlement": None,
            }
        ],
        "executed_transactions": [],
        "unreconciled_purchase_observations": plan["estimated_october_foreign_buys"],
        "reconstructing_fills_from_position_differences_is_forbidden": True,
        "deduction_observations_must_not_be_added_to_historical_nav_without_reconciliation": True,
        "raw_workbook": {"sheets": sheets, "values_are_cached_source_cells": True},
    }


def api_preview(bundle: dict) -> dict:
    """Preview only the latest screenshot; historical/unknown values stay in the bundle."""
    items = []
    for position in bundle["working_state"]["positions"]:
        if position["currentness"] != "latest_user_observation":
            continue
        items.append(
            {
                "holding_id": None,
                "account_id": None,
                "source_id": None,
                "snapshot_date": bundle["recorded_on"],
                "instrument_id": None,
                "name": position["name"],
                "asset_type": position["asset_category"],
                "quantity": position["quantity"],
                "average_cost": None,
                "currency": position["currency"],
                "metadata": {
                    "current_price": position["current_price"],
                    "risk_bucket": position["risk_bucket"],
                    "market_source": "user_screenshot",
                    "price_as_of": None,
                    "received_on": bundle["recorded_on"],
                    "screenshot_time_unknown": True,
                    "cash_is_approximate": position["asset_category"] == "CASH",
                    "source_position_id": position["position_id"],
                    "source_account_id": position["account_id"],
                    "symbol": position["symbol"],
                    "derived_cost_not_imported_as_confirmed": True,
                },
            }
        )
    return {
        "preview_only": True,
        "records_not_sent_to_any_account": True,
        "request": {"client_request_id": "initial-state-" + bundle["recorded_on"], "items": items},
    }


def write_private_output(output: Path, documents: dict[str, Any], project: Path) -> bool:
    """Return False for an identical repeat; reject changes to an existing batch."""
    output, project = output.resolve(), project.resolve()
    if not output.is_relative_to(project / "data"):
        raise ValueError("Personal data output must be inside this project's data directory")
    ignored = subprocess.run(
        ["git", "check-ignore", "--quiet", str(output / "baseline.json")],
        cwd=project,
        check=False,
    )
    if ignored.returncode != 0:
        raise ValueError("Personal data output must already be Git-ignored")
    encoded = {
        name: (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()
        for name, value in documents.items()
    }
    if output.exists():
        if all(
            (output / name).is_file() and (output / name).read_bytes() == value
            for name, value in encoded.items()
        ):
            return False
        raise FileExistsError("Existing initial-state batch differs; use a new version directory")
    output.mkdir(parents=True, exist_ok=False)
    for name, content in encoded.items():
        with (output / name).open("xb") as handle:
            handle.write(content)
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", required=True, type=Path)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    bundle = build_bundle(args.workbook, args.snapshot, args.plan)
    preview = api_preview(bundle)
    documents = {"baseline.json": bundle, "holdings-api-preview.json": preview}
    created = write_private_output(args.output, documents, Path(__file__).resolve().parents[1])
    print(
        json.dumps(
            {
                "created": created,
                "accounts": len(bundle["accounts"]),
                "baseline_positions": len(bundle["historical_baseline"]["positions"]),
                "working_positions": len(bundle["working_state"]["positions"]),
                "excluded_summary_rows": len(bundle["historical_baseline"]["excluded_rows"]),
                "latest_screenshot_api_preview_rows": len(preview["request"]["items"]),
                "live_account_writes": 0,
                "created_transactions": 0,
            }
        )
    )


if __name__ == "__main__":
    main()
