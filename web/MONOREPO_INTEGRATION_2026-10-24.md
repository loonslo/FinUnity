# Web monorepo integration acceptance

Date: 2026-10-24

The React/Vite client is located at `web/`, with its own `package.json`, lockfile, build, and deployment boundary. The production build produces `web/dist`, which the Server's disposable browser acceptance app serves for the integrated local smoke check.

## Local verification

- `npm ci`: completed; npm reported two high severity dependency advisories.
- `npm test`: 44 tests passed.
- `npm run lint`: passed.
- `npm run build`: passed.
- Server/Web smoke check after build: `/` and a client-side fallback route returned HTTP 200; the SPA root element and Content-Security-Policy header were present.

The initial import audit identified high severity advisories in `vite` and `source-map-js`. They were subsequently fixed by the dependency update recorded in [the security remediation](SECURITY_DEPENDENCY_REMEDIATION_2026-10-24.md); the current audit reports zero vulnerabilities. No Vercel setting or live deployment was changed; set the Vercel Root Directory to `web/` when the deployment is moved to this repository.
