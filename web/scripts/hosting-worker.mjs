const applicationRoutes = new Set([
  '/demo', '/demo/', '/demo/holdings', '/demo/holdings/', '/demo/flows', '/demo/flows/', '/login', '/login/', '/register', '/register/', '/app', '/app/',
  '/app/holdings', '/app/holdings/', '/app/flows', '/app/flows/',
])

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    if (['GET', 'HEAD'].includes(request.method)) {
      const homes = { '/': '/home.html', '/en/': '/en/index.html', '/about/': '/about/index.html', '/en/about/': '/en/about/index.html' }
      const redirects = { '/about': '/about/', '/en/about': '/en/about/', '/product': '/about/', '/product/': '/about/', '/en/product': '/en/about/', '/en/product/': '/en/about/', '/en': '/en/' }
      if (redirects[url.pathname]) return new Response(null, { status: 308, headers: { Location: redirects[url.pathname] } })
      if (homes[url.pathname]) return env.ASSETS.fetch(new Request(new URL(homes[url.pathname], url), request))
    }
    const response = await env.ASSETS.fetch(request)
    if (response.status !== 404) return response
    if (!['GET', 'HEAD'].includes(request.method) || !applicationRoutes.has(url.pathname)) {
      return response
    }
    return env.ASSETS.fetch(new Request(new URL('/index.html', url), request))
  },
}
