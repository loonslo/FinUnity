import type { HoldingImport, LedgerSummary, RiskBucket, Transaction } from "../types.ts";
import type { DemoHolding, DemoSource, DemoTransaction, Translated } from "./ledger.ts";

/**
 * Everything the demo keeps in memory. Nothing here is persisted or sent
 * anywhere: reloading the page, or pressing "reset", starts from the seed again.
 */
export type DemoState = {
  visitorHoldingIds: Set<string>;
  visitorTransactionIds: Set<string>;
  sampleDocumentIds: Set<string>;
  baseCurrency: string;
  preferencesVersion: number;
  goals: LedgerSummary["goals"];
  sources: DemoSource[];
  holdings: DemoHolding[];
  transactions: DemoTransaction[];
  imports: Map<string, HoldingImport>;
  committed: Map<string, { batch_id: string; status: string; version: number; items: DemoHolding[] }>;
  sequence: number;
};

const bi = (zh: string, en: string): Translated => ({ zh, en });

const pad = (value: number) => String(value).padStart(2, "0");
export const dateKey = (year: number, month: number, day: number) => `${year}-${pad(month)}-${pad(day)}`;

type SeedHolding = {
  name: Translated;
  code?: string;
  type: string;
  bucket: RiskBucket;
  source: string;
  currency: string;
  quantity: number;
  cost: number | null;
  price: number | null;
};

// 三桶目标设成 40/40/20，而下面的持仓实际约 19/38/43：偏离提示和月度复盘因此有内容可看。
// 美元、港币持仓不会折算进人民币总额，这与真实产品一致（它们只出现在“未折算”提示里）。
const HOLDINGS: SeedHolding[] = [
  { name: bi("示例活期存款", "Example savings"), type: "cash", bucket: "DEFENSIVE", source: "demo-s1", currency: "CNY", quantity: 40000, cost: 1, price: null },
  { name: bi("示例货币基金", "Example money-market fund"), code: "DEMO-MM", type: "fund", bucket: "DEFENSIVE", source: "demo-s2", currency: "CNY", quantity: 36000, cost: 1, price: 1 },
  { name: bi("示例债券基金", "Example bond fund"), code: "DEMO-BOND", type: "fund", bucket: "DEFENSIVE", source: "demo-s2", currency: "CNY", quantity: 20000, cost: 1.15, price: 1.2 },
  { name: bi("示例宽基指数基金", "Example broad-index fund"), code: "DEMO-IDX", type: "etf", bucket: "BALANCED", source: "demo-s3", currency: "CNY", quantity: 30000, cost: 1.05, price: 1.1 },
  { name: bi("示例红利基金", "Example dividend fund"), code: "DEMO-DIV", type: "fund", bucket: "BALANCED", source: "demo-s2", currency: "CNY", quantity: 20000, cost: 1.28, price: 1.25 },
  { name: bi("示例黄金ETF", "Example gold ETF"), code: "DEMO-GLD", type: "etf", bucket: "BALANCED", source: "demo-s3", currency: "CNY", quantity: 6000, cost: 5, price: 5.2 },
  { name: bi("示例成长股 A", "Example growth stock A"), code: "DEMO-GRW", type: "stock", bucket: "AGGRESSIVE", source: "demo-s3", currency: "CNY", quantity: 400, cost: 52, price: 61.5 },
  { name: bi("示例科技主题基金", "Example tech-theme fund"), code: "DEMO-TECH", type: "fund", bucket: "AGGRESSIVE", source: "demo-s2", currency: "CNY", quantity: 15000, cost: 1.4, price: 1.32 },
  { name: bi("示例美元现金", "Example USD cash"), type: "cash", bucket: "DEFENSIVE", source: "demo-s4", currency: "USD", quantity: 3000, cost: 1, price: null },
  { name: bi("示例海外指数基金", "Example global index ETF"), code: "DEMO-GLOBAL", type: "etf", bucket: "BALANCED", source: "demo-s4", currency: "USD", quantity: 40, cost: 410, price: 430 },
  { name: bi("示例港股 B", "Example HK stock B"), code: "DEMO-HK", type: "stock", bucket: "AGGRESSIVE", source: "demo-s4", currency: "HKD", quantity: 600, cost: 38, price: 41 },
];

const SOURCES: Array<{ id: string; name: Translated }> = [
  { id: "demo-s1", name: bi("示例银行", "Example bank") },
  { id: "demo-s2", name: bi("示例基金平台", "Example fund platform") },
  { id: "demo-s3", name: bi("示例券商", "Example broker") },
  { id: "demo-s4", name: bi("示例境外券商", "Example overseas broker") },
];

