import type { LedgerSummary } from './types';

type ObjectValue = Record<string, unknown>;
const object = (value: unknown): value is ObjectValue => value !== null && typeof value === 'object' && !Array.isArray(value);
const moneyKeys = ['cash_balance_minor', 'holdings_value_minor', 'holdings_cost_minor', 'unrealized_profit_minor', 'total_assets_minor'];
const currencyKeys = [...moneyKeys, 'income_minor', 'expense_minor', 'net_cashflow_minor'];
const numbers = (value: unknown, keys: string[]) => object(value) && keys.every(key => Number.isSafeInteger(value[key]));

/** Reject partial snapshots. Missing balances are unknown, never zero. */
export function validateLedger(value: unknown): asserts value is LedgerSummary {
  const invalid = () => { throw new Error('账本快照不完整或数据不一致，请刷新或更新服务端 / Invalid ledger snapshot'); };
  if (!object(value)) return invalid();
  const { records, accounts, base, by_currency: currencies, goals, allocation, categories, price_refresh: refresh } = value;
  if (!object(records) || !Array.isArray(records.accounts) || !Array.isArray(records.holdings) || !Array.isArray(records.transactions) || !Array.isArray(records.sources || [])) return invalid();
  if (!numbers(base, currencyKeys) || !object(currencies) || !Object.values(currencies).every(v => numbers(v, currencyKeys))) return invalid();
  if (typeof value.base_currency !== 'string' || !/^[A-Z]{3}$/.test(value.base_currency) || !currencies[value.base_currency] || typeof value.month !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])$/.test(value.month) || typeof value.generated_at !== 'string' || !Number.isFinite(Date.parse(value.generated_at))) return invalid();
  if (!Array.isArray(value.excluded_currencies) || !value.excluded_currencies.every(c => typeof c === 'string')) return invalid();
  if (!Array.isArray(accounts)) return invalid();
  if (!object(goals) || !['aggressive_pct', 'balanced_pct', 'defensive_pct', 'version'].every(k => typeof goals[k] === 'number' && Number.isFinite(goals[k]))) return invalid();
  if (!object(allocation) || typeof allocation.valid !== 'boolean' || !Array.isArray(allocation.items) || !allocation.items.every(item => object(item) && ['AGGRESSIVE', 'BALANCED', 'DEFENSIVE'].includes(String(item.key)) && Number.isFinite(item.value) && (item.gap_minor == null || Number.isSafeInteger(item.gap_minor)) && ['share_pct', 'target_pct', 'deviation_pct'].every(k => item[k] === null || Number.isFinite(item[k])))) return invalid();
  if (!['drift_pct', 'score', 'largest_gap_pct'].every(k => allocation[k] === null || Number.isFinite(allocation[k])) || !Array.isArray(categories) || !categories.every(c => Array.isArray(c) && typeof c[0] === 'string' && Number.isSafeInteger(c[1])) || !object(refresh) || !['unconfigured', 'pending', 'success', 'partial', 'failed'].includes(String(refresh.status)) || !Number.isSafeInteger(refresh.updated_count)) return invalid();
  const accountIds = new Set<string>();
  for (const account of records.accounts) {
    if (!object(account) || typeof account.account_id !== 'string' || accountIds.has(account.account_id)) return invalid();
    accountIds.add(account.account_id);
  }
  const recordSources = Array.isArray(records.sources) ? records.sources : [];
  for (const source of recordSources) {
    if (!object(source) || typeof source.source_id !== 'string' || typeof source.name !== 'string') return invalid();
  }
  const summaryIds = new Set<string>();
  for (const account of accounts) {
    if (!object(account) || typeof account.account_id !== 'string' || summaryIds.has(account.account_id) || !numbers(account, [...moneyKeys, 'missing_price_count', 'missing_cost_count'])) return invalid();
    summaryIds.add(account.account_id);
  }
  for (const [rows, id] of [[records.holdings, 'holding_id'], [records.transactions, 'transaction_id']] as const) {
    const ids = new Set<string>();
    for (const row of rows) {
      if (!object(row) || typeof row[id] !== 'string' || ids.has(row[id]) || typeof row.currency !== 'string' || (row.account_id !== null && row.account_id !== undefined && (typeof row.account_id !== 'string' || !accountIds.has(row.account_id))) || (row.source_id !== null && row.source_id !== undefined && typeof row.source_id !== 'string')) return invalid();
      ids.add(row[id]);
    }
  }
}
