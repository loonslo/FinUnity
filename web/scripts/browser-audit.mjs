import { createRequire } from 'node:module';
import { readFileSync, mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.FINUNITY_PLAYWRIGHT || 'playwright');
const snapshot = JSON.parse(readFileSync(new URL('./fixtures/ledger.json', import.meta.url), 'utf8'));
const browser = await chromium.launch({ headless: true, channel: 'chrome' });
const output = new URL('../docs/qa-2026-09-13/', import.meta.url);
mkdirSync(output, { recursive: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let failRefresh = false;
  const writes = [];
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/me')) return route.fulfill({ json: { data: { user_id: 'audit', email: 'audit@example.test', language: 'zh-CN', base_currency: 'CNY' } } });
    if (path.endsWith('/ledger/summary')) return route.fulfill(failRefresh ? { status: 503, json: { error: { message: 'Audit refresh failure' } } } : { json: { data: snapshot } });
    if (path.endsWith('/sources') && route.request().method() === 'GET') return route.fulfill({ json: { data: { items: [] } } });
    if (path.endsWith('/holding-imports') && route.request().method() === 'POST') {
      return route.fulfill({ json: { data: { batch_id: 'imp-audit', status: 'DRAFT', documents: [], rows: [], plan: [], version: 1, created_at: '2026-09-13T10:34:17Z', updated_at: '2026-09-13T10:34:17Z' } } });
    }
    if (path.includes('/holding-imports/imp-audit:preview')) {
      return route.fulfill({ json: { data: { batch_id: 'imp-audit', status: 'READY', documents: [], rows: [], plan: [], version: 2, created_at: '2026-09-13T10:34:17Z', updated_at: '2026-09-13T10:34:17Z' } } });
    }
    if (path.includes('/holding-imports/imp-audit:commit')) {
      writes.push(route.request().postDataJSON());
      if (writes.length === 1) return route.abort('connectionfailed');
      failRefresh = true;
      return route.fulfill({ json: { data: { batch_id: 'imp-audit', status: 'COMPLETED', version: 3, items: [] } } });
    }
    return route.fulfill({ status: 404, json: { error: { message: `Unmocked ${path}` } } });
  });
  await page.addInitScript(() => sessionStorage.setItem('finunity-session-token', 'synthetic-browser-test'));
  await page.goto('http://127.0.0.1:5173/app');
  await page.locator('.portfolio-hero strong').first().waitFor();
  const palette = await page.locator('.side-nav nav a').first().evaluate(el => ({ text: getComputedStyle(el).color, background: getComputedStyle(el.closest('.side-nav')).backgroundColor }));
  assert.notEqual(palette.text, palette.background);
  assert.equal(await page.locator('.portfolio-hero strong').first().evaluate(el => getComputedStyle(el).fontSize), '36px');
  assert.ok(await page.locator('.bucket-track').first().evaluate(el => el.getBoundingClientRect().width) > 100, 'Allocation bars must stay readable');
  await page.screenshot({ path: new URL('desktop.png', output).pathname.replace(/^\/(\w:)/, '$1'), fullPage: true });
  await page.goto('http://127.0.0.1:5173/app/holdings');
  await page.getByRole('button', { name: '导入持仓', exact: true }).first().click();
  await page.getByRole('button', { name: '表格', exact: true }).click();
  await page.locator('input[type=file]').setInputFiles(fileURLToPath(new URL('./fixtures/holdings.xlsx', import.meta.url)));
  await page.locator('.import-dialog').getByRole('button', { name: '导入持仓', exact: true }).click();
  await page.locator('.parsed-row').waitFor();
  assert.equal(await page.locator('.parsed-row input').first().inputValue(), '工作簿测试');
  assert.match(await page.locator('.import-row-meta').innerText(), /000001/);
  await page.getByRole('button', { name: '文本', exact: true }).click();
  await page.locator('textarea').fill('名称,数量,成本价,现价,币种,代码,类型\n测试资产,0.0000001,0,0,CNY,000001,stock');
  await page.locator('.import-dialog').getByRole('button', { name: '导入持仓', exact: true }).click();
  await page.locator('.parsed-row').waitFor();
  assert.equal(await page.locator('.parsed-row small').count(), 0, 'Text imports must not invent OCR confidence');
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  assert.equal(await page.locator('.dialog').evaluate(el => el.scrollWidth <= el.clientWidth), true, 'Mobile dialog must not overflow');
  await page.screenshot({ path: new URL('mobile-import.png', output).pathname.replace(/^\/(\w:)/, '$1'), fullPage: true });
  await page.getByRole('button', { name: '确认导入持仓', exact: true }).click();
  await page.getByText('尚未确认保存结果', { exact: false }).waitFor();
  assert.equal(await page.locator('.parsed-row input').first().isDisabled(), true);
  await page.getByRole('button', { name: '确认导入持仓', exact: true }).click();
  await page.getByText('数据已保存，但页面刷新失败。请刷新页面，不要重复新增。', { exact: true }).waitFor();
  assert.equal(await page.locator('.dialog').count(), 0);
  assert.equal(writes.length, 2);
  assert.deepEqual(writes[0], writes[1], 'Network retry must preserve payload and idempotency key');
  assert.equal(writes[0].rows?.[0]?.quantity, 0.0000001, 'Commit payload must keep decimal quantities without precision loss');
  assert.deepEqual(errors, []);
  console.log('Browser audit passed: desktop contrast, typography, mobile import, uncertain retry, refresh failure.');
} finally { await browser.close(); }
