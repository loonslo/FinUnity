import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createDemoApi } from '../src/demo/api.ts'
import { guestRows, settingsTransfer } from '../src/onboarding.ts'
import { DEMO_SAMPLE_FILE_NAME } from '../src/demo/sample.ts'

test('transfer excludes seed edits, sample imports and archived visitor records', async () => {
  const { api, state } = createDemoApi({ parseDelayMs: 0 })
  const ledger = await api.ledgerSummary()
  await api.saveHoldings([{ ...ledger.records.holdings[0], name: 'Renamed example', quantity: 1 }])
  await api.saveTransactions([{ ...ledger.records.transactions[0], title: 'Edited example' }])
  const sample = await api.parseImage(new File([''], DEMO_SAMPLE_FILE_NAME), 'zh-CN')
  const rows = sample.holdings.map(row => ({ ...row, action: 'ADD', metadata: { risk_bucket: 'BALANCED' } }))
  const batch = await api.createHoldingImport({ client_request_id: 'sample', document_ids: [sample.document_id], rows })
  const preview = await api.previewHoldingImport(batch.batch_id, { version: batch.version, rows })
  await api.commitHoldingImport(batch.batch_id, { client_request_id: 'commit', version: preview.version, rows })
  assert.deepEqual(guestRows(state, () => 'id'), [])
  const { items: [mine] } = await api.saveHoldings([{ name: 'My cash', asset_type: 'cash', quantity: 50, currency: 'CNY', source_id: 'demo-s1' }])
  await api.saveHoldings([{ ...mine, quantity: 75 }])
  const { items: [removed] } = await api.saveTransactions([{ title: 'Removed', transaction_type: 'deposit', transaction_date: '2026-10-21', amount_minor: 100, currency: 'CNY' }])
  await api.archiveTransaction(removed.transaction_id, removed.version)
  const transfer = guestRows(state, () => 'stable-id')
  assert.equal(transfer.length, 1)
  assert.equal(transfer[0].holding.quantity, 75)
  assert.equal(transfer[0].requestId, 'stable-id')
  assert.ok(!('source_id' in transfer[0].holding))
  assert.ok(!('holding_id' in transfer[0].holding))
})

test('visitor text import qualifies while updates to seed rows do not', async () => {
  const { api, state } = createDemoApi()
  const seed = (await api.ledgerSummary()).records.holdings[0]
  const rows = [{ name: 'My fund', asset_type: 'fund', quantity: '100', currency: 'USD', action: 'ADD' },
    { ...seed, name: 'Edited seed', action: 'UPDATE' }]
  const batch = await api.createHoldingImport({ client_request_id: 'manual', rows })
  const preview = await api.previewHoldingImport(batch.batch_id, { version: batch.version, rows })
  await api.commitHoldingImport(batch.batch_id, { client_request_id: 'manual-commit', version: preview.version, rows })
  assert.deepEqual(guestRows(state, () => 'request').map(row => row.label), ['My fund'])
})

test('settings transfer retries after partial success without replaying acknowledged preferences', async () => {
  const { api: demoApi, state } = createDemoApi()
  await demoApi.saveGoals([20, 30, 50], 1)
  await demoApi.savePreferences('en-US', 'USD')
  let preferenceWrites = 0, goalWrites = 0
  let goals = { version: 0, aggressive_pct: 30, balanced_pct: 60, defensive_pct: 10 }
  const server = {
    savePreferences: async (language, base_currency) => { preferenceWrites++; return { language, base_currency, version: 1 } },
    ledgerSummary: async () => ({ goals }),
    saveGoals: async values => {
      goalWrites++
      goals = { version: 1, aggressive_pct: values[0], balanced_pct: values[1], defensive_pct: values[2] }
      throw new Error('response lost after write')
    },
  }
  const carry = settingsTransfer(state, 'en-US')
  const user = { user_id: 'synthetic', email: null, username: 'test' }
  await assert.rejects(carry(server, user))
  const result = await carry(server, user)
  assert.equal(preferenceWrites, 1)
  assert.equal(goalWrites, 1)
  assert.equal(result.language, 'en-US')
  assert.equal(result.base_currency, 'USD')
})
