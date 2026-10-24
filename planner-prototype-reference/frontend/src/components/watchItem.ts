/* What a watchlist line says about the instrument it names.

   A line is a FUND from the catalogue, named by its ISIN, with perhaps a
   ticker from Yahoo's lookup beside it as a hint; or, since brief AG, a single
   SHARE, named by the symbol Yahoo lists it under and with no ISIN, because
   nothing this app can ask gives a share's. The ISIN alone tells them apart,
   and the sentences differ because the symbol means two things: a hint to
   check on a fund's line, the identity on a share's.

   Since brief AQ a line can also keep the amount its card was sized with: a
   sum now, a sum each month or both, in the currency the app computed them
   in, with what the chat assumed and what the figures were that day.

   No runtime imports but `money.ts`, itself pure, so `npm test` reaches it. */

import { money } from "./money.ts";

/** The two fields that say what a line names. */
export interface WatchedInstrument {
  isin?: string | null;
  symbol?: string | null;
}

/** The fields that say what a line's card was sized with, when it was. */
export interface SizedLine {
  amount_now?: number | null;
  amount_monthly?: number | null;
  amount_currency?: string | null;
}

/** The amount a line's card carried, in the reader's language ("€2,000.00
    now and €150.00 a month"), or null for a line whose card carried none. */
export function sizedAs(item: SizedLine): string | null {
  const currency = item.amount_currency;
  const parts: string[] = [];
  if (currency && item.amount_now != null) {
    parts.push(`${money(currency, "cents").format(item.amount_now)} now`);
  }
  if (currency && item.amount_monthly != null) {
    parts.push(`${money(currency, "cents").format(item.amount_monthly)} a month`);
  }
  return parts.length ? parts.join(" and ") : null;
}

/** Whether the line names a single share rather than a fund. */
export function isShare(item: WatchedInstrument): boolean {
  return !item.isin;
}

/** The line under the name: what identifies it, and the hint beside it. */
export function identityOf(item: WatchedInstrument): string {
  if (isShare(item)) return `${item.symbol ?? ""} · share, as Yahoo lists it`;
  return [item.isin, item.symbol].filter(Boolean).join(" · ");
}

const YOURS =
  " How many, at what price and where are yours too. Recording this leaves the watchlist line where it is: drop it with ✕ when the idea is done.";

/** What the ledger form says when "Record a buy" brings a line into it. */
export function prefillNote(item: WatchedInstrument): string {
  if (isShare(item)) {
    return (
      `${item.symbol}: the symbol Yahoo lists this share under, checked when the card was drawn and again when you accepted it. Check it is the listing you bought on.` +
      YOURS
    );
  }
  return (
    (item.symbol
      ? `${item.isin} · ${item.symbol}: the ISIN identifies the fund; the ticker came from the live lookup, so check it is the listing you actually bought on.`
      : `${item.isin}. That identifies the fund but does not price it: the ticker of the listing you bought on is still yours to give.`) +
    YOURS
  );
}
