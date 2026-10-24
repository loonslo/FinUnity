import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const IMMUTABLE = 'public, max-age=31536000, immutable'

// 与 Vercel routes 的合并规则一致：按顺序匹配，continue 的路由继续往下，靠后的同名响应头覆盖靠前的；
// handle: filesystem 命中真实文件就停止，没命中才会走到最后的 404 路由。
function cacheControl(routes, path, files) {
  let value
  for (const route of routes) {
    if (route.handle === 'filesystem') {
      if (files.has(path)) break
      continue
    }
    if (!route.src || !new RegExp(`^${route.src}$`).test(path)) continue
    if (route.headers?.['Cache-Control']) value = route.headers['Cache-Control']
    if (!route.continue && (route.dest || route.status)) break
  }
  return value
}

const hashed = [
  '/assets/index-CodEnkx4.js',
  '/assets/charts-BEj-mqs5.js',
  '/assets/rolldown-runtime-hePW80VL.js',
  '/assets/index-C0qyEjPH.css',
]

for (const file of ['vercel.json', 'deploy/vercel.baikai.json']) {
  test(`${file}: hashed build assets are immutable, documents and APIs are not`, async () => {
    const { routes } = JSON.parse(await readFile(file, 'utf8'))
    const files = new Set([...hashed, '/landing.css', '/index.html'])
    for (const path of hashed) assert.equal(cacheControl(routes, path, files), IMMUTABLE, path)
    // 应用壳、公共页面和没有哈希的静态文件必须每次校验，否则发布后用户会卡在旧版本
    for (const path of ['/app', '/app/holdings', '/login', '/landing.css', '/privacy/']) {
      assert.equal(cacheControl(routes, path, files), 'no-cache', path)
    }
    assert.equal(cacheControl(routes, '/api/v1/me', files), 'private, no-store')
  })

  test(`${file}: a missing hashed asset is never cached as immutable`, async () => {
    const { routes } = JSON.parse(await readFile(file, 'utf8'))
    assert.equal(cacheControl(routes, '/assets/index-ZZZZZZZZ.js', new Set()), 'no-cache')
  })
}
