import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import test from 'node:test'

test('public page manifest, sitemap and page metadata agree', async () => {
  const root = process.cwd()
  const { pages } = JSON.parse(await readFile(join(root, 'docs/seo-pages.json'), 'utf8'))
  const sitemap = await readFile(join(root, 'public/sitemap.xml'), 'utf8')
  const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match => match[1])
  assert.deepEqual(urls.sort(), pages.map(page => page.canonical).sort())
  for (const page of pages) {
    const html = await readFile(join(root, 'public', page.source), 'utf8')
    assert.ok(html.includes(`<title>${page.title}</title>`))
    assert.ok(html.includes(`rel="canonical" href="${page.canonical}"`))
    assert.ok(html.includes('<meta name="description"'))
    assert.ok(!html.includes('content="noindex"'))
    for (const target of Object.values(page.hreflang)) {
      assert.ok(urls.includes(target))
      assert.ok(Object.values(pages.find(other => other.canonical === target).hreflang).includes(page.canonical))
    }
  }
  assert.ok((await readFile(join(root, 'index.html'), 'utf8')).includes('content="noindex"'))
})


test('public product and guide pages are readable without application JavaScript', async () => {
  const { pages } = JSON.parse(await readFile('docs/seo-pages.json', 'utf8'))
  for (const page of pages.filter(page => ['/about/', '/en/about/'].includes(page.path) || page.path.includes('/guides/'))) {
    const html = await readFile(join('public', page.source), 'utf8')
    assert.equal((html.match(/<h1[ >]/g) || []).length, 1)
    assert.ok(!/<script|<form|id="root"/.test(html))
    assert.ok(html.includes('href="/register"'))
    assert.ok(html.includes(page.language === "en" ? 'href="/en/"' : 'href="/"'))
    assert.ok(html.includes('href="/content.css"'))
  }
})


test('Vercel serves indexable public homes while authentication and preview routes stay noindex', async () => {
  for (const file of ['vercel.json', 'deploy/vercel.baikai.json']) {
  const { routes } = JSON.parse(await readFile(file, 'utf8'))
  const match = (path) => {
    const matched = []
    for (const route of routes) {
      if (!route.src || !new RegExp(`^${route.src}$`).test(path)) continue
      matched.push(route)
      if (!route.continue && (route.dest || route.status)) break
    }
    return matched
  }
  for (const [path, dest] of [['/', '/home.html'], ['/en/', '/en/index.html'], ['/about/', '/about/index.html'], ['/en/about/', '/en/about/index.html']]) {
    assert.equal(match(path).find(route => route.dest)?.dest, dest)
    assert.ok(!match(path).some(route => route.headers?.['X-Robots-Tag']?.includes('noindex')))
  }
  for (const path of ['/login', '/login/', '/register', '/demo', '/demo/holdings', '/demo/flows', '/app', '/app/holdings', '/app/flows/']) {
    assert.ok(match(path).some(route => route.headers?.['X-Robots-Tag'] === 'noindex'))
    assert.equal(match(path).find(route => route.dest)?.dest, '/index.html')
  }
  for (const [path, dest] of [['/product', '/about/'], ['/product/', '/about/'], ['/en/product', '/en/about/'], ['/en/product/', '/en/about/']]) {
    const redirect = match(path).find(route => route.status === 308)
    assert.equal(redirect?.headers.Location, dest)
  }
  }
})
