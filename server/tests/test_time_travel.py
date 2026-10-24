from datetime import UTC, datetime, timedelta

import pytest
from time_travel import shift_clock

from finunity_server import platform_service


def application_now() -> datetime:
    return datetime.fromisoformat(platform_service.utc_now().replace("Z", "+00:00"))


def test_shift_clock_moves_application_time_and_can_be_undone(request):
    if request.config.getoption("--time-offset-days"):
        pytest.skip("the whole session is already time-shifted; shifting twice is a no-op")
    baseline = application_now()
    undo = shift_clock(400)
    try:
        assert application_now() - baseline > timedelta(days=399)
        # tokens are signed with time.time(); it must move together with datetime.now()
        shifted_token_time = platform_service.time.time()
        assert shifted_token_time - baseline.timestamp() > 399 * 86400
    finally:
        undo()
    assert abs(application_now() - baseline) < timedelta(seconds=30)
    assert abs(datetime.now(UTC) - baseline) < timedelta(seconds=30)
