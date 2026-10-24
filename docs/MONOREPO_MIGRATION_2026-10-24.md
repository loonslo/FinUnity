# FinUnity monorepo consolidation

Date: 2026-10-24

## Target structure

The public `loonslo/FinUnity` repository is the single monorepo. Android remains the Gradle root project; `server/` contains the FastAPI service; `web/` contains the React/Vite client; `planner-prototype-reference/` preserves the old planner source for migration comparison and is not an active runtime.

These modules keep separate dependencies, builds, configurations, and deployment targets. Web and Android continue to call the Server API. This change does not combine them into one process or change live Vercel/server deployments.

## Import provenance

The module directories are working-tree snapshots so current uncommitted work is carried forward. Original sibling directories remain untouched for rollback. Source Git histories were not imported into the monorepo; each source directory and remote retains its history separately.

| Source | Source HEAD at import | Working-tree state included |
|---|---|---|
| FinUnity Android | `1e8260e79acebd1965084d188ee01c997052c1d6` | Existing local changes remain on this branch, including planner integration and the earlier handoff document. |
| FinUnityServer | `e69f09f18f85cb6516093e739282adba73d1284a` | Current tracked and non-ignored source changes; excludes `tmp/` test databases and logs. |
| FinUintyWeb | `2716ca7115d29916db85fecd86f6261acc50e996` | Current tracked changes, deletions, and non-ignored new files. |
| FinUnityPrototype | `9bee2b2404a4675afa31bfa214ac1d3490a8de61` | Clean source snapshot; marked reference-only. |

The Android root already contained local goal-planner integration work and it remains on this review branch. No nested `.git` directories were copied. Ignored runtime files, environment files, SQLite databases, temporary files, dependency caches, and build outputs are excluded.

## Deployment follow-up

- Android continues to build from the repository root.
- Server deployment commands run from `server/`; Docker Compose remains module-local.
- Web build commands run from `web/`; the Vercel project's Root Directory must be changed to `web` when deployment is intentionally moved to this monorepo.
- The Server's browser acceptance test now loads `web/dist` from the monorepo. It requires a Web production build first.
- The prior source repositories are not deleted or archived by this code change. Keep them as rollback copies until the monorepo PR and deployment roots have been verified.

## Validation

Validated locally on 2026-10-24:

- Android: `:app:testDebugUnitTest` passed (154 tests), `:app:assembleDebug` passed, and `:app:compileDebugAndroidTestKotlin` passed. Device-only instrumentation tests were not run because `adb` is unavailable.
- Server: `uv run --extra dev pytest -q --basetemp=.pytest-public-launch-monorepo` passed (140 tests, one Starlette/httpx deprecation warning); `uv run --extra dev ruff check .` passed. The default pytest temp location was inaccessible to this account, so the successful run uses an ignored temp folder inside the module.
- Web: `npm test` passed (44 tests), `npm run lint` passed, and `npm run build` passed. Production build output was ignored and not staged.
- Server/Web integration: after building Web, the disposable Server browser acceptance app served `/` and a client-side fallback route with HTTP 200; HTML had the expected root element and the Content-Security-Policy header was present. The local app was stopped after the check.
- Dependency audit: `npm audit` reports two high severity advisories in `vite` and `source-map-js`. Dependency upgrades were not applied during this consolidation.

Live hosting configuration is not changed by the branch. Vercel's Root Directory still needs to be set to `web/` when the Web deployment is switched to this repository.

Dependency audit follow-up (2026-10-24): the initial Vite and `source-map-js` findings were fixed in the Web module; the current audit reports zero vulnerabilities. Details and verification are recorded in `web/SECURITY_DEPENDENCY_REMEDIATION_2026-10-24.md`.
