import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validateLedger } from '../src/ledgerValidation.ts'
import { percentageOfMinor, productToMinor, roundHalfEven, toMinor } from '../src/demo/money.ts'
import { buildLedgerSummary, publicHolding } from '../src/demo/ledger.ts'
import { createDemoApi } from '../src/demo/api.ts'
import { DEMO_SAMPLE_FILE_NAME } from '../src/demo/sample.ts'

// 2026-10-14 本地时间中午：当月流水只含 1～14 日的记录
const NOW = new Date(2026, 9, 14, 12, 0, 0)

function demo(options = {}) {
  return createDemoApi({ now: () => NOW, parseDelayMs: 0, ...options })
}

test('minor-unit arithmetic rounds half away from zero like the server Decimal code', () => {
  assert.equal(toMinor(0.005), 1)
  assert.equal(toMinor('1.005'), 101)
  assert.equal(toMinor(-0.005), -1)
  assert.equal(productToMinor(3, 0.1), 30)
  assert.equal(productToMinor(20000, 1.2), 2_400_000)
  assert.equal(productToMinor('0.333', 3), 100) // 0.999 -> 99.9 minor -> 100
  assert.equal(productToMinor(1e-7, 1), 0)
  assert.equal(percentageOfMinor(305, 30), 92)
  assert.equal(percentageOfMinor(305, 10), 31)
})

test("the alignment score rounds like Python's round() (half to even)", () => {
  assert.equal(roundHalfEven(77.5), 78)
  assert.equal(roundHalfEven(78.5), 78)
  assert.equal(roundHalfEven(77.2), 77)
  assert.equal(roundHalfEven(0.5), 0)
  assert.equal(roundHalfEven(1.5), 2)
})

test('public_holding: cash is priced at 1, a missing price falls back to cost, VALUE rows use the amount', () => {
  const base = {
    holding_id: 'h', name: 'x', quantity_kind: 'UNITS', risk_bucket: 'BALANCED', currency: 'CNY',
    market_source: 'manual', version: 1, updated_at: 'now',
  }
  const cash = publicHolding({ ...base, asset_type: 'cash', quantity: 250, average_cost: null, current_price: null })
  assert.deepEqual([cash.market_value_minor, cash.cost_minor, cash.unrealized_profit_minor, cash.valuation_status], [25_000, 25_000, null, 'priced'])
  const unpriced = publicHolding({ ...base, asset_type: 'fund', quantity: 10, average_cost: 2, current_price: null })
  assert.deepEqual([unpriced.market_value_minor, unpriced.valuation_status, unpriced.unrealized_profit_minor], [2000, 'estimated', 0])
  const value = publicHolding({ ...base, asset_type: 'fund', quantity: 1234.56, quantity_kind: 'VALUE', average_cost: null, cost_value: 1000 })
  assert.deepEqual([value.market_value_minor, value.cost_minor, value.unrealized_profit_minor], [123_456, 100_000, 23_456])
})

test('the seeded ledger is a valid snapshot with the figures the demo is designed to show', async () => {
  const { api } = demo()
  const ledger = await api.ledgerSummary()
  assert.doesNotThrow(() => validateLedger(ledger))
  // 40,000 cash + 36,000 + 24,000 defensive; 33,000 + 25,000 + 31,200 balanced; 24,600 + 19,800 aggressive
  assert.equal(ledger.base.total_assets_minor, 23_360_000)
  assert.equal(ledger.base.cash_balance_minor, 4_000_000)
  assert.equal(ledger.base.unrealized_profit_minor, 570_000)
  const byKey = Object.fromEntries(ledger.allocation.items.map(item => [item.key, item]))
  assert.deepEqual([byKey.AGGRESSIVE.value, byKey.BALANCED.value, byKey.DEFENSIVE.value], [4_440_000, 8_920_000, 10_000_000])
  assert.deepEqual([byKey.AGGRESSIVE.target_pct, byKey.BALANCED.target_pct, byKey.DEFENSIVE.target_pct], [40, 40, 20])
  assert.deepEqual(ledger.allocation.items.map(item => item.gap_minor), [4_904_000, 424_000, -5_328_000])
  // 偏离提示和月度复盘都要有内容：最大偏离 ≥ 10 个百分点，评分 77
  assert.ok(ledger.allocation.largest_gap_pct >= 10)
  assert.equal(ledger.allocation.score, 77)
  // 美元、港币不折算进人民币总额，只出现在“未折算”里
  assert.deepEqual(ledger.excluded_currencies, ['HKD', 'USD'])
  // 当月（1～14 日）：收入 18,000 + 利息 45 + 分红 260；支出 6,500 + 1,850 + 520 + 380 + 12 + 900；买入不算收支
  assert.equal(ledger.base.income_minor, 1_830_500)
  assert.equal(ledger.base.expense_minor, 1_016_200)
  assert.equal(ledger.base.net_cashflow_minor, 814_300)
  assert.deepEqual(ledger.categories.map(([name]) => name), ['工资', '居住', '餐饮', '购物', '交通', '投资收益'])
  assert.deepEqual(ledger.records.accounts, [])
})

