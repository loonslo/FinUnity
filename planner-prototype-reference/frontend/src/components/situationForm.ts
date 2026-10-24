/* The situation's one form: which fields each group shows, and the request a
   save sends.

   A situation page drew five forms, one per group (Stocks & ETF, Bonds,
   Commodities, Crypto, Other), the same ten controls five times: 35 fields on
   the page, and 1,103 characters of rules per form under the pointer (brief
   AU, measured). The only thing the five sent differently was the class of a
   new row. Now the group is chosen first, in one form, and the rules are said
   once, in words, under their fields.

   Read off the backend, the class decides the bucket a row is shown in, the
   allocation, the look-through's class by region grid and, for a coin, that
   its dividends are never asked about (`pac._follows_dividends`); nothing
   else reads it. So Commodities and Crypto show no ISIN and no dividend
   policy for a new row (the reader's choice, brief AU): the look-through
   cannot see inside a metal or a coin, and neither pays a dividend. A value
   already there, from a catalogue pick or the row being edited, is shown in
   any group: it is never hidden.

   A row's cost may pay for only some of its units (brief AV): a situation
   started from the last keeps what was paid for a row partly bought since the
   one before, and `cost_quantity` says for how many units. The form says so
   under the cost, keeps it while the cost is left as loaded, and drops it
   when the reader types a cost of their own, which covers every unit.

   No runtime imports beyond buckets.ts and money.ts, so `npm test` reaches
   it. */

import type { HoldingCreate } from "../api/holdings";
import { classOnSave, type Bucket } from "./buckets.ts";
import { locale } from "./money.ts";

/** The row being edited, as the form loaded it. `cost_basis` and
    `cost_quantity` are its cost and the units that cost paid for; left out,
    nothing is known of them and the cost is sent as it always was. */
export interface Editing {
  asset_class?: string | null;
  cost_basis?: number | null;
  cost_quantity?: number | null;
}

/** Whether the reader changed the cost from the one the row was loaded
    with: compared as numbers, so "766.00" typed over 766 is no change, and an
    emptied box is one. With nothing loaded, no change. */
export function costChanged(cost: string, editing: Editing | null): boolean {
  if (!editing || editing.cost_basis === undefined) return false;
  const typed = cost.trim() === "" ? null : Number(cost);
  return typed !== editing.cost_basis;
}

function units(n: number): string {
  return n.toLocaleString(locale, { maximumFractionDigits: 4 });
}

/** Under the cost of a row whose cost, as loaded, paid for some of its
    units: what it paid for, what the rest is, and how to make the rest a
    cost. Null when it covers them all, or the reader typed a cost. */
export function costCovers(d: Draft, editing: Editing | null): string | null {
  const covered = editing?.cost_quantity;
  if (covered == null || d.mode !== "qty" || costChanged(d.cost, editing)) return null;
  const held = Number(d.quantity);
  if (d.quantity.trim() === "" || !(held > covered)) return null;
  return (
    `Paid for ${units(covered)} of the ${units(held)} units: the other ${units(held - covered)} ` +
    `are this situation's value, with no cost recorded. Type what all ${units(held)} cost to make it all a cost.`
  );
}

/** The sentence under the units (or the value, for a total) when the cost
    left as loaded paid for more units than the row would hold: the backend
    refuses such a row with the same words. Null when there is nothing to
    refuse. */
export function unitsBelowCost(d: Draft, editing: Editing | null): string | null {
  const covered = editing?.cost_quantity;
  if (covered == null || d.cost.trim() === "" || costChanged(d.cost, editing)) return null;
  if (d.mode !== "qty") {
    return (
      `This cost paid for ${units(covered)} units, and a row entered as a total has none. ` +
      "Type what the whole position cost, or keep it in units and a price."
    );
  }
  const held = Number(d.quantity);
  if (d.quantity.trim() === "" || held >= covered) return null;
  return (
    `This cost paid for ${units(covered)} units, more than the ${units(held)} typed here. ` +
    `Type what the units you hold cost, or keep at least ${units(covered)}.`
  );
}

/** The fields some groups leave out. */
export type Optional = "isin" | "policy";

/** The groups, by the class a new row takes, that leave each field out. */
const LEFT_OUT: Record<Optional, readonly string[]> = {
  isin: ["commodity", "crypto"],
  policy: ["commodity", "crypto"],
};

/** Whether a group offers the field for a new row. */
export function offers(group: Bucket, field: Optional): boolean {
  return !LEFT_OUT[field].includes(group.cls);
}

/** Whether the field is shown: the group offers it, or it already holds a
    value (a pick, a row being edited), which is never hidden. */
export function shows(group: Bucket, field: Optional, value: string): boolean {
  return offers(group, field) || value.trim() !== "";
}

/** What the form holds when it is saved, as its boxes hold it. `currency` is
    the one the form shows: the reader's, or its proposal. */
export interface Draft {
  name: string;
  symbol: string;
  isin: string;
  cost: string;
  policy: string;
  mode: "total" | "qty";
  value: string;
  quantity: string;
  unitPrice: string;
  currency: string;
}

/** The request a save sends, field by field what each of the five forms sent:
    a new row takes its group's class, an edited one keeps its own (an ETF
    stays `fund_etf`). Entered as a total, no units and no price; as units
    and a price, no value, which the backend works out. A cost the reader
    changed covers every unit (`cost_quantity` null) and is their own figure,
    no longer an estimate (`cost_estimated` false, or null with no cost);
    one left as loaded keeps both, by not sending them (brief AV). The form
    has no box for either, so an edit kept a plan-built cost "estimated"
    after the reader typed the contract note's figure over it. */
export function holdingPayload(d: Draft, group: Bucket, editing: Editing | null): HoldingCreate {
  const shared = {
    asset_name: d.name.trim(),
    asset_class: classOnSave(group, editing),
    symbol: d.symbol.trim() || null,
    isin: d.isin.trim().toUpperCase() || null,
  };
  const rest = {
    cost_basis: d.cost ? Number(d.cost) : null,
    ...(costChanged(d.cost, editing)
      ? { cost_estimated: d.cost ? false : null, cost_quantity: null }
      : {}),
    currency: d.currency,
    distribution_policy: d.policy || null,
  };
  return d.mode === "qty"
    ? { ...shared, quantity: Number(d.quantity), unit_price: Number(d.unitPrice), value: null, ...rest }
    : { ...shared, quantity: null, unit_price: null, value: Number(d.value), ...rest };
}
