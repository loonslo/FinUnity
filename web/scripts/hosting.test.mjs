import { test } from 'node:test'
import assert from 'node:assert/strict'
import worker from './hosting-worker.mjs'

function assets() {
  const calls = []
  return { calls, env: { ASSETS: { async fetch(request) {
    const path = new URL(request.url).pathname
    calls.push([path, request.method])
    return ['/index.html', '/home.html', '/en/index.html', '/about/index.html', '/en/about/index.html'].includes(path)
      ? new Response(request.method === 'HEAD' ? null : 'app', { status: 200 })
      : new Response('missing', { status: 404 })
  } } } }
}

test('declared application routes support GET and HEAD reloads', async () => {
  for (const path of ['/login', '/login/', '/register', '/demo', '/demo/', '/demo/holdings', '/demo/flows/', '/app/', '/app/holdings', '/app/flows/']) {
    for (const method of ['GET', 'HEAD']) {
      const { env, calls } = assets()
      const result = await worker.fetch(new Request(`https://finunity.example${path}`, { method }), env)
      assert.equal(result.status, 200)
      assert.deepEqual(calls.at(-1), ['/index.html', method])
    }
  }
})

test('missing crawl files, assets and unknown application paths remain 404', async () => {
  for (const path of ['/robots.txt', '/sitemap.xml', '/assets/missing.js', '/unknown', '/app/unknown']) {
    const { env, calls } = assets()
    assert.equal((await worker.fetch(new Request(`https://finunity.example${path}`), env)).status, 404)
    assert.equal(calls.length, 1)
  }
})

test('POST requests never fall back to application HTML', async () => {
  const { env, calls } = assets()
  assert.equal((await worker.fetch(new Request('https://finunity.example/app', { method: 'POST' }), env)).status, 404)
  assert.equal(calls.length, 1)
})


test('home serves the product shell; legacy products redirect to the same language about page', async () => {
  for (const [path, file] of [['/', '/home.html'], ['/en/', '/en/index.html'], ['/about/', '/about/index.html'], ['/en/about/', '/en/about/index.html']]) {
    for (const method of ['GET', 'HEAD']) {
      const { env, calls } = assets()
      const result = await worker.fetch(new Request(`https://finunity.example${path}`, { method }), env)
      assert.equal(result.status, 200)
      assert.deepEqual(calls, [[file, method]])
      if (method === 'HEAD') assert.equal(await result.text(), '')
    }
  }
  for (const [path, target] of [['/product', '/about/'], ['/product/', '/about/'], ['/en/product', '/en/about/'], ['/en/product/', '/en/about/'], ['/en', '/en/']]) {
    const { env, calls } = assets()
    const result = await worker.fetch(new Request(`https://finunity.example${path}`, { method: 'HEAD' }), env)
    assert.equal(result.status, 308)
    assert.equal(result.headers.get('Location'), target)
    assert.equal(calls.length, 0)
  }
})
