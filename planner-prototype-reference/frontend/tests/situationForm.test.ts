/* The situation's one form: each group's fields, and the same request the
   five forms sent.

   `oldPayload` below is the request-building code of `BucketSection.submit`,
   Wealth.tsx at 2e3d97a, copied verbatim (only wrapped in a function of the
   form's state), so the one form is held to exactly what the five sent.
   Every other expected value is written here. */

import assert from "node:assert/strict";
import { test } from "node:test";
import type { HoldingCreate } from "../src/api/holdings";
import { BUCKETS, classOnSave, type Bucket } from "../src/components/buckets.ts";
import {
  costChanged,
  costCovers,
  holdingPayload,
  offers,
  shows,
  unitsBelowCost,
  type Draft,
} from "../src/components/situationForm.ts";

const group = (label: string): Bucket => BUCKETS.find((b) => b.label === label)!;
const STOCKS = group("Stocks & ETF");
const BONDS = group("Bonds");
const COMMODITIES = group("Commodities");
const CRYPTO = group("Crypto");
const OTHER = group("Other");

test("Stocks & ETF, Bonds and Other offer the ISIN and the dividend policy", () => {
  for (const g of [STOCKS, BONDS, OTHER]) {
    assert.equal(offers(g, "isin"), true, g.label);
    assert.equal(offers(g, "policy"), true, g.label);
  }
});

test("Commodities and Crypto offer neither to a new row", () => {
  for (const g of [COMMODITIES, CRYPTO]) {
    assert.equal(offers(g, "isin"), false, g.label);
    assert.equal(offers(g, "policy"), false, g.label);
    assert.equal(shows(g, "isin", ""), false, g.label);
    assert.equal(shows(g, "policy", ""), false, g.label);
  }
});

test("a value already there is shown in any group: a pick or a row being edited", () => {
  assert.equal(shows(CRYPTO, "isin", "ZZ0000000042"), true);
  assert.equal(shows(COMMODITIES, "policy", "acc"), true);
  assert.equal(shows(COMMODITIES, "isin", "   "), false);
});

function draft(over: Partial<Draft> = {}): Draft {
  return {
    name: "",
    symbol: "",
    isin: "",
    cost: "",
    policy: "",
    mode: "total",
    value: "",
    quantity: "",
    unitPrice: "",
    currency: "EUR",
    ...over,
  };
}

test("a new row in units and a price: its group's class, no value, the ISIN upper-cased", () => {
  const d = draft({
    name: "  Example Global Equity UCITS ETF (Acc) ",
    symbol: " EXGE.DE ",
    isin: " zz0000000018 ",
    cost: "480",
    policy: "acc",
    mode: "qty",
    quantity: "10",
    unitPrice: "50",
  });
  assert.deepEqual(holdingPayload(d, STOCKS, null), {
    asset_name: "Example Global Equity UCITS ETF (Acc)",
    asset_class: "equity",
    symbol: "EXGE.DE",
    isin: "ZZ0000000018",
    quantity: 10,
    unit_price: 50,
    value: null,
    cost_basis: 480,
    currency: "EUR",
    distribution_policy: "acc",
  });
});

test("a new row as a total: no units, no price, an empty ticker and cost are nothing", () => {
  const d = draft({ name: "Gold coins", value: "1500" });
  assert.deepEqual(holdingPayload(d, COMMODITIES, null), {
    asset_name: "Gold coins",
    asset_class: "commodity",
    symbol: null,
    isin: null,
    quantity: null,
    unit_price: null,
    value: 1500,
    cost_basis: null,
    currency: "EUR",
    distribution_policy: null,
  });
});

test("an edited row keeps its own class, an ETF its fund_etf", () => {
  const d = draft({ name: "A fund", value: "100" });
  assert.equal(holdingPayload(d, STOCKS, { asset_class: "fund_etf" }).asset_class, "fund_etf");
  assert.equal(holdingPayload(d, OTHER, { asset_class: null }).asset_class, null);
  assert.equal(holdingPayload(d, OTHER, { asset_class: "cash" }).asset_class, "cash");
});

