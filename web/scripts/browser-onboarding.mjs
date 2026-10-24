import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import assert from 'node:assert/strict'
import { createDemoApi } from '../src/demo/api.ts'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.FINUNITY_PLAYWRIGHT || 'playwright')
const base = process.argv[2] || 'http://127.0.0.1:5173'
const output = process.argv[3] || 'docs/qa-entry-2026-10-21/onboarding'
mkdirSync(output, { recursive: true })
const results = { date: '2026-10-21', base, steps: [] }
const passed = name => { results.steps.push(name); console.log(`PASS ${name}`) }
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const user = { user_id: 'synthetic-user', username: 'synthetic', email: null, language: 'zh-CN', base_currency: 'CNY', preferences_version: 0 }
const seed = await createDemoApi().api.ledgerSummary()
const empty = structuredClone(seed)
empty.records = { accounts: [], holdings: [], transactions: [], sources: [] }
empty.base = Object.fromEntries(Object.keys(empty.base).map(key => [key, 0]))
empty.by_currency = { CNY: empty.base, USD: empty.base }; empty.accounts = []; empty.excluded_currencies = []; empty.categories = []; empty.allocation.valid = false
empty.allocation.items = empty.allocation.items.map(row => ({ ...row, value: 0, share_pct: null, deviation_pct: null }))
empty.goals = { aggressive_pct: 30, balanced_pct: 60, defensive_pct: 10, version: 0 }
const errors = []
try {
  const context = await browser.newContext({ viewport: { width: 1366, height: 860 }, locale: 'zh-CN' })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  const requests = [], writes = []
  let failRow = true, failSettings = true
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    requests.push(path)
    const body = request.method() === 'POST' ? request.postDataJSON() : null
    if (body) writes.push({ path, body })
    let data
    if (path.endsWith('/auth/register') || path.endsWith('/auth/recovery/reset')) data = { user, access_token: 'synthetic-access', refresh_token: 'synthetic-refresh', recovery_code: 'SYNTHETIC-NOT-A-REAL-CODE' }
    else if (path.endsWith('/auth/password/login') || path.endsWith('/auth/login')) data = { user, access_token: 'synthetic-access', refresh_token: 'synthetic-refresh' }
    else if (path.endsWith('/me/preferences')) {
      if (failSettings) { failSettings = false; return route.abort('connectionfailed') }
      empty.base_currency = body.base_currency; Object.assign(user, { language: body.language, base_currency: body.base_currency }); data = { ...body, version: 1 }
    } else if (path.endsWith('/me/profile')) { Object.assign(user, body); data = user }
    else if (path.endsWith('/me/goals')) { empty.goals = { ...body, version: 1 }; data = empty.goals }
    else if (path.endsWith('/ledger/summary')) data = empty
    else if (path.endsWith('/me')) data = user
    else if (path.endsWith('/holdings:batch-upsert')) {
      if (failRow) { failRow = false; return route.abort('connectionfailed') }
      data = { items: [] }
    } else if (path.endsWith('/transactions:batch-upsert')) data = { items: [] }
    else return route.fulfill({ status: 404, json: { error: { message: `Unmocked ${path}` } } })
    return route.fulfill({ json: { data } })
  })
  await page.goto(`${base}/`)
  await page.locator('.allocation-bars').waitFor()
  assert.equal(await page.locator('.demo-reset').count(), 0)
  const desktopBars = await page.locator('.allocation-bars').boundingBox()
  assert.ok(desktopBars.y + desktopBars.height < 860)
  assert.deepEqual(await page.locator('.bucket-gap').allInnerTexts(), ['少约 ¥49,040', '无需调整', '多约 ¥53,280'])
  await page.screenshot({ path: join(output, 'desktop.png') })
  passed('root opens the example immediately; all three bars are visible at 1366 × 860')
  await page.locator('.demo-tour button', { hasText: '总资产怎么算' }).click()
  assert.equal(await page.locator('.portfolio-hero.tour-highlight').count(), 1)
  await page.locator('.demo-tour button', { hasText: '三类资产差多少' }).click()
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await page.locator('.demo-tour button', { hasText: '截图导入一次' }).click()
  assert.equal(await page.getByRole('button', { name: '已选择示例截图', exact: true }).count(), 1)
  assert.deepEqual(await page.evaluate(() => JSON.parse(sessionStorage.getItem('finunity-tour'))), [0, 1])
  await page.locator('.import-dialog').getByRole('button', { name: '导入持仓', exact: true }).click()
  await page.locator('.parsed-row').first().waitFor()
  await page.getByRole('button', { name: '确认导入持仓', exact: true }).click()
  await page.locator('.dialog').waitFor({ state: 'detached' })
  await page.locator('.demo-tour button', { hasText: '看本月复盘' }).click()
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  assert.deepEqual(await page.evaluate(() => JSON.parse(sessionStorage.getItem('finunity-tour'))), [0, 1, 2, 3])
  passed('tour highlights real features, preselects sample image and only completes import after confirmation; progress stays in sessionStorage')
  // SPA navigation and opening/closing auth must preserve edits and never reload the document.
  await page.evaluate(() => { window.__onboardingMarker = 'same-document' })
  await page.locator('.side-nav nav a', { hasText: '持仓' }).click()
  await page.locator('.holding-card').first().waitFor()
  assert.equal(await page.locator('.holding-card').count(), 14)
  await page.getByRole('button', { name: '添加持仓', exact: true }).click()
  await page.locator('input[name=name]').fill('访客自己的基金')
  await page.locator('input[name=quantity]').fill('10')
  await page.locator('input[name=currency]').fill('CNY')
  await page.locator('input[name=price]').fill('2')
  await page.locator('.dialog .primary-button').click()
  await page.locator('.dialog').waitFor({ state: 'detached' })
  await page.locator('.holding-card', { hasText: '访客自己的基金' }).waitFor()
  await page.locator('.demo-start').click()
  await page.locator('.auth-panel').waitFor()
  assert.equal(await page.locator('.auth-panel input:not([type=checkbox])').count(), 2)
  assert.equal(requests.length, 0)
  await page.goBack()
  await page.locator('.auth-panel').waitFor({ state: 'detached' })
  assert.equal(await page.locator('.holding-card').count(), 15)
  assert.equal(await page.evaluate(() => window.__onboardingMarker), 'same-document')
  passed('SPA navigation, two-field registration, back closes only the panel; edits survive; zero API requests')
  // Change targets, language and base currency via real demo controls.
  await page.locator('.side-nav nav a', { hasText: '总览' }).click()
  await page.locator('.allocation-panel .text-button').click()
  const goals = page.locator('.dialog input[type=range]')
  if (await goals.count()) {
    await goals.first().fill('20'); await goals.nth(1).fill('30')
  } else {
    const numbers = page.locator('.dialog input[type=number]')
    await numbers.nth(0).fill('20'); await numbers.nth(1).fill('30'); await numbers.nth(2).fill('50')
  }
  await page.locator('.dialog .primary-button').click()
  await page.locator('.dialog').waitFor({ state: 'detached' })
  await page.locator('.side-foot button').last().click()
  await page.locator('.dialog select').first().selectOption('USD')
  await page.getByRole('button', { name: '保存偏好', exact: true }).click()
  await page.locator('.dialog').waitFor({ state: 'detached' })
  await page.locator('.top-actions .language-toggle').click()
  await page.locator('.demo-start').click()
  await page.locator('.auth-panel input[autocomplete=username]').fill('synthetic')
  await page.locator('.auth-panel input[autocomplete=new-password]').fill('synthetic-password-only')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  assert.equal(requests.length, 0, 'unchecked consent must not submit')
  await page.getByRole('button', { name: 'Agree and create', exact: true }).click()
  await page.locator('.recovery-step').waitFor()
  const registration = writes.find(row => row.path.endsWith('/auth/register')).body
  assert.equal(registration.password, registration.password_confirmation)
  assert.ok(!registration.display_name)
  assert.ok(registration.accept_privacy && registration.accept_terms)
  passed('explicit consent creates account with only username/password; recovery stays in the panel')
  await page.goBack()
  await page.locator('.recovery-step').waitFor()
  await page.getByRole('checkbox', { name: 'I have saved the recovery code' }).check()
  await page.getByRole('button', { name: 'Open my ledger', exact: true }).click()
  await page.locator('.auth-panel [role=alert]').waitFor()
  await page.getByRole('button', { name: 'Open my ledger', exact: true }).click()
  await page.getByRole('heading', { name: 'Bring your own records' }).waitFor({ timeout: 6000 }).catch(async error => {
    console.log('Completion debug:', await page.locator('.auth-panel').innerText(), writes.map(row => ({ path: row.path, body: row.body })))
    throw error
  })
  assert.equal(await page.evaluate(() => window.__onboardingMarker), 'same-document')
  const preferences = writes.filter(row => row.path.endsWith('/me/preferences')).at(-1).body
  assert.equal(preferences.language, 'en-US'); assert.equal(preferences.base_currency, 'USD')
  assert.deepEqual([empty.goals.aggressive_pct, empty.goals.balanced_pct, empty.goals.defensive_pct], [20, 30, 50])
  assert.equal(await page.locator('.guest-row').count(), 1)
  assert.equal(await page.locator('.demo-banner').count(), 0)
  passed('recovery survives Back; settings failure is retryable; registered ledger replaces demo without reload')
  await page.getByRole('button', { name: 'Save this row', exact: true }).click()
  await page.getByRole('button', { name: 'Retry row', exact: true }).click()
  await page.locator('.guest-row').waitFor({ state: 'detached' })
  const holdingWrites = writes.filter(row => row.path.endsWith('/holdings:batch-upsert'))
  assert.equal(holdingWrites.length, 2)
  assert.deepEqual(holdingWrites[0].body, holdingWrites[1].body)
  assert.equal(holdingWrites[0].body.items[0].name, '访客自己的基金')
  assert.ok(!holdingWrites[0].body.items[0].holding_id)
  assert.ok(!holdingWrites[0].body.items[0].source_id)
  passed('only visitor record is offered; confirmation writes it; uncertain retry preserves request ID and body')
  await page.locator('.side-foot button').last().click()
  await page.getByLabel('Display name (optional)').fill('My household')
  await page.getByRole('button', { name: 'Save name', exact: true }).click()
  await page.locator('.side-foot strong', { hasText: 'My household' }).waitFor()
  await page.getByRole('button', { name: 'Close', exact: true }).click()
  passed('display name is optional in the profile and can be saved after registration')
  await page.goto(`${base}/demo`)
  await page.locator('.demo-banner').waitFor()
  assert.match(await page.locator('.demo-banner').innerText(), /does not affect your ledger/)
  await page.locator('.side-nav nav a', { hasText: 'Holdings' }).click()
  await page.locator('.holding-card').first().waitFor()
  assert.ok(page.url().endsWith('/demo/holdings'))
  assert.equal(await page.locator('.demo-banner').count(), 1)
  await page.locator('.demo-banner a', { hasText: 'Back to my ledger' }).click()
  await page.locator('.demo-banner').waitFor({ state: 'detached' })
  passed('signed-in visitors can revisit /demo and return to their own ledger')
  await context.close()

  for (const path of ['/login', '/register', '/en/', '/app?from=portfolio&lang=en']) {
    const context = await browser.newContext()
    const page = await context.newPage(); let calls = 0
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) calls++ })
    await page.goto(`${base}${path}`)
    await page.locator('.allocation-bars').waitFor()
    if (path === '/login' || path === '/register') await page.locator('.auth-panel').waitFor()
    else assert.match(await page.locator('.demo-banner').innerText(), /Example ledger/)
    assert.equal(calls, 0)
    await context.close()
  }
  passed('login/register deep links, English home and old portfolio language link work with zero guest API calls')
  for (const mode of ['login', 'recovery']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const paths = []
    await page.route('**/api/v1/**', route => {
      const path = new URL(route.request().url()).pathname; paths.push(path)
      if (path.endsWith('/ledger/summary')) return route.fulfill({ json: { data: empty } })
      const result = { user: { ...user, language: 'zh-CN' }, access_token: 'synthetic-access', refresh_token: 'synthetic-refresh' }
      if (mode === 'recovery') result.recovery_code = 'SYNTHETIC-RECOVERY'
      return route.fulfill({ json: { data: result } })
    })
    await page.goto(`${base}/login`)
    if (mode === 'recovery') await page.getByRole('button', { name: '忘记密码？使用恢复码' }).click()
    await page.locator('.auth-panel input[autocomplete=username]').fill('synthetic')
    if (mode === 'recovery') await page.getByLabel('恢复码', { exact: true }).fill('SYNTHETIC-OLD-CODE')
    await page.locator('.auth-panel input[type=password]').fill('synthetic-password-only')
    await page.locator('.auth-panel form .primary-button').last().click()
    if (mode === 'recovery') {
      await page.locator('.recovery-step').waitFor()
      const download = page.waitForEvent('download')
      await page.getByRole('button', { name: '下载 .txt' }).click()
      assert.equal((await download).suggestedFilename(), 'finunity-recovery-code.txt')
      await page.getByRole('button', { name: '进入我的账本', exact: true }).click()
    }
    await page.locator('.auth-panel').waitFor({ state: 'detached' })
    await page.locator('.portfolio-hero').waitFor()
    assert.equal(await page.locator('.demo-banner').count(), 0)
    assert.ok(!paths.some(path => path.includes('/me/preferences') || path.includes('/me/goals')))
    await context.close()
  }
  passed('password sign-in and recovery finish in place; recovery download enables continuation; existing settings stay intact')
  const mobileContext = await browser.newContext({ viewport: { width: 390, height: 812 }, isMobile: true, deviceScaleFactor: 1 })
  const mobile = await mobileContext.newPage(); await mobile.goto(`${base}/`)
  await mobile.locator('.allocation-bars').waitFor()
  const bars = await mobile.locator('.allocation-bars').boundingBox()
  assert.ok(bars.y + bars.height < 760, 'all bars appear above the bottom navigation')
  assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  await mobile.screenshot({ path: join(output, 'mobile.png') })
  await mobile.locator('.demo-start').click()
  await mobile.screenshot({ path: join(output, 'mobile-register.png') })
  await mobileContext.close()
  passed('390 × 812 first viewport shows all three bars; no horizontal overflow; bottom registration panel')
  // Slow restoration: no example is ever mounted while a saved session is loading.
  const restored = await browser.newContext()
  const signed = await restored.newPage()
  await signed.addInitScript(() => sessionStorage.setItem('finunity-session-token', 'synthetic'))
  await signed.route('**/api/v1/me', async route => { await new Promise(resolve => setTimeout(resolve, 500)); return route.fulfill({ json: { data: user } }) })
  await signed.route('**/api/v1/ledger/summary*', route => route.fulfill({ json: { data: empty } }))
  await signed.goto(`${base}/`)
  await signed.evaluate(() => { window.__demoFlash = false; new MutationObserver(() => { if (document.querySelector('.demo-banner')) window.__demoFlash = true }).observe(document.body, { childList: true, subtree: true }) })
  await signed.locator('.portfolio-hero').waitFor()
  assert.equal(await signed.evaluate(() => window.__demoFlash), false)
  await restored.close()
  passed('restored sessions open their own ledger at / with no flash of example data')
  assert.deepEqual(errors, [])
  results.pass = true
} finally {
  writeFileSync(join(output, 'results.json'), JSON.stringify(results, null, 2))
  await browser.close()
}
