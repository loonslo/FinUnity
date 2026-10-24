# Planner prototype archive record

Date: 2026-10-24

The former `FinUnityPrototype` working-tree snapshot is preserved in this directory as a historical implementation reference. The maintained product is the Android application at the repository root, with the API in `server/` and Web client in `web/`. The prototype is not a separate active project or deployment target.

The source was copied from a clean working tree at `9bee2b2404a4675afa31bfa214ac1d3490a8de61`. Its Git history was not imported. No prototype runtime, dependency cache, local database, credentials, or generated build output was copied. At the time of migration, the source directory and its existing remote remained available outside the monorepo. The local source directory was moved to the Windows Recycle Bin on 2026-10-25; its GitHub remote remains available for reference.

No prototype tests or runtime were re-run on 2026-10-24 because this directory is retained only for migration comparison. This record does not claim current functional parity with Android.

## Public snapshot cleanup

On 2026-10-24, the archived `frontend/vite.config.ts` was removed from the current monorepo tree at the user's request. That file only set the local Vite development server to `127.0.0.1:5174` and proxied `/api` to `127.0.0.1:8011`; it contained no credentials. The TypeScript node-project include and this directory's README were updated to reflect that the archived snapshot no longer supplies those Vite development settings. The separate `FinUnityPrototype` working directory was left untouched. This removes the file from the current tree; prior public Git commits still retain its historical contents.

No build, tests, deployment, or production configuration was run or changed for this cleanup.
