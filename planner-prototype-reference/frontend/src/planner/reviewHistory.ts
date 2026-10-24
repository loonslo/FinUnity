import type { Event, Review } from "./api";

/** Select one saved snapshot per month. Never sum different plan versions. */
export function reviewHistory(
  reviews: Review[],
  events: Event[],
  month: string,
  currency: string,
  goalId: string,
) {
  return Array.from({ length: 6 }, (_, i) => {
    const date = new Date(`${month}-01T12:00:00Z`);
    date.setUTCMonth(date.getUTCMonth() - 5 + i);
    const key = date.toISOString().slice(0, 7);
    const review = reviews
      .filter(
        (r) => r.result.month === key && r.result.base_currency === currency,
      )
      .toSorted(
        (a, b) =>
          b.revision - a.revision || b.created_at.localeCompare(a.created_at),
      )[0];
    const goal = review?.result.goals.find((g) => g.id === goalId);
    const recorded = review?.result.event_ids.some((id) =>
      events.some(
        (e) =>
          e.id === id &&
          e.event.kind === "transfer" &&
          e.event.goal_id === goalId &&
          e.event.allocation_mode === "new_funding",
      ),
    );
    return {
      month: key,
      planned: goal?.planned ?? null,
      actual:
        goal && recorded && !review?.result.missing.length ? goal.actual : null,
      asOf: review?.result.as_of ?? null,
    };
  });
}
