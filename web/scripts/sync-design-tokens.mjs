import { copyFile } from 'node:fs/promises'
await copyFile(new URL('../src/finunity-color-tokens.css', import.meta.url), new URL('../public/design-tokens.css', import.meta.url))
