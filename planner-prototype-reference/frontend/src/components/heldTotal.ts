/* What an account's page says the app records there: the market value where
   a market priced a position, the value its situation was recorded at where
   none did, and how much of that total is such a carried figure.

   The same fallback the Dashboard's total uses (`Position.current_value`).
   It added `book_value` for a row nothing priced, which is the COST where one
   is recorded: measured in Edge on the demo (brief AV), a bond fund recorded
   at 2,450 with a cost of 2,300 made the page say "held €22,510" while the
   Dashboard counted 22,660.

   Pure, so tests/heldTotal.test.ts runs it under Node. */

import type { PortfolioRow } from "../api/portfolio";

type HeldRow = Pick<PortfolioRow, "market_value" | "observed_value">;

/** The account's total and the part of it carried from what was recorded. */
export function heldFigures(rows: HeldRow[]): { total: number; carried: number } {
  const carried = rows
    .filter((r) => r.market_value === null)
    .reduce((sum, r) => sum + r.observed_value, 0);
  return { total: rows.reduce((sum, r) => sum + (r.market_value ?? 0), 0) + carried, carried };
}
