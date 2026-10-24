/* A past situation's price is the close of its own day.

   On 2e3d97a the situation form asked the latest close whatever the
   situation's date: on the situation of 2026-07-16 the price button filled the
   close of 2026-10-14, and a save stores the units times it as that day's
   value (brief AU, measured in Edge). The day is now decided in priceDay.ts.

   Every expected value is written here. */

import assert from "node:assert/strict";
import { test } from "node:test";
import { priceDay } from "../src/components/priceDay.ts";

test("a past situation asks the close of its own day", () => {
  assert.equal(priceDay("2026-07-16", "2026-10-14"), "2026-07-16");
});

test("yesterday's situation asks yesterday's close, not today's", () => {
  assert.equal(priceDay("2026-10-13", "2026-10-14"), "2026-10-13");
});

test("a situation of last year asks its own day", () => {
  assert.equal(priceDay("2025-12-31", "2026-01-01"), "2025-12-31");
});

test("today's situation asks the latest close, as it always did", () => {
  assert.equal(priceDay("2026-10-14", "2026-10-14"), null);
});

test("a date after today asks for no day: it has no close yet", () => {
  assert.equal(priceDay("2026-10-15", "2026-10-14"), null);
});
