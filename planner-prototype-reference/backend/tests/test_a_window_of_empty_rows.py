"""A window whose every row has no price is read as rows with no close.

Yahoo keeps a session's row with no price in it for a while, and sometimes for
good (the record's empty European closes: on 2026-10-14 every European listing
measured had an empty row for the day before). A window holding only such rows
came back, under yfinance 1.4.1, with its "Close" column made of None objects
rather than numbers, and `_fetch_close_window` raised TypeError on the first
of them (`float(None)`), where it reads an empty row as a row with no close:
measured 2026-10-14 (brief AT) with Yahoo's answer faked under the real
yfinance. yfinance 1.7.0 makes every price column a float column, so the same
window reads as rows with no close, and a day asked for in it waits for its
close as any day without one does (`prices._close_on`).

A window with a price in any of its rows was a float column under both.

Yahoo is faked under the real yfinance (`tests/yahoo_chart.py`): the column's
type is yfinance's own doing, so nothing above it can show this. Every symbol
here is invented.
"""

from __future__ import annotations

import datetime

import pytest

from app import prices
from tests.yahoo_chart import Listing, install

REAL_FETCH_CLOSE_WINDOW = prices._fetch_close_window
MONDAY, TUESDAY, WEDNESDAY = datetime.date(2026, 10, 12), datetime.date(2026, 10, 13), datetime.date(2026, 10, 14)


@pytest.fixture()
def yahoo_chart(monkeypatch, tmp_path):
    monkeypatch.setattr(prices, "_fetch_close_window", REAL_FETCH_CLOSE_WINDOW)
    fake = install(monkeypatch, tmp_path)
    fake.listings["XEMPTY.DE"] = Listing(closes={"2026-10-12": None, "2026-10-13": None})
    fake.listings["XSOME.DE"] = Listing(closes={"2026-10-12": None, "2026-10-13": 101.5})
    return fake


def test_a_window_of_rows_with_no_price_reads_as_rows_with_no_close(yahoo_chart):
    rows = prices._fetch_close_window("XEMPTY.DE", MONDAY, WEDNESDAY)

    assert rows == [("2026-10-12", None), ("2026-10-13", None)]


def test_a_day_in_such_a_window_waits_for_its_close(yahoo_chart):
    """No session after Tuesday has ended on Wednesday, so Tuesday waits."""
    with pytest.raises(prices.NoCloseYet):
        prices.get_price_on("XEMPTY.DE", TUESDAY, WEDNESDAY)


def test_a_window_with_a_price_keeps_its_empty_rows_as_rows_with_no_close(yahoo_chart):
    """The guard: a float column under both releases."""
    rows = prices._fetch_close_window("XSOME.DE", MONDAY, WEDNESDAY)

    assert rows == [("2026-10-12", None), ("2026-10-13", 101.5)]
