import os

import pytest
from time_travel import shift_clock


def pytest_addoption(parser):
    parser.addoption(
        "--time-offset-days",
        type=float,
        default=float(os.environ.get("FINUNITY_TEST_TIME_OFFSET_DAYS", "0")),
        help="Run the suite with the application's clock moved forward by this many days.",
    )


@pytest.fixture(scope="session", autouse=True)
def _time_travel(request):
    days = request.config.getoption("--time-offset-days")
    undo = shift_clock(days) if days else None
    yield
    if undo:
        undo()
