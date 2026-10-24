import type { ArrivedPosition, OmittedPosition } from "../api/dashboard";
import { locale, money } from "./ui";

/* Value a situation did not account for, and what it declared instead.

   Selling is an assertion with a date and proceeds; forgetting is the absence
   of one, and the total must not shrink in silence. Both the Dashboard (right
   under the net-worth figure it qualifies), the Portfolio (above the table the
   rows are missing from) and the account itself (where the situation that did
   the dropping is written) have to say so, which is why this used to be
   written twice, with two slightly different bodies for the same warning. */

// Names are said in full and wrap. They were cut to 34 characters with the
// rest one hover away, on a tooltip a phone never shows and a keyboard never
// reaches (brief AU); a name is what says which position this is.

// The long form, not the numeric one. A date read in a column can be terse;
// this one is read once, in prose, and "15 March 2026" cannot be mistaken for
// the American reading of the same digits the way 03/15/2026 can.
function fmtDate(iso: string): string {
  return new Date(iso + "T00:00:00").toLocaleDateString(locale, {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

// Units are not money: they can be fractional and they are not punctuated like
// a currency. Trailing zeroes are dropped so a whole number reads as one.
function fmtUnits(n: number): string {
  return n.toLocaleString(locale, { maximumFractionDigits: 4 });
}

/* How a single missing thing is named.

   A position that went unnamed is gone; a position whose units shrank is still
   in the table, and calling that one "gone" reads as a bug in the notice
   rather than as a question about the situation. They are different sentences
   because they are different facts. */
function label(o: OmittedPosition): string {
  return o.units_missing ? `${fmtUnits(o.units_missing)} units of ${o.asset_name}` : o.asset_name;
}

/* When the positions were last on record, and the arrivals first declared.

   Both copies used to print `items[0].last_seen` for the whole list, which is
   only right by accident: the backend sorts omissions by VALUE, not by date
   (analytics._unaccounted), and they come from different institutions with
   different photograph dates. So the date shown was the largest omission's,
   attached to every name in the line. One date is said once; dates that
   differ are said beside each name, where a tooltip used to keep them. */
function sharedDay(days: string[]): string | null {
  return days.every((d) => d === days[0]) ? days[0] : null;
}

export function OmissionsNotice({
  items,
  /** What the same situations declared that the app was not expecting. Usually
      the same money described differently — three rows becoming twenty-one is
      a portfolio written out in full, not a portfolio lost — and the reader is
      the only one who can tell which. Naming only the first half reads as a
      loss every time. */
  arrivals = [],
  /** Where to go to record the disposal. The Portfolio page is already there
      and would be telling the reader to walk to the room they are standing in;
      the Dashboard is one page away and has to say which one. */
  where,
  /** The currency the values are in: the base of the payload they came from. */
  baseCurrency,
}: {
  items: OmittedPosition[];
  arrivals?: ArrivedPosition[];
  where?: string;
  baseCurrency: string;
}) {
  if (items.length === 0) return null;
  const total = items.reduce((sum, o) => sum + o.last_value, 0);
  const arrived = arrivals.reduce((sum, a) => sum + a.value, 0);
  const seen = sharedDay(items.map((o) => o.last_seen));
  const declared = sharedDay(arrivals.map((a) => a.appeared_on));
  // Read at a glance, so the cause is one clause and the remedy one line. The
  // cause used to be a tooltip on the whole notice (brief AU).
  return (
    <div className="border-l-2 border-warn bg-warn-tint px-3 py-2 text-xs text-warn">
      <b>{money(baseCurrency).format(total)}</b> left the totals with nothing saying where it went: a
      newer situation no longer accounts for{" "}
      {items.map((o, i) => (
        <span key={`${o.institution ?? ""}-${o.symbol ?? o.asset_name}`}>
          {i > 0 && " · "}
          {label(o)}
          {seen === null && <span className="text-ink-faint"> (last seen {fmtDate(o.last_seen)})</span>}
        </span>
      ))}
      {seen !== null && <span className="text-ink-faint"> (last seen {fmtDate(seen)})</span>}
      {arrivals.length > 0 && (
        /* The second number, and the reason it is here: the same situation
           declared things the app had never heard of. If they are the same
           money, nothing was lost and the account was described better — and
           only the reader knows. Without this line that case is indistinguish-
           able from a disappearance, and it is the one that actually happened
           the first time this notice ran on real data. */
        <div className="mt-0.5">
          <b>{money(baseCurrency).format(arrived)}</b> was declared in the same{" "}
          {arrivals.length === 1 ? "situation" : "situations"} under{" "}
          {arrivals.length === 1 ? "a name" : "names"} the app did not know
          {": "}
          {arrivals.map((a, i) => (
            <span key={`${a.institution ?? ""}-${a.symbol ?? a.asset_name}`}>
              {i > 0 && " · "}
              {a.asset_name}
              {declared === null && (
                <span className="text-ink-faint"> (declared {fmtDate(a.appeared_on)})</span>
              )}
            </span>
          ))}
          {declared !== null && <span className="text-ink-faint"> (declared {fmtDate(declared)})</span>}
          . <span className="text-ink-soft">Same holdings, written out?</span>
        </div>
      )}
      <div className="mt-0.5 text-ink-soft">
        Sold → record a <b>close</b>
        {where ? ` ${where}` : ""}. Dropped by mistake → put it back.
      </div>
    </div>
  );
}
