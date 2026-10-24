"""Small process-local TTL/LRU cache; restart drops all cached results."""

import time
from collections import OrderedDict
from typing import Any


class TTLCache:
    def __init__(self, capacity: int, ttl_seconds: int) -> None:
        self.capacity = capacity
        self.ttl_seconds = ttl_seconds
        self._items: OrderedDict[str, tuple[float, Any]] = OrderedDict()

    def _prune(self) -> None:
        current = time.monotonic()
        for key, (expires, _) in list(self._items.items()):
            if expires <= current:
                del self._items[key]

    def get(self, key: str, default: Any = None) -> Any:
        self._prune()
        item = self._items.get(key)
        if item is None:
            return default
        self._items.move_to_end(key)
        return item[1]

    def __setitem__(self, key: str, value: Any) -> None:
        self._prune()
        self._items[key] = (time.monotonic() + self.ttl_seconds, value)
        self._items.move_to_end(key)
        while len(self._items) > self.capacity:
            self._items.popitem(last=False)

    def pop(self, key: str, default: Any = None) -> Any:
        self._prune()
        item = self._items.pop(key, None)
        return default if item is None else item[1]

    def remove_prefix(self, prefix: str) -> None:
        for key in list(self._items):
            if key.startswith(prefix):
                del self._items[key]
