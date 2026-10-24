"""Move the application's notion of "now" forward so date-dependent tests fail early.

A test that stores a fixed 2026-09 transaction and reads it back through a "current month"
default passes for a month and then fails on the first morning of the next one. Running the whole
suite with the clock moved forward (``pytest --time-offset-days=400``) finds those tests before
the calendar does. Only ``datetime.now()`` and ``time.time()`` as seen by the application and the
test modules are shifted; pytest itself keeps the real clock.
"""

import datetime
import sys
import time as real_time
from collections.abc import Callable


def shift_clock(days: float) -> Callable[[], None]:
    """Shift the wall clock for application and test modules; return a function that undoes it."""
    offset = datetime.timedelta(days=days)

    class ShiftedDatetime(datetime.datetime):
        @classmethod
        def now(cls, tz=None):
            return super().now(tz) + offset

        @classmethod
        def today(cls):
            return cls.now()

    class ShiftedTime:
        """Delegates to the real ``time`` module except for the wall clock."""

        def __getattr__(self, name):
            return getattr(real_time, name)

        def time(self):
            return real_time.time() + offset.total_seconds()

        def time_ns(self):
            return real_time.time_ns() + int(offset.total_seconds() * 1_000_000_000)

    proxy = ShiftedTime()
    patched: list[tuple[object, str, object]] = []
    for name, module in list(sys.modules.items()):
        if not (name.startswith(("finunity_server", "test_")) or name == "conftest"):
            continue
        if getattr(module, "datetime", None) is datetime.datetime:
            patched.append((module, "datetime", datetime.datetime))
            module.datetime = ShiftedDatetime
        if getattr(module, "time", None) is real_time:
            patched.append((module, "time", real_time))
            module.time = proxy

    def undo() -> None:
        for module, attribute, original in reversed(patched):
            setattr(module, attribute, original)

    return undo
