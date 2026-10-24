// Explicit workspace maintenance step; normal Web builds do not require the Android repo.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const source = fileURLToPath(new URL('../../FinUnity/PRIVACY_POLICY.md', import.meta.url));
const output = fileURLToPath(new URL('../public/android-privacy/index.html', import.meta.url));
const escape = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const markdown = await readFile(source, 'utf8');
const blocks = markdown.trim().split(/\r?\n\r?\n/).map(block => {
  if (block.startsWith('# ')) return `<h1>${escape(block.slice(2))}</h1>`;
  if (block.startsWith('## ')) return `<h2>${escape(block.slice(3))}</h2>`;
  const text = escape(block).replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');
  return `<p>${text}</p>`;
});
await mkdir(fileURLToPath(new URL('../public/android-privacy/', import.meta.url)), { recursive: true });
// Keep the manually translated public/android-privacy-en.html aligned when the source policy changes.
const metadata = '<meta name="description" content="衡仓 Android 客户端的资料处理、权限与隐私说明。">'
  + '<link rel="canonical" href="https://finunity.baikai.site/android-privacy">'
  + '<link rel="alternate" hreflang="zh-CN" href="https://finunity.baikai.site/android-privacy">'
  + '<link rel="alternate" hreflang="en" href="https://finunity.baikai.site/android-privacy-en.html">'
  + '<link rel="alternate" hreflang="x-default" href="https://finunity.baikai.site/android-privacy">';
await writeFile(output, '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>衡仓 Android 隐私政策</title><link rel="stylesheet" href="/policy.css">' + metadata + '</head><body><article><nav class="policy-nav"><a href="/">返回 FinUnity</a><a href="/android-privacy-en.html" lang="en">English</a></nav>' + blocks.join('') + '</article></body></html>\n');
console.log('Android public policy synchronized from PRIVACY_POLICY.md');