type FlowTemplate = {
  day: number;
  type: Transaction["transaction_type"];
  /** Minor units; null skips the record for that month. `back` is how many months ago. */
  amount: (back: number) => number | null;
  title: Translated;
  category: Translated;
  source: string;
};

const FLOWS: FlowTemplate[] = [
  { day: 1, type: "deposit", amount: () => 1_800_000, title: bi("工资", "Salary"), category: bi("工资", "Salary"), source: "demo-s1" },
  { day: 3, type: "withdrawal", amount: () => 650_000, title: bi("房租", "Rent"), category: bi("居住", "Housing"), source: "demo-s1" },
  { day: 6, type: "withdrawal", amount: (back) => 185_000 + ((back * 37) % 5) * 9_000, title: bi("超市与餐饮", "Groceries and dining"), category: bi("餐饮", "Dining"), source: "demo-s1" },
  { day: 8, type: "withdrawal", amount: (back) => 52_000 + (back % 3) * 6_000, title: bi("地铁与打车", "Transit and rides"), category: bi("交通", "Transport"), source: "demo-s1" },
  { day: 9, type: "withdrawal", amount: () => 38_000, title: bi("水电燃气", "Utilities"), category: bi("居住", "Housing"), source: "demo-s1" },
  { day: 10, type: "buy", amount: () => 300_000, title: bi("定投 示例宽基指数基金", "Monthly buy: example broad-index fund"), category: bi("投资", "Investing"), source: "demo-s3" },
  { day: 10, type: "fee", amount: () => 1_200, title: bi("手续费", "Trading fee"), category: bi("手续费", "Fees"), source: "demo-s3" },
  { day: 12, type: "interest", amount: () => 4_500, title: bi("活期利息", "Savings interest"), category: bi("利息", "Interest"), source: "demo-s1" },
  { day: 12, type: "dividend", amount: (back) => (back % 2 === 0 ? 26_000 : null), title: bi("基金分红", "Fund dividend"), category: bi("投资收益", "Investment income"), source: "demo-s2" },
  { day: 13, type: "withdrawal", amount: (back) => 90_000 + ((back * 53) % 4) * 15_000, title: bi("网购", "Online shopping"), category: bi("购物", "Shopping"), source: "demo-s1" },
];

/** Six months of flows ending in the current month, never dated after `now`. */
function seedTransactions(now: Date, stamp: string): DemoTransaction[] {
  const rows: DemoTransaction[] = [];
  for (let back = 0; back < 6; back += 1) {
    const first = new Date(now.getFullYear(), now.getMonth() - back, 1);
    for (const template of FLOWS) {
      if (back === 0 && template.day > now.getDate()) continue;
      const amount = template.amount(back);
      if (amount === null) continue;
      rows.push({
        transaction_id: `demo-t${rows.length + 1}`,
        source_id: template.source,
        transaction_date: dateKey(first.getFullYear(), first.getMonth() + 1, template.day),
        transaction_type: template.type,
        category: template.category.zh,
        title: template.title.zh,
        amount_minor: amount,
        currency: "CNY",
        version: 1,
        updated_at: stamp,
        titles: template.title,
        categories: template.category,
      });
    }
  }
  return rows;
}

export function createSeedState(now: Date = new Date()): DemoState {
  const stamp = now.toISOString();
  const priceAsOf = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1, 7)).toISOString();
  const snapshot = dateKey(now.getFullYear(), now.getMonth() + 1, now.getDate());
  return {
    visitorHoldingIds: new Set(),
    visitorTransactionIds: new Set(),
    sampleDocumentIds: new Set(),
    baseCurrency: "CNY",
    preferencesVersion: 0,
    goals: { aggressive_pct: 40, balanced_pct: 40, defensive_pct: 20, version: 1 },
    sources: SOURCES.map((source) => ({
      source_id: source.id,
      name: source.name.zh,
      version: 1,
      updated_at: stamp,
      names: source.name,
    })),
    holdings: HOLDINGS.map((seed, index) => ({
      holding_id: `demo-h${index + 1}`,
      source_id: seed.source,
      snapshot_date: snapshot,
      instrument_id: seed.code,
      name: seed.name.zh,
      asset_type: seed.type,
      quantity: seed.quantity,
      average_cost: seed.cost,
      currency: seed.currency,
      current_price: seed.price,
      risk_bucket: seed.bucket,
      quantity_kind: "UNITS",
      market_source: "manual",
      price_as_of: seed.type === "cash" ? undefined : priceAsOf,
      version: 1,
      updated_at: stamp,
      names: seed.name,
    })),
    transactions: seedTransactions(now, stamp),
    imports: new Map(),
    committed: new Map(),
    sequence: 1000,
  };
}
