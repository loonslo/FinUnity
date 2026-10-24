/*
 * Real-browser acceptance for the no-account demo workspace.
 *
 *   FINUNITY_PLAYWRIGHT=<path to playwright> node scripts/browser-demo.mjs <base url> <output dir>
 *
 * Needs a served build (vite dev server or preview) and a local Chrome. It never
 * signs in and never mocks the API: the point is that a visitor can use the whole
 * workspace and the page makes no request to the server at all.
 */
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.FINUNITY_PLAYWRIGHT || 'playwright')
const base = (process.argv[2] || 'http://127.0.0.1:5173').replace(/\/$/, '')
const output = process.argv[3] || 'demo-review'
mkdirSync(output, { recursive: true })

const results = { date: new Date().toISOString().slice(0, 10), base, steps: [], layouts: [] }
const step = (name) => results.steps.push({ name, pass: true })

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' })
const page = await context.newPage()
const requests = []
const problems = []
page.on('request', (request) => requests.push({ method: request.method(), url: new URL(request.url()) }))
page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
page.on('console', (message) => { if (message.type() === 'error') problems.push(`console.error: ${message.text()}`) })

// A success notice from the previous step can still be on screen, so wait for the dialog to close
// and then poll for the visible effect instead of trusting the notice.
const dialogClosed = () => page.locator('.dialog').waitFor({ state: 'detached' })
async function eventually(check, message) {
  const deadline = Date.now() + 5000
  let last
  while (Date.now() < deadline) {
    try { last = await check(); if (last === true) return } catch (error) { last = error }
    await page.waitForTimeout(40)
  }
  assert.fail(`${message} (last: ${last})`)
}
const hero = () => page.locator('.portfolio-hero strong').first()
const holdingCards = () => page.locator('.holding-card')
const goTo = (label) => page.locator('.side-nav nav a', { hasText: label }).click()
const shot = (name, options = {}) => page.screenshot({ path: join(output, name), ...options })

