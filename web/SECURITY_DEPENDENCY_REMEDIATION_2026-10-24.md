# Web dependency security remediation

Date: 2026-10-24

## Changes

- Raised the Web build tool from Vite 8.2.2 to Vite 8.3.5. The first fixed 8.2 release is 8.2.4; the installed 8.3.5 release is above that patched version and also includes the patched `launch-editor` dependency.
- Updated the transitive `source-map-js` dependency from 1.2.1 to 1.2.2, the version patched for the indexed-source-map offset denial of service.
- Updated `package.json` and `package-lock.json`; npm changed seven packages in the Vite dependency chain.

## Verification

Environment: Windows, Node v22.22.3, npm 12.0.2.

- `npm audit`: 0 vulnerabilities.
- `npm ls vite source-map-js --all`: Vite 8.3.5 and source-map-js 1.2.2 resolved.
- `npm test`: 44 passed.
- `npm run lint`: passed.
- `npm run build`: passed with Vite 8.3.5.

No Vercel setting or live deployment was changed.

## Security references

- [Vite release 8.2.4](https://github.com/vitejs/vite/releases/tag/v8.2.4)
- [Vite launch-editor advisory GHSA-587h-7wmj-h5pm](https://github.com/advisories/GHSA-587h-7wmj-h5pm)
- [source-map-js advisory GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)
- [source-map-js release 1.2.2](https://github.com/7rulnik/source-map-js/releases/tag/v1.2.2)
