/* The day a situation's price is asked for.

   A situation describes one day, and the price on its form is that day's. The
   form's price button and "→ units" asked the latest close whatever the
   situation's date: on the situation of 2026-07-16 the button filled the close
   of 2026-10-14, and a save stored the units times it as that day's value
   (measured 2026-10-14, brief AU). The quote endpoint answers the close of a
   given day with `on`, as the ledger's "price on date" already asks it.

   A past situation asks its own day. Today's asks the latest close, which is
   today's, as it always did.

   No runtime imports, so `npm test` reaches it. */

/** The day to ask a situation's price for: its own date when it is a past
    one; null for today's, whose latest close is its own, and for a date after
    today, which has no close yet. Both are calendar dates, YYYY-MM-DD. */
export function priceDay(situationDate: string, today: string): string | null {
  return situationDate < today ? situationDate : null;
}
