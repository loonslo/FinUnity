import assert from "node:assert/strict";
import { test } from "node:test";
import type { Event, Review } from "../src/planner/api.ts";
import { reviewHistory } from "../src/planner/reviewHistory.ts";

function snapshot(patch: Partial<Review["result"]> = {}, revision = 1): Review {
  return {
    id: `review-${revision}`,
    kind: "review",
    status: "confirmed",
    revision,
    created_at: "2026-10-19T00:00:00Z",
    result: {
      plan_id: "plan",
      month: "2026-10",
      base_currency: "CNY",
      as_of: "2026-10-19",
      partial_month: true,
      income: "0.00",
      expenses: "0.00",
      debt_payments: "0.00",
      recorded_cash_surplus: "0.00",
      coverage: "仅已记录",
      missing: [],
      event_ids: ["event"],
      goals: [
        {
          id: "goal",
          name: "目标",
          planned: "2000.00",
          actual: "1234.56",
          variance: "-765.44",
        },
      ],
      next: "复盘",
      ...patch,
    },
  };
}
const event = {
  id: "event",
  event: { kind: "transfer", goal_id: "goal", allocation_mode: "new_funding" },
} as Event;
const history = (r: Review[], e: Event[] = [event], month = "2026-10") =>
  reviewHistory(r, e, month, "CNY", "goal");

test("six month chart crosses year boundaries without inventing missing values", () => {
  const rows = history([], [], "2026-02");
  assert.deepEqual(
    rows.map((r) => r.month),
    ["2025-09", "2025-10", "2025-11", "2025-12", "2026-01", "2026-02"],
  );
  assert(rows.every((r) => r.actual === null && r.planned === null));
});
test("saved review amounts remain decimal strings with source date", () => {
  assert.deepEqual(history([snapshot()]).at(-1), {
    month: "2026-10",
    planned: "2000.00",
    actual: "1234.56",
    asOf: "2026-10-19",
  });
});
test("multiple plan versions select the latest snapshot instead of summing", () => {
  const latest = snapshot(
    {
      goals: [
        {
          id: "goal",
          name: "目标",
          planned: "3000.00",
          actual: "2000.00",
          variance: "-1000.00",
        },
      ],
    },
    8,
  );
  assert.equal(history([latest, snapshot()]).at(-1)?.actual, "2000.00");
  assert.equal(history([snapshot(), latest]).at(-1)?.planned, "3000.00");
});
test("no contribution, adjustments, and existing funding moves remain unrecorded", () => {
  for (const events of [
    [],
    [{ ...event, event: { ...event.event, kind: "adjustment" } } as Event],
    [
      {
        ...event,
        event: { ...event.event, allocation_mode: "move_existing" },
      } as Event,
    ],
  ]) {
    assert.equal(history([snapshot()], events).at(-1)?.actual, null);
  }
});
test("missing rates and different currencies never yield a comparable actual bar", () => {
  assert.equal(
    history([snapshot({ missing: ["缺少汇率"] })]).at(-1)?.actual,
    null,
  );
  assert.equal(
    history([snapshot({ base_currency: "USD" })]).at(-1)?.planned,
    null,
  );
});
test("saved snapshot is retained after a later void rather than pretending it is live", () => {
  assert.equal(
    history([snapshot()], [{ ...event, status: "voided" }]).at(-1)?.actual,
    "1234.56",
  );
});