try {
  // ---- 1. arriving from the portfolio: both strips, real workspace, example numbers
  await page.goto(`${base}/?from=portfolio`)
  await hero().waitFor()
  assert.match(await page.locator('.demo-origin').innerText(), /半開 的作品/)
  assert.match(await page.locator('.demo-banner').innerText(), /示例账本/)
  assert.equal(await page.locator('.demo-origin a').getAttribute('href'), 'https://halfopen.dev/')
  assert.equal(await hero().innerText(), '¥233,600.00')
  const shares = await page.locator('.allocation-list b').allInnerTexts()
  assert.deepEqual(shares, ['19.0%', '38.2%', '42.8%'])
  assert.match(await page.locator('.currency-warning').innerText(), /HKD, USD/)
  assert.match(await page.locator('.portfolio-hero footer').innerText(), /示例数据/, 'the overview must not claim the data comes from the server')
  assert.equal(await page.locator('.alert-badge').innerText(), '1')
  await shot('overview-desktop.png', { fullPage: true })
  step('portfolio visitor sees both strips and the real overview on example data')

  // sessionStorage keeps the strip within the tab; a visitor without the mark never sees it
  await goTo('持仓')
  await holdingCards().first().waitFor()
  assert.equal(await page.locator('.demo-origin').count(), 1)
  const fresh = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const plain = await fresh.newPage()
  await plain.goto(`${base}/`)
  await plain.locator('.demo-banner').waitFor()
  assert.equal(await plain.locator('.demo-origin').count(), 0)
  await fresh.close()
  step('the portfolio strip persists in the tab and never shows for other visitors')

  // ---- 2. holdings: 11 rows, filter, edit
  assert.equal(await holdingCards().count(), 11)
  await shot('holdings-desktop.png', { fullPage: true })
  await page.locator('.search-box input').fill('黄金')
  assert.equal(await holdingCards().count(), 1)
  await page.getByRole('button', { name: '清除筛选' }).click()
  assert.equal(await holdingCards().count(), 11)
  await page.locator('.holding-card', { hasText: '示例成长股 A' }).click()
  await page.locator('input[name=quantity]').fill('500')
  await page.locator('.dialog .primary-button').click()
  await dialogClosed()
  await eventually(async () => (await page.locator('.holding-card', { hasText: '示例成长股 A' }).innerText()).includes('¥30,750.00'), '500 units x 61.5 = 30,750 shown after the edit')
  assert.match(await page.locator('.inline-notice').first().innerText(), /示例已更新/, 'the notice must not claim the edit was saved or synced')
  step('holdings list, filter and edit work on the example ledger')

  // ---- 3. screenshot import: sample → recognise → review → decide → write
  await page.getByRole('button', { name: '导入持仓', exact: true }).first().click()
  const dialog = page.locator('.import-dialog')
  await dialog.locator('.demo-sample img').waitFor()
  assert.equal(await dialog.locator('input[type=file]').count(), 0, 'the demo offers the sample, not an upload')
  await dialog.getByRole('button', { name: '使用这张示例截图' }).click()
  await dialog.getByRole('button', { name: '导入持仓', exact: true }).click()
  await dialog.locator('.parsed-row').first().waitFor()
  assert.equal(await dialog.locator('.parsed-row').count(), 3)
  assert.ok(await dialog.getByLabel('导入决定 1').count(), 'the first row matches an existing holding')
  assert.match(await dialog.locator('.parsed-row').nth(2).innerText(), /需人工确认/)
  await shot('import-review.png')
  await dialog.getByLabel('导入决定 1').selectOption('UPDATE')
  await dialog.getByRole('button', { name: '确认导入持仓' }).click()
  await dialogClosed()
  await eventually(async () => (await holdingCards().count()) === 13, 'one update + two new rows')
  step('sample screenshot import: recognise, review, choose update/add, write')

  // ---- 4. goals and review
  await goTo('总览')
  await page.getByRole('button', { name: /调整目标/ }).click()
  await page.getByLabel('进取 %').fill('20')
  await page.getByLabel('稳健 %').fill('30')
  await page.getByLabel('防守 %').fill('50')
  await page.locator('.goals-form .primary-button').click()
  await dialogClosed()
  await eventually(async () => /目标 50%/.test(await page.locator('.allocation-list').innerText()), 'new targets shown')
  await page.getByRole('button', { name: /开始复盘/ }).click()
  await page.locator('.review-dialog .score b').waitFor()
  assert.match(await page.locator('.review-dialog').innerText(), /目标贴合度/)
  await shot('review-dialog.png')
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  step('three-bucket targets can be changed and the monthly review reads them')

  // ---- 5. flows
  await goTo('资金流')
  await page.locator('.flow-row').first().waitFor()
  const before = await page.locator('.flow-row').count()
  await page.getByRole('button', { name: '记录流水' }).first().click()
  await page.locator('input[name=title]').fill('浏览器测试支出')
  await page.locator('input[name=amount]').fill('2000') // above the 6th category, so it shows in the top-6 totals
  await page.locator('input[name=category]').fill('测试')
  await page.locator('.dialog .primary-button').click()
  await dialogClosed()
  await eventually(async () => (await page.locator('.flow-row').count()) === before + 1, 'new flow listed')
  await eventually(async () => /测试/.test(await page.locator('.categories').innerText()), 'new category total')
  await shot('flows-desktop.png', { fullPage: true })
  step('a new transaction appears in the month list and the category totals')

  // ---- 6. privacy and profile: honest copy, no account features, base currency switch needs no conversion
  await page.locator('.side-foot > button').first().click()
  assert.match(await page.locator('.privacy-copy').innerText(), /示例数据不上传、不保存/)
  assert.doesNotMatch(await page.locator('.privacy-copy').innerText(), /服务器内存/)
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await dialogClosed()
  await page.locator('.side-foot > button').nth(1).click()
  const profile = page.locator('.profile-card')
  assert.match(await profile.locator('.demo-profile').innerText(), /示例里没有账号/)
  assert.equal(await profile.locator('.account-security').count(), 0, 'no password / delete-account form in the demo')
  await profile.locator('select').selectOption('USD')
  await profile.getByRole('button', { name: '保存偏好' }).click()
  await dialogClosed()
  await goTo('总览')
  await eventually(async () => (await hero().innerText()) === 'US$20,200.00', 'USD total')
  assert.match(await page.locator('.currency-warning').innerText(), /CNY, HKD/)
  step('base currency switch re-sums in USD and lists the rest as not converted')

  // ---- 7. language switch shows the English example names
  await page.locator('.top-actions .language-toggle').click()
  await page.locator('.demo-banner', { hasText: 'Example ledger' }).waitFor()
  await goTo('Holdings')
  await holdingCards().first().waitFor()
  assert.match(await page.locator('.holdings-grid').innerText(), /Example global index ETF/)
  await shot('holdings-en.png', { fullPage: true })
  await page.locator('.top-actions .language-toggle').click()
  step('English and Chinese names both come from the example data')

  // ---- 8. reset restores the seed; so does a reload
  await page.locator('.demo-banner').getByRole('button', { name: '重置示例' }).click()
  await holdingCards().first().waitFor()
  assert.equal(await holdingCards().count(), 11)
  await goTo('总览')
  assert.equal(await hero().innerText(), '¥233,600.00', 'reset also restores the base currency')
  await goTo('持仓')
  await page.getByRole('button', { name: '导入持仓', exact: true }).first().click()
  await page.locator('.import-dialog').getByRole('button', { name: '使用这张示例截图' }).click()
  await page.locator('.import-dialog').getByRole('button', { name: '导入持仓', exact: true }).click()
  await page.locator('.parsed-row').first().waitFor()
  await page.locator('.import-dialog').getByRole('button', { name: '确认导入持仓' }).click()
  await dialogClosed()
  await eventually(async () => (await holdingCards().count()) === 14, 'ADD is the default decision for every row')
  await page.reload()
  await holdingCards().first().waitFor()
  assert.equal(await holdingCards().count(), 11, 'edits live in memory only')
  step('reset and reload both return to the seeded ledger')

  // ---- 9. layout on a phone: no overflow, banner still pinned, bottom nav present
  for (const [width, height] of [[1280, 900], [768, 1024], [390, 844], [320, 700]]) {
    await page.setViewportSize({ width, height })
    await page.goto(`${base}/`)
    await hero().waitFor()
    const metrics = await page.evaluate(() => ({
      overflow: document.documentElement.scrollWidth > innerWidth + 1,
      bannerTop: Math.round(document.querySelector('.demo-banner').getBoundingClientRect().top),
    }))
    assert.equal(metrics.overflow, false, `${width}px: no horizontal overflow`)
    await page.mouse.wheel(0, 900)
    await page.waitForTimeout(150)
    const pinned = await page.evaluate(() => document.querySelector('.demo-banner').getBoundingClientRect().top)
    assert.ok(pinned >= 0 && pinned < 120, `${width}px: the demo banner stays in view while scrolling (top=${pinned})`)
    results.layouts.push({ width, pass: true })
    if (width === 390) await shot('overview-mobile.png')
  }
  step('no horizontal overflow at 1280/768/390/320 and the demo banner stays pinned')

  // ---- 10. the whole session never talked to the server
  const apiCalls = requests.filter(({ url }) => url.pathname.startsWith('/api') || url.port === '8000')
  const writes = requests.filter(({ method }) => !['GET', 'HEAD'].includes(method))
  assert.deepEqual(apiCalls.map(({ url }) => url.href), [], 'no request to /api')
  assert.deepEqual(writes.map(({ method, url }) => `${method} ${url.href}`), [], 'no write request at all')
  results.requestCount = requests.length
  step(`zero API requests and zero write requests across ${requests.length} page requests`)

  const real = problems.filter((message) => !/favicon|Failed to load resource/.test(message))
  assert.deepEqual(real, [], 'no page or console errors')
  step('no page errors or console errors')
  results.verdict = 'passed'
} catch (error) {
  results.verdict = 'failed'
  results.error = String(error && error.stack ? error.stack : error)
  await shot('failure.png', { fullPage: true }).catch(() => {})
  throw error
} finally {
  writeFileSync(join(output, 'checks.json'), JSON.stringify(results, null, 2))
  await browser.close()
}
console.log(`Demo browser check passed: ${results.steps.length} steps, ${results.requestCount} page requests, none to the API.`)