test("a cost of 0 is a cost, as it was", () => {
  assert.equal(holdingPayload(draft({ name: "A gift", value: "10", cost: "0" }), OTHER, null).cost_basis, 0);
});

// --- 2e3d97a's code, verbatim, and the one form held to it ------------------

type Old = {
  bucket: Bucket;
  editId: number | null;
  editClass: string | null;
  name: string;
  symbol: string;
  isin: string;
  cost: string;
  mode: "total" | "qty";
  value: string;
  quantity: string;
  unitPrice: string;
  currency: string;
  distPolicy: string;
};

function oldPayload(s: Old): HoldingCreate {
  const { bucket, editId, editClass, name, symbol, isin, cost, mode, value, quantity, unitPrice, currency, distPolicy } = s;
  // A new row takes this bucket's class; an edited one keeps its own.
  const assetClass = classOnSave(bucket, editId != null ? { asset_class: editClass } : null);
  let payload: HoldingCreate;
  if (mode === "qty") {
    payload = {
      asset_name: name.trim(),
      asset_class: assetClass,
      symbol: symbol.trim() || null,
      isin: isin.trim().toUpperCase() || null,
      quantity: Number(quantity),
      unit_price: Number(unitPrice),
      value: null,
      cost_basis: cost ? Number(cost) : null,
      // The box's currency: the reader's, or the listing's (a USD stock's
      // qty x price is a USD amount), or the account's.
      currency,
      distribution_policy: distPolicy || null,
    };
  } else {
    payload = {
      asset_name: name.trim(),
      asset_class: assetClass,
      symbol: symbol.trim() || null,
      isin: isin.trim().toUpperCase() || null,
      quantity: null,
      unit_price: null,
      value: Number(value),
      cost_basis: cost ? Number(cost) : null,
      currency,
      distribution_policy: distPolicy || null,
    };
  }
  return payload;
}

test("every group, both ways of entering, new and edited: the same request, byte for byte", () => {
  const fills: Partial<Draft>[] = [
    { name: "SAP", symbol: "SAP.DE", mode: "qty", quantity: "6", unitPrice: "195" },
    { name: " Taipei fund ", value: "10000", currency: "TWD", cost: "9500.5" },
    { name: "Bitcoin", symbol: "btc-eur ", mode: "qty", quantity: "0.05", unitPrice: "58000", currency: "USD" },
    { name: "A bond fund", isin: "ie00b4wxjj64", value: "2450", policy: "dist", cost: "0" },
    { name: "Odd", symbol: "  ", isin: "  ", value: "", mode: "total", policy: "acc" },
    { name: "Half typed", mode: "qty", quantity: "", unitPrice: "12.5" },
  ];
  const editings: (null | { asset_class: string | null })[] = [null, { asset_class: "fund_etf" }, { asset_class: null }];
  let compared = 0;
  for (const g of BUCKETS) {
    for (const f of fills) {
      for (const editing of editings) {
        const d = draft(f);
        const old = oldPayload({
          bucket: g,
          editId: editing ? 7 : null,
          editClass: editing ? editing.asset_class : null,
          name: d.name,
          symbol: d.symbol,
          isin: d.isin,
          cost: d.cost,
          mode: d.mode,
          value: d.value,
          quantity: d.quantity,
          unitPrice: d.unitPrice,
          currency: d.currency,
          distPolicy: d.policy,
        });
        assert.equal(JSON.stringify(holdingPayload(d, g, editing)), JSON.stringify(old), `${g.label} ${JSON.stringify(f)}`);
        compared += 1;
      }
    }
  }
  assert.equal(compared, 90);
});

// --- Brief AV: a cost that paid for some of the units ---------------------------

