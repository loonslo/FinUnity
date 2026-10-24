import assert from "node:assert/strict";
import { test } from "node:test";
import { diffCount, mergeHouseholdDraft } from "../src/planner/drafts.ts";
import { example, yesterday, day } from "../src/planner/sample.ts";
import { fraction, requestErrors } from "../src/planner/scenario.ts";

test("refresh replaces clean household data but preserves edited draft and its base", () => {
  const saved = example(),
    changed = { ...saved, name: "未保存的家庭名称" },
    updated = { ...saved, name: "另一页已保存名称" };
  const dirty = { base: saved, value: changed };
  assert.strictEqual(mergeHouseholdDraft(dirty, updated), dirty);
  assert.equal(diffCount(dirty.base, dirty.value), 1);
  assert.deepEqual(
    mergeHouseholdDraft({ base: saved, value: saved }, updated),
    { base: updated, value: updated },
  );
});
test("initial household draft adopts saved input", () => {
  const h = example();
  assert.deepEqual(mergeHouseholdDraft(null, h), { base: h, value: h });
});
test("ended-day default precedes today including month boundaries", () => {
  assert(yesterday() < day());
  assert.equal(example().as_of, yesterday());
});
test("percentage input converts to a decimal fraction without financial float conversion", () => {
  assert.equal(fraction("3"), "0.03");
  assert.equal(fraction("-90"), "−0.90".replace("−", "-"));
  assert.equal(fraction("0.25"), "0.0025");
});
test("commitment floor and percentage bounds are explained at their fields", () => {
  const h = example();
  assert(
    requestErrors({ monthly: { family: "1000" } }, h).family.includes("下限"),
  );
  assert(
    requestErrors(
      { scenario: { annual_return: "0.51" } },
      h,
    ).annual_return.includes("50%"),
  );
  assert.deepEqual(
    requestErrors(
      { scenario: { annual_return: "0.03" }, monthly: { family: "3000" } },
      h,
    ),
    {},
  );
});
