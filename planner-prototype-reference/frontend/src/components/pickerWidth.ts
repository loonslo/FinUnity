/* How wide the instrument picker's list may be.

   The list hangs under its box, from the box's left edge, over the page. It
   was a fixed 34rem, 544px: at 390 the page then scrolled sideways to 641 on
   a situation and to 624 on the ledger (measured, brief AU). It is now as wide
   as the room from the box's left edge to the window's right edge, less the
   same EDGE a "?"'s panel keeps, and never wider than 34rem. Never narrower
   than the box either, so the list cannot be cut shorter than what it hangs
   from.

   Window coordinates, as `getBoundingClientRect` and `clientWidth` give them.
   Pure, so `tests/pickerWidth.test.ts` runs it under Node. */

import { EDGE } from "./explain.ts";

/** The widest the list is, in px: 34rem. */
export const LIST_MAX = 544;

/** The list's width for a box at `boxLeft`, `boxWidth` wide, in a window
    `viewportWidth` wide. */
export function pickerListWidth(boxLeft: number, boxWidth: number, viewportWidth: number): number {
  const room = viewportWidth - EDGE - boxLeft;
  return Math.min(LIST_MAX, Math.max(boxWidth, room));
}
