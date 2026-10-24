import { copyFile, mkdir } from 'node:fs/promises'
import { join } from 'node:path'

const serverDir = join(process.cwd(), 'dist', 'server')
await mkdir(serverDir, { recursive: true })

// Keep alternate hosting and nginx aligned: only known client routes fall back.
await copyFile(join(process.cwd(), 'scripts', 'hosting-worker.mjs'), join(serverDir, 'index.js'))
