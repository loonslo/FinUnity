/* The instrument picker's list stays inside the window.

   On 2e3d97a the list was a fixed 544px: on a situation at 390 its box starts
   at x 97, so the list ended at 641 and the page scrolled sideways (measured,
   brief AU). Every expected value is written here. */

import assert from "node:assert/strict";
import { test } from "node:test";
import { LIST_MAX, pickerListWidth } from "../src/components/pickerWidth.ts";

test("at 390 the list ends 16px inside the window", () => {
  // The situation form's box: x 97, as wide as its card allows.
  assert.equal(pickerListWidth(97, 253, 390), 277);
  assert.equal(97 + pickerListWidth(97, 253, 390), 374);
  // The ledger's box, further left.
  assert.equal(pickerListWidth(80, 286, 390), 294);
});

test("with the room for it, the list is 34rem, as it was", () => {
  assert.equal(LIST_MAX, 544);
  assert.equal(pickerListWidth(400, 288, 1440), 544);
});

test("a box near the window's right edge gets the room that is left", () => {
  assert.equal(pickerListWidth(900, 288, 1440), 524);
});

test("the list is never narrower than its box", () => {
  assert.equal(pickerListWidth(300, 288, 390), 288);
});
