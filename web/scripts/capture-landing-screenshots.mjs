/*
 * Re-take the product screenshots used on the public home pages from the real demo workspace.
 *
 *   FINUNITY_PLAYWRIGHT=<path to playwright> node scripts/capture-landing-screenshots.mjs <base url> <output dir>
 *
 * Writes overview.zh.png and overview.en.png (total + allocation only, light theme, example data). Re-run it
 * whenever the overview layout changes, then optimise the PNGs and copy them to public/demo/
 * (see docs/DEMO_MODE_2026-10-14.md).
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.FINUNITY_PLAYWRIGHT || 'playwright')
const base = (process.argv[2] || 'http://127.0.0.1:5173').replace(/\/$/, '')
const output = process.argv[3] || 'landing-screenshots'
mkdirSync(output, { recursive: true })

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
try {
  for (const [name, locale] of [['zh', 'zh-CN'], ['en', 'en-US']]) {
    const context = await browser.newContext({ viewport: { width: 1280, height: 980 }, locale, colorScheme: 'light', reducedMotion: 'reduce' })
    const page = await context.newPage()
    await page.addInitScript((value) => {
      localStorage.setItem('finunity-locale', value)
      localStorage.setItem('finunity-appearance', 'light')
    }, locale)
    await page.goto(`${base}/`)
    await page.locator('.portfolio-hero strong').first().waitFor()
    await page.locator('.allocation-list b').first().waitFor()
    await page.addStyleTag({ content: '.demo-tour, .quick-panel { display: none !important; }' })
    await page.evaluate(() => document.fonts.ready)
    const hero = await page.locator('.portfolio-hero').boundingBox()
    const bars = await page.locator('.allocation-panel').boundingBox()
    await page.screenshot({ path: join(output, `overview.${name}.png`), clip: {
      x: hero.x, y: hero.y, width: hero.width, height: bars.y + bars.height - hero.y,
    } })
    await context.close()
  }
} finally {
  await browser.close()
}
console.log(`Screenshots written to ${output}`)
