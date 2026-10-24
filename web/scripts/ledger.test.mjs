import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateLedger } from '../src/ledgerValidation.ts';
const fixture = JSON.parse(readFileSync(new URL('./fixtures/ledger.json', import.meta.url), 'utf8'));

test('accepts the real server contract, including null allocation metrics', () => {
  assert.doesNotThrow(() => validateLedger(fixture));
});

test('rejects partial balances instead of inventing zeros', () => {
  for (const mutate of [s => {delete s.base.cash_balance_minor;}, s => {s.records.sources = [{source_id: 1, name: 'bad'}];}, s => {s.categories = {};}, s => {s.allocation.items = null;}, s => {delete s.by_currency;}, s => {s.base.total_assets_minor = Infinity;}]) {
    const snapshot = structuredClone(fixture); mutate(snapshot);
    assert.throws(() => validateLedger(snapshot));
  }
});

test('rejects orphan and duplicate records', () => {
  const snapshot = structuredClone(fixture);
  snapshot.records.holdings = [{holding_id: 'h', account_id: 'missing', currency: 'CNY'}];
  assert.throws(() => validateLedger(snapshot));
  snapshot.records.holdings = [];
  snapshot.records.accounts.push(snapshot.records.accounts[0]);
  assert.throws(() => validateLedger(snapshot));
});
