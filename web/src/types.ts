export type Locale = "zh-CN" | "en-US";
export type RiskBucket = "AGGRESSIVE" | "BALANCED" | "DEFENSIVE";
export type HoldingImportAction = "ADD" | "UPDATE" | "SKIP";

export interface User {
  user_id: string;
  email: string | null;
  username: string | null;
  requires_username?: boolean;
  display_name?: string;
  language?: Locale;
  base_currency?: string;
  preferences_version?: number;
}

export interface Account {
  account_id: string;
  name: string;
  account_type: string;
  institution?: string;
  currency: string;
  status?: string;
  balance_minor?: number;
  opening_balance_minor: number;
  version: number;
  updated_at: string;
}

export interface Source {
  source_id: string;
  name: string;
  version: number;
  updated_at: string;
}

export interface Holding {
  market_value_minor: number;
  cost_minor: number;
  unrealized_profit_minor: number | null;
  valuation_status: "priced" | "estimated";
  holding_id: string;
  account_id?: string;
  source_id?: string;
  source_name?: string;
  snapshot_date?: string;
  instrument_id?: string;
  name: string;
  asset_type: string;
  quantity: number;
  quantity_unit?: string;
  quantity_kind?: "UNITS" | "VALUE";
  average_cost?: number;
  current_price?: number;
  currency: string;
  risk_bucket?: RiskBucket;
  market_source?: string;
  price_as_of?: string;
  version: number;
  updated_at: string;
}

export interface Transaction {
  transaction_id: string;
  account_id?: string;
  to_account_id?: string;
  source_id?: string;
  transaction_date: string;
  transaction_type:
    | "deposit"
    | "withdrawal"
    | "buy"
    | "sell"
    | "dividend"
    | "fee"
    | "interest"
    | "transfer"
    | "other";
  category?: string;
  title: string;
  amount_minor: number;
  currency: string;
  note?: string;
  version: number;
  updated_at: string;
}

export interface ParsedHolding {
  name: string;
  instrument_id?: string;
  quantity: number;
  quantity_unit?: string;
  quantity_kind?: "UNITS" | "VALUE";
  average_cost?: number;
  current_price?: number;
  currency?: string;
  source_id?: string;
  source_name?: string;
  snapshot_date?: string;
  document_id?: string;
  asset_type?: string;
  confidence?: number;
  review_reasons?: string[];
  needs_confirmation?: boolean;
  action?: HoldingImportAction;
  candidate_holding_ids?: string[];
  holding_id?: string;
  holding_version?: number;
}

export interface ParseResult {
  document_id: string;
  holdings: ParsedHolding[];
  warnings?: string[];
}

export interface HoldingImport {
  batch_id: string;
  status: "DRAFT" | "READY" | "PARTIAL" | "COMPLETED";
  documents: string[];
  rows: Array<Record<string, unknown>>;
  plan: Array<Record<string, unknown>>;
  version: number;
  created_at: string;
  updated_at: string;
}

export interface LedgerCurrencySummary {
  cash_balance_minor: number;
  holdings_value_minor: number;
  holdings_cost_minor: number;
  unrealized_profit_minor: number;
  total_assets_minor: number;
  income_minor: number;
  expense_minor: number;
  net_cashflow_minor: number;
}

export interface LedgerAccountSummary {
  account_id: string;
  currency: string;
  cash_balance_minor: number;
  holdings_value_minor: number;
  holdings_cost_minor: number;
  unrealized_profit_minor: number;
  total_assets_minor: number;
  missing_price_count: number;
  missing_cost_count: number;
}

export interface LedgerSummary {
  price_refresh: { status: "unconfigured" | "pending" | "success" | "partial" | "failed"; updated_count: number; unavailable_count?: number; attempted_at?: string };
  generated_at: string;
  records: { accounts: Account[]; holdings: Holding[]; transactions: Transaction[]; sources?: Source[] };
  goals: { aggressive_pct: number; balanced_pct: number; defensive_pct: number; version: number };
  allocation: {
    valid: boolean;
    items: Array<{ key: RiskBucket; value: number; share_pct: number | null; target_pct: number; gap_minor?: number | null; deviation_pct: number | null }>;
    drift_pct: number | null;
    score: number | null;
    largest_gap_pct: number | null;
  };
  categories: [string, number][];
  month: string;
  base_currency: string;
  base: LedgerCurrencySummary;
  by_currency: Record<string, LedgerCurrencySummary>;
  accounts: LedgerAccountSummary[];
  excluded_currencies: string[];
}
