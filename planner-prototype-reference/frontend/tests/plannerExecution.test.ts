import assert from "node:assert/strict";
import { test } from "node:test";
import {
  atoms,
  changeKind,
  draftErrors,
  effects,
  emptyOccurrence,
  forAction,
  isEmptyDraft,
} from "../src/planner/execution.ts";
import { example, day } from "../src/planner/sample.ts";
import type { Action } from "../src/planner/api.ts";

function action(): Action {
  return {
    id: "action",
    kind: "action",
    status: "pending",
    revision: 1,
    created_at: "",
    plan_id: "plan",
    goal_id: "family",
    month: day().slice(0, 7),
    name: "长期储蓄",
    amount: "3000",
    requires_event: true,
    event_id: null,
    voided_event_id: null,
    completed_at: null,
    note: "",
    base_currency: "CNY",
    actual_amount: "1000",
    remaining_amount: "2000",
    excess_amount: "0",
    event_ids: [],
    progress_missing: [],
    can_record: true,
  };
}

test("a plan pre-fills its goal, remaining contribution and destination", () => {
  const entry = forAction(example(), action());
  assert.equal(entry.action_id, "action");
  assert.equal(entry.goal_id, "family");
  assert.equal(entry.amount, "2000");
  assert.equal(entry.kind, "transfer");
  assert.equal(entry.target_asset_id, "investment");
  assert.deepEqual(draftErrors(example(), entry, [action()]), []);
});

test("empty actual amounts get a useful validation message", () => {
  const errors = draftErrors(example(), emptyOccurrence(example()), []);
  assert(errors.some((error) => error.includes("请填写金额")));
});

test("direction uses record kind and rejects negative amounts", () => {
  const entry = { ...emptyOccurrence(example()), amount: "-20" };
  assert(draftErrors(example(), entry, []).some((e) => e.includes("正数")));
  assert(
    effects(example(), { ...entry, amount: "20" })[0].includes("＋ ¥20.00"),
  );
  assert(
    effects(example(), { ...entry, kind: "expense", amount: "20" })[0].includes(
      "− ¥20.00",
    ),
  );
});

test("transfers show the decrease and the increase separately", () => {
  const lines = effects(example(), forAction(example(), action()));
  assert(lines[0].includes("日常与储蓄账户 − ¥2,000.00"));
  assert(lines[1].includes("目标投资账户 ＋ ¥2,000.00"));
});

test("changing type clears incompatible goal and account links", () => {
  const changed = changeKind(forAction(example(), action()), "income");
  assert.equal(changed.action_id, null);
  assert.equal(changed.goal_id, null);
  assert.equal(changed.target_asset_id, null);
});

test("unknown forecast amounts can remain unknown", () => {
  const entry = {
    ...emptyOccurrence(example()),
    state: "forecast",
    quality: "unknown",
  } as const;
  assert.deepEqual(draftErrors(example(), entry, []), []);
});

test("each new draft has its own submission identity", () => {
  assert.notEqual(
    emptyOccurrence(example()).client_key,
    emptyOccurrence(example()).client_key,
  );
});

test("choosing a plan only replaces an untouched draft", () => {
  const h = example();
  const empty = emptyOccurrence(h);
  assert.equal(isEmptyDraft(h, empty), true);
  assert.equal(isEmptyDraft(h, { ...empty, note: "稍后核对工资" }), false);
  assert.equal(isEmptyDraft(h, { ...empty, state: "forecast" }), false);
  assert.equal(isEmptyDraft(h, { ...empty, amount: "100" }), false);
  assert.equal(isEmptyDraft(h, { ...empty, kind: "expense" }), false);
});

test("exact decimal comparison does not use floating point", () => {
  assert.equal(atoms("0.10"), atoms("0.1"));
  assert.equal(atoms("0.00000001"), 1n);
  assert.equal(atoms("NaN"), null);
});
