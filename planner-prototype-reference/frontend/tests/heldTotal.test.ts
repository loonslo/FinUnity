/* What an account's page says the app records there (brief AV): the market
   where it priced a row, the value recorded where nothing did, never the
   cost. Every expected value is written here. */

import assert from "node:assert/strict";
import { test } from "node:test";
import { heldFigures } from "../src/components/heldTotal.ts";

test("a row nothing priced counts at the value recorded, not at its cost", () => {
  // The demo's shape: priced funds, and a bond fund with no ticker recorded
  // at 2,450 whose invented cost is 2,300.
  const rows = [
    { market_value: 20210, observed_value: 19796, book_value: 19796 },
    { market_value: null, observed_value: 2450, book_value: 2300 },
  ];
  assert.deepEqual(heldFigures(rows), { total: 22660, carried: 2450 });
});

test("with every row priced, nothing is carried", () => {
  assert.deepEqual(heldFigures([{ market_value: 130, observed_value: 100 }]), { total: 130, carried: 0 });
});

test("an account holding nothing holds nothing", () => {
  assert.deepEqual(heldFigures([]), { total: 0, carried: 0 });
});