test('the demo covers six months of flows and never dates one after today', async () => {
  const { api } = demo()
  const ledger = await api.ledgerSummary()
  const months = new Set(ledger.records.transactions.map(item => item.transaction_date.slice(0, 7)))
  assert.deepEqual([...months].sort(), ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'])
  assert.ok(ledger.records.transactions.every(item => item.transaction_date <= '2026-10-14'))
  const september = await api.ledgerSummary('2026-09')
  assert.doesNotThrow(() => validateLedger(september))
  assert.ok(september.base.income_minor > 0)
})

test('allocation is empty-safe: no base-currency assets means no score and no shares', () => {
  const summary = buildLedgerSummary(
    { baseCurrency: 'CNY', goals: { aggressive_pct: 30, balanced_pct: 60, defensive_pct: 10, version: 0 }, holdings: [], transactions: [], sources: [] },
    '2026-10', NOW,
  )
  assert.doesNotThrow(() => validateLedger(summary))
  assert.equal(summary.allocation.valid, false)
  assert.deepEqual([summary.allocation.score, summary.allocation.drift_pct, summary.allocation.largest_gap_pct], [null, null, null])
  assert.ok(summary.allocation.items.every(item => item.share_pct === null && item.deviation_pct === null && item.gap_minor === null))
})

test('switching the base currency re-sums the ledger without inventing a conversion', async () => {
  const { api } = demo()
  const me = await api.me()
  const saved = await api.savePreferences('zh-CN', 'USD', me.preferences_version)
  assert.equal(saved.base_currency, 'USD')
  const ledger = await api.ledgerSummary()
  assert.doesNotThrow(() => validateLedger(ledger))
  assert.equal(ledger.base_currency, 'USD')
  // USD cash 3,000 + ETF 40 × 430 = 17,200
  assert.equal(ledger.base.total_assets_minor, 2_020_000)
  assert.deepEqual(ledger.excluded_currencies, ['CNY', 'HKD'])
  await assert.rejects(api.savePreferences('zh-CN', 'EUR', 0), { code: 'VERSION_CONFLICT' })
})

test('holdings: add, edit with optimistic versioning, archive', async () => {
  const { api } = demo()
  const before = await api.ledgerSummary()
  const { items } = await api.saveHoldings([{ name: '手填基金', asset_type: 'fund', quantity: 100, average_cost: 2, current_price: 2.5, currency: 'cny', risk_bucket: 'AGGRESSIVE' }])
  const [added] = items
  assert.equal(added.currency, 'CNY')
  assert.equal(added.market_value_minor, 25_000)
  assert.equal(added.version, 1)
  assert.ok(added.price_as_of, 'a manually entered price is stamped with the time it was entered')

  const edited = (await api.saveHoldings([{ ...added, quantity: 200 }])).items[0]
  assert.equal(edited.version, 2)
  assert.equal(edited.market_value_minor, 50_000)
  await assert.rejects(api.saveHoldings([{ ...added, quantity: 1 }]), { code: 'VERSION_CONFLICT' })

  await api.archiveHolding(edited.holding_id, edited.version)
  const after = await api.ledgerSummary()
  assert.equal(after.records.holdings.length, before.records.holdings.length)
  await assert.rejects(api.archiveHolding(edited.holding_id, edited.version), { code: 'NOT_FOUND' })
})

test('holdings: invalid input is refused the way the server contract refuses it', async () => {
  const { api } = demo()
  const ok = { name: 'x', asset_type: 'fund', quantity: 1, currency: 'CNY' }
  for (const bad of [{ name: ' ' }, { asset_type: 'swap' }, { quantity: -1 }, { quantity: Number.NaN }, { currency: 'RMBXYZ' }, { average_cost: -1 }, { current_price: -0.1 }, { risk_bucket: 'WILD' }]) {
    await assert.rejects(api.saveHoldings([{ ...ok, ...bad }]), { status: 422 }, JSON.stringify(bad))
  }
  assert.equal((await api.ledgerSummary()).records.holdings.length, 11, 'a rejected batch writes nothing')
})

test('goals: must total 100 and respect the version', async () => {
  const { api } = demo()
  await assert.rejects(api.saveGoals([50, 40, 20], 1), { status: 422 })
  await assert.rejects(api.saveGoals([50, 30, 20], 0), { code: 'VERSION_CONFLICT' })
  await api.saveGoals([20, 30, 50], 1)
  const ledger = await api.ledgerSummary()
  assert.deepEqual([ledger.goals.aggressive_pct, ledger.goals.balanced_pct, ledger.goals.defensive_pct, ledger.goals.version], [20, 30, 50, 2])
  assert.equal(ledger.allocation.items.find(item => item.key === 'DEFENSIVE').target_pct, 50)
})

test('transactions: a new flow shows up in its month and category totals', async () => {
  const { api } = demo()
  const { items } = await api.saveTransactions([{ title: '手填支出', transaction_type: 'withdrawal', transaction_date: '2026-10-14', category: '旅行', amount_minor: 50_000, currency: 'CNY' }])
  assert.equal(items[0].transaction_type, 'withdrawal')
  const ledger = await api.ledgerSummary('2026-10')
  assert.equal(ledger.base.expense_minor, 1_016_200 + 50_000)
  assert.ok(ledger.categories.some(([name, value]) => name === '旅行' && value === 50_000))
  await assert.rejects(api.saveTransactions([{ title: 'x', transaction_type: 'withdrawal', transaction_date: '14/10/2026', amount_minor: 1 }]), { status: 422 })
  await api.archiveTransaction(items[0].transaction_id, items[0].version)
  assert.equal((await api.ledgerSummary('2026-10')).base.expense_minor, 1_016_200)
})

test('built-in rows follow the language; a row the visitor renames keeps their text', async () => {
  let language = 'zh-CN'
  const { api } = demo({ locale: () => language })
  const zh = (await api.ledgerSummary()).records.holdings
  language = 'en-US'
  const en = (await api.ledgerSummary()).records.holdings
  assert.equal(zh[3].name, '示例宽基指数基金')
  assert.equal(en[3].name, 'Example broad-index fund')
  assert.equal((await api.ledgerSummary()).records.sources[0].name, 'Example bank')
  // Saving a built-in row under its displayed name keeps it bilingual; a new name sticks.
  await api.saveHoldings([{ ...en[3], quantity: 1 }])
  await api.saveHoldings([{ ...en[4], name: 'My dividend fund' }])
  language = 'zh-CN'
  const after = (await api.ledgerSummary()).records.holdings
  assert.equal(after[3].name, '示例宽基指数基金')
  assert.equal(after[4].name, 'My dividend fund')
})

test('screenshot import: recognise the sample, review candidates, then write', async () => {
  const { api } = demo()
  await assert.rejects(api.parseImage(new File(['x'], 'mine.png'), 'zh-CN'), { code: 'DEMO_MODE' })
  const parsed = await api.parseImage(new File([''], DEMO_SAMPLE_FILE_NAME), 'zh-CN')
  assert.equal(parsed.holdings.length, 3)
  assert.ok(parsed.holdings.every(row => row.document_id === parsed.document_id))
  assert.equal(parsed.holdings[2].needs_confirmation, true)

  const rows = parsed.holdings.map((row, index) => ({
    row_id: `row-${index + 1}`, action: 'ADD', name: row.name, instrument_id: row.instrument_id, asset_type: row.asset_type.toUpperCase(),
    quantity: String(row.quantity), average_cost: row.average_cost, current_price: row.current_price, currency: row.currency,
    document_id: row.document_id, metadata: { risk_bucket: 'BALANCED' },
  }))
  const created = await api.createHoldingImport({ client_request_id: 'req-1', document_ids: [parsed.document_id], rows })
  assert.equal(created.status, 'DRAFT')
  const preview = await api.previewHoldingImport(created.batch_id, { version: created.version, rows })
  assert.equal(preview.status, 'READY')
  // DEMO-IDX already exists in CNY → one candidate that needs confirmation; the others are new.
  assert.deepEqual(preview.plan.map(item => item.candidate_holding_ids.length), [1, 0, 0])
  assert.deepEqual(preview.plan.map(item => item.needs_confirmation), [true, false, false])
  await assert.rejects(api.previewHoldingImport(created.batch_id, { version: created.version, rows }), { code: 'VERSION_CONFLICT' })

  const target = preview.plan[0].candidate_holding_ids[0]
  const decided = rows.map((row, index) => index === 0
    ? { ...row, action: 'UPDATE', holding_id: target, version: 1 }
    : index === 1 ? row : { ...row, action: 'SKIP' })
  const commit = await api.commitHoldingImport(created.batch_id, { client_request_id: 'req-commit', version: preview.version, rows: decided })
  assert.equal(commit.status, 'COMPLETED')
  assert.equal(commit.items.length, 2)
  const holdings = (await api.ledgerSummary()).records.holdings
  assert.equal(holdings.length, 12, 'one update, one add, one skip')
  assert.equal(holdings.find(item => item.holding_id === target).quantity, 32000)

  // 重试同一个提交不会重复写入
  const again = await api.commitHoldingImport(created.batch_id, { client_request_id: 'req-commit', version: preview.version, rows: decided })
  assert.equal(again.items.length, 2)
  assert.equal((await api.ledgerSummary()).records.holdings.length, 12)
})

test('screenshot import: an update without a target and an unknown action are refused', async () => {
  const { api } = demo()
  const row = { name: 'x', asset_type: 'FUND', quantity: '1', currency: 'CNY' }
  const created = await api.createHoldingImport({ client_request_id: 'a', rows: [row] })
  const preview = await api.previewHoldingImport(created.batch_id, { version: created.version, rows: [row] })
  await assert.rejects(api.commitHoldingImport(created.batch_id, { client_request_id: 'b', version: preview.version, rows: [{ ...row, action: 'UPDATE' }] }), { code: 'IMPORT_DECISION_REQUIRED' })
  await assert.rejects(api.commitHoldingImport(created.batch_id, { client_request_id: 'c', version: preview.version, rows: [{ ...row, action: 'DELETE' }] }), { code: 'IMPORT_DECISION_INVALID' })
  await assert.rejects(api.commitHoldingImport(created.batch_id, { client_request_id: 'd', version: preview.version, rows: [{ ...row, quantity: 'abc' }] }), { code: 'IMPORT_ROW_INVALID' })
})

test('the demo never touches the network, and account features are not available in it', async () => {
  const calls = []
  const realFetch = globalThis.fetch
  globalThis.fetch = (...args) => { calls.push(args); return Promise.reject(new Error('the demo must not call fetch')) }
  try {
    const { api } = demo()
    await api.authConfig()
    await api.me()
    await api.ledgerSummary()
    await api.refreshHoldings()
    await api.listSources()
    await api.saveSource({ name: '手填来源' })
    await api.saveHoldings([{ name: 'x', asset_type: 'fund', quantity: 1, currency: 'CNY' }])
    await api.parseImage(new File([''], DEMO_SAMPLE_FILE_NAME), 'en-US')
    await api.logout()
    for (const call of [
      () => api.registerUsername('a', 'b', 'b', 'c', true),
      () => api.recoverPassword('a', 'b', 'c', 'c'),
      () => api.changePassword('a', 'b', 'b'),
      () => api.regenerateRecovery('a'),
      () => api.setUsername('a', 'b'),
      () => api.deleteAccount('a'),
      () => api.exportData(),
      () => api.sendCode('a@b.c', 'login', 'zh-CN'),
      () => api.login('a', 'b', 'c'),
      () => api.register('a', 'b', 'c', 'd'),
      () => api.loginWithPassword('a', 'b', 'c'),
      () => api.setPassword('a', 'b', 'c', 'd'),
      () => api.saveAccount({ name: 'x', client_request_id: 'y' }),
      () => api.archiveAccount('x', 1),
    ]) await assert.rejects(Promise.resolve().then(call), { code: 'DEMO_MODE' })
    assert.throws(() => api.completeAuth({ user: {} }), { code: 'DEMO_MODE' })
  } finally {
    globalThis.fetch = realFetch
  }
  assert.deepEqual(calls, [])
})

test('every demo instance starts from the same seed and keeps edits to itself', async () => {
  const first = demo().api
  const second = demo().api
  await first.saveHoldings([{ name: '只在第一份里', asset_type: 'fund', quantity: 1, currency: 'CNY' }])
  assert.equal((await first.ledgerSummary()).records.holdings.length, 12)
  assert.equal((await second.ledgerSummary()).records.holdings.length, 11)
})
