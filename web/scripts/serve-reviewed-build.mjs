// Local acceptance server: evaluates the checked-in Vercel routing rules against dist/.
// API calls are deliberately never proxied. Browser acceptance uses synthetic responses.
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { resolve, sep, extname } from 'node:path'

const root = resolve('dist')
const { routes } = JSON.parse(await readFile('vercel.json', 'utf8'))
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff': 'font/woff', '.woff2': 'font/woff2', '.xml': 'application/xml', '.txt': 'text/plain; charset=utf-8' }
async function asset(path) {
  const target = resolve(root, `.${decodeURIComponent(path)}`)
  if (!target.startsWith(root + sep)) return null
  try { return (await stat(target)).isFile() ? target : null } catch { return null }
}
const server = createServer(async (request, response) => {
  try {
    const path = new URL(request.url, 'http://localhost').pathname
    const method = request.method || 'GET'
    let target = null
    for (const route of routes) {
      if (route.handle === 'filesystem') {
        target = await asset(path)
        if (target) break
      }
      if (!route.src || !new RegExp(`^${route.src}$`).test(path) || (route.methods && !route.methods.includes(method))) continue
      for (const [name, value] of Object.entries(route.headers || {})) response.setHeader(name, value)
      if (route.continue) continue
      if (route.status) { response.writeHead(route.status); response.end(); return }
      if (route.dest) {
        if (route.dest.startsWith('https:')) { response.writeHead(502); response.end('API disabled in local acceptance'); return }
        target = await asset(route.dest)
        break
      }
    }
    if (!target) { response.writeHead(404); response.end(); return }
    response.setHeader('Content-Type', types[extname(target)] || 'application/octet-stream')
    response.writeHead(200)
    response.end(method === 'HEAD' ? undefined : await readFile(target))
  } catch { response.writeHead(400); response.end() }
})
const port = Number(process.argv[2] || 4173)
server.listen(port, '127.0.0.1', () => console.log(`Reviewed build: http://127.0.0.1:${port}`))