const CARRIED = { asset_class: "fund_etf", cost_basis: 300, cost_quantity: 3 };
const carried = (over: Partial<Draft> = {}) =>
  draft({ name: "Fondo Alfa", symbol: "ALFA.MI", mode: "qty", quantity: "13", unitPrice: "110", cost: "300", ...over });

test("a cost left as loaded keeps the units it paid for, by not being sent", () => {
  const sent = holdingPayload(carried({ unitPrice: "112" }), STOCKS, CARRIED);
  assert.equal("cost_quantity" in sent, false);
  assert.equal(sent.cost_basis, 300);
  assert.equal("cost_quantity" in holdingPayload(carried({ cost: "300.00" }), STOCKS, CARRIED), false,
    "300.00 typed over 300 is the same cost");
});

test("a cost the reader types covers every unit, and an emptied box clears it", () => {
  assert.equal(holdingPayload(carried({ cost: "1250" }), STOCKS, CARRIED).cost_quantity, null);
  const cleared = holdingPayload(carried({ cost: "" }), STOCKS, CARRIED);
  assert.equal(cleared.cost_basis, null);
  assert.equal(cleared.cost_quantity, null);
});

test("a new row, or an edit loaded with no cost known, sends no count of units", () => {
  assert.equal("cost_quantity" in holdingPayload(carried(), STOCKS, null), false);
  assert.equal("cost_quantity" in holdingPayload(carried(), STOCKS, { asset_class: "fund_etf" }), false);
  assert.equal(costChanged("300", { asset_class: null }), false);
  assert.equal(costChanged("300", { cost_basis: null }), true);
});

test("under the cost: what it paid for, what the rest is, how to make it all a cost", () => {
  assert.equal(
    costCovers(carried(), CARRIED),
    "Paid for 3 of the 13 units: the other 10 are this situation's value, with no cost recorded. Type what all 13 cost to make it all a cost.",
  );
  assert.equal(costCovers(carried({ cost: "1250" }), CARRIED), null, "a typed cost covers them all");
  assert.equal(costCovers(carried(), { ...CARRIED, cost_quantity: null }), null);
  assert.equal(costCovers(carried({ quantity: "2" }), CARRIED), null, "said under the units instead");
});

test("units typed below what the cost paid for are refused under the field, in the backend's words", () => {
  assert.equal(
    unitsBelowCost(carried({ quantity: "2" }), CARRIED),
    "This cost paid for 3 units, more than the 2 typed here. Type what the units you hold cost, or keep at least 3.",
  );
  assert.equal(
    unitsBelowCost(carried({ mode: "total", value: "1430" }), CARRIED),
    "This cost paid for 3 units, and a row entered as a total has none. Type what the whole position cost, or keep it in units and a price.",
  );
  assert.equal(unitsBelowCost(carried({ quantity: "3" }), CARRIED), null, "3 of 3 is all of them");
  assert.equal(unitsBelowCost(carried({ quantity: "2", cost: "200" }), CARRIED), null,
    "a cost the reader typed covers the units they typed");
  assert.equal(unitsBelowCost(carried({ quantity: "" }), CARRIED), null, "the missing units are another refusal");
});

test("a cost the reader types is their own figure: no longer an estimate", () => {
  const typed = holdingPayload(carried({ cost: "1250" }), STOCKS, { ...CARRIED, cost_basis: 300 });
  assert.equal(typed.cost_estimated, false);
  const cleared = holdingPayload(carried({ cost: "" }), STOCKS, CARRIED);
  assert.equal(cleared.cost_estimated, null, "no cost, nothing estimated");
});

test("a cost left as loaded keeps its estimate, by not sending it", () => {
  assert.equal("cost_estimated" in holdingPayload(carried({ quantity: "14" }), STOCKS, CARRIED), false);
  assert.equal("cost_estimated" in holdingPayload(carried(), STOCKS, null), false, "a new row says nothing");
});
