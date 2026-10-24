import assert from 'node:assert/strict'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
const base = process.argv[2] || 'http://127.0.0.1:4173'
const output = process.argv[3] || 'docs/qa-entry-2026-10-21/http.json'
const { pages } = JSON.parse(await readFile('docs/seo-pages.json', 'utf8'))
const results = []
for (const method of ['GET', 'HEAD']) {
  for (const page of pages) {
    const response = await fetch(base + page.path, { method, redirect: 'manual' })
    assert.equal(response.status, 200, page.path)
    assert.ok(!response.headers.get('X-Robots-Tag')?.includes('noindex'))
    const body = await response.text()
    if (method === 'GET') {
      assert.ok(body.includes(`<title>${page.title}</title>`), page.path)
      assert.ok(body.includes(`rel="canonical" href="${page.canonical}"`), page.path)
      assert.ok(!body.includes('content="noindex"'), page.path)
      if (['/', '/en/'].includes(page.path)) assert.ok(body.includes('type="module"'))
    } else assert.equal(body, '')
    results.push({ method, path: page.path, status: response.status })
  }
  for (const path of ['/login', '/register', '/demo', '/demo/holdings', '/demo/flows', '/app', '/app/holdings', '/app/flows']) {
    const response = await fetch(base + path, { method, redirect: 'manual' })
    assert.equal(response.status, 200); assert.equal(response.headers.get('X-Robots-Tag'), 'noindex')
    if (method === 'GET') assert.ok((await response.text()).includes('content="noindex"'))
    results.push({ method, path, status: response.status })
  }
  for (const [path, target] of [['/product', '/about/'], ['/product/', '/about/'], ['/en/product', '/en/about/'], ['/en/product/', '/en/about/'], ['/about', '/about/'], ['/en/about', '/en/about/']]) {
    const response = await fetch(base + path, { method, redirect: 'manual' })
    assert.equal(response.status, 308); assert.equal(response.headers.get('Location'), target)
    results.push({ method, path, status: response.status, location: target })
  }
}
for (const path of ['/app/missing', '/unknown-entry', '/assets/missing.js']) {
  const response = await fetch(base + path)
  assert.equal(response.status, 404)
  results.push({ method: 'GET', path, status: 404 })
}
await mkdir(new URL('../docs/qa-entry-2026-10-21/', import.meta.url), { recursive: true })
await writeFile(output, JSON.stringify({ date: '2026-10-21', base, scope: 'Local Vercel routing emulator; no production or nginx validation', checks: results, pass: true }, null, 2))
console.log(`${results.length} local build HTTP checks passed`)
