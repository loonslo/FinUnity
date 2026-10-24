import type { Holding, LedgerSummary, RiskBucket, Source, Transaction } from "../types.ts";
import { percentageOfMinor, productToMinor, roundHalfEven, toMinor } from "./money.ts";

/**
 * A stored holding row as the demo keeps it. It mirrors the server's raw row
 * (decimal quantity, optional average cost, price in metadata) rather than the
 * public view, so the valuation below can follow `public_holding` step by step.
 */
export type DemoHolding = {
  holding_id: string;
  source_id?: string;
  snapshot_date?: string;
  instrument_id?: string;
  name: string;
  asset_type: string;
  quantity: number;
  average_cost: number | null;
  currency: string;
  current_price?: number | null;
  risk_bucket: RiskBucket;
  quantity_kind: "UNITS" | "VALUE";
  quantity_unit?: string;
  cost_value?: number | null;
  market_source: string;
  price_as_of?: string;
  version: number;
  updated_at: string;
  /** Built-in rows carry both languages; a row the visitor edits drops this. */
  names?: Translated;
};

export type Translated = { zh: string; en: string };

export type DemoSource = Source & { names?: Translated };

export type DemoTransaction = Transaction & { titles?: Translated; categories?: Translated };

export type LedgerInput = {
  baseCurrency: string;
  goals: LedgerSummary["goals"];
  holdings: Holding[];
  transactions: Transaction[];
  sources: Source[];
};

export const BUCKETS: RiskBucket[] = ["AGGRESSIVE", "BALANCED", "DEFENSIVE"];

const INCOME_TYPES = new Set(["deposit", "dividend", "interest"]);
const EXPENSE_TYPES = new Set(["withdrawal", "fee"]);

/** `public_holding` in the server: value, cost and profit in minor units. */
export function publicHolding(row: DemoHolding): Holding {
  const isCash = row.asset_type === "cash";
  const cost: number = row.average_cost ?? (isCash ? 1 : 0);
  const price = isCash ? row.current_price || 1 : (row.current_price ?? null);
  let valueMinor: number;
  let costMinor: number;
  let profit: number | null;
  if (row.quantity_kind === "VALUE") {
    valueMinor = toMinor(row.quantity);
    costMinor = row.cost_value != null ? toMinor(row.cost_value) : 0;
    profit = row.cost_value != null ? valueMinor - costMinor : null;
  } else {
    valueMinor = productToMinor(row.quantity, price ?? cost);
    costMinor = productToMinor(row.quantity, cost);
    profit = row.average_cost != null ? valueMinor - costMinor : null;
  }
  return {
    market_value_minor: valueMinor,
    cost_minor: costMinor,
    unrealized_profit_minor: profit,
    valuation_status: price != null ? "priced" : "estimated",
    holding_id: row.holding_id,
    source_id: row.source_id,
    snapshot_date: row.snapshot_date,
    instrument_id: row.instrument_id,
    name: row.name,
    asset_type: row.asset_type,
    quantity: row.quantity,
    quantity_unit: row.quantity_unit,
    quantity_kind: row.quantity_kind,
    average_cost: row.average_cost ?? undefined,
    current_price: price ?? undefined,
    currency: row.currency,
    risk_bucket: row.risk_bucket,
    market_source: row.market_source,
    price_as_of: row.price_as_of,
    version: row.version,
    updated_at: row.updated_at,
  };
}

type Totals = LedgerSummary["base"];
const emptyTotals = (): Totals => ({
  cash_balance_minor: 0,
  holdings_value_minor: 0,
  holdings_cost_minor: 0,
  unrealized_profit_minor: 0,
  total_assets_minor: 0,
  income_minor: 0,
  expense_minor: 0,
  net_cashflow_minor: 0,
});

/**
 * `ledger_summary` in the server, applied to in-memory rows. Like the server it
 * never converts currencies: the base-currency totals only count base-currency
 * records and every other currency is reported as excluded.
 */
export function buildLedgerSummary(
  input: LedgerInput,
  month: string,
  now: Date,
): LedgerSummary {
  const totals: Record<string, Totals> = {};
  const totalsFor = (currency: string) => (totals[currency] ??= emptyTotals());
  const { baseCurrency, goals } = input;

  for (const holding of input.holdings) {
    const aggregate = totalsFor(holding.currency);
    if (holding.asset_type === "cash") {
      aggregate.cash_balance_minor += holding.market_value_minor;
    } else {
      aggregate.holdings_value_minor += holding.market_value_minor;
      aggregate.holdings_cost_minor += holding.cost_minor;
      aggregate.unrealized_profit_minor += holding.unrealized_profit_minor ?? 0;
    }
    aggregate.total_assets_minor += holding.market_value_minor;
  }

  for (const item of input.transactions) {
    if (!item.transaction_date.startsWith(month)) continue;
    const aggregate = totalsFor(item.currency);
    const amount = Math.abs(item.amount_minor);
    if (INCOME_TYPES.has(item.transaction_type)) aggregate.income_minor += amount;
    else if (EXPENSE_TYPES.has(item.transaction_type)) aggregate.expense_minor += amount;
  }
  for (const aggregate of Object.values(totals)) {
    aggregate.net_cashflow_minor = aggregate.income_minor - aggregate.expense_minor;
  }

  const base = totalsFor(baseCurrency);
  const valid = base.total_assets_minor > 0;
  const items = BUCKETS.map((key) => {
    let value = input.holdings
      .filter(
        (holding) =>
          holding.asset_type !== "cash" &&
          holding.currency === baseCurrency &&
          holding.risk_bucket === key,
      )
      .reduce((sum, holding) => sum + holding.market_value_minor, 0);
    if (key === "DEFENSIVE") value += base.cash_balance_minor;
    const share = valid ? (value / base.total_assets_minor) * 100 : null;
    const target = goals[`${key.toLowerCase()}_pct` as "aggressive_pct" | "balanced_pct" | "defensive_pct"];
    return {
      key,
      value,
      share_pct: share,
      target_pct: target,
      gap_minor: valid ? percentageOfMinor(base.total_assets_minor, target) - value : null,
      deviation_pct: share !== null ? share - target : null,
    };
  });
  const drift = valid ? items.reduce((sum, item) => sum + Math.abs(item.deviation_pct!), 0) : null;

  const categories = new Map<string, number>();
  for (const item of input.transactions) {
    if (
      item.currency === baseCurrency &&
      item.transaction_date.startsWith(month) &&
      (INCOME_TYPES.has(item.transaction_type) || EXPENSE_TYPES.has(item.transaction_type))
    ) {
      const name = item.category || "Other";
      categories.set(name, (categories.get(name) ?? 0) + Math.abs(item.amount_minor));
    }
  }

  return {
    generated_at: now.toISOString(),
    records: {
      accounts: [],
      holdings: input.holdings,
      transactions: input.transactions,
      sources: input.sources,
    },
    goals,
    allocation: {
      items,
      valid,
      drift_pct: drift,
      score: drift !== null ? Math.max(0, roundHalfEven(100 - drift / 2)) : null,
      largest_gap_pct: valid ? Math.max(...items.map((item) => Math.abs(item.deviation_pct!))) : null,
    },
    categories: [...categories.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6),
    month,
    base_currency: baseCurrency,
    base,
    by_currency: totals,
    accounts: [],
    excluded_currencies: Object.keys(totals).filter((currency) => currency !== baseCurrency).sort(),
    price_refresh: { status: "unconfigured", updated_count: 0 },
  };
}
