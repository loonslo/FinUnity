import { test } from 'node:test'
import assert from 'node:assert/strict'
import { languageFromSearch } from '../src/urlLanguage.ts'

test('a link can choose the interface language for the visit', () => {
  assert.equal(languageFromSearch('?lang=en'), 'en-US')
  assert.equal(languageFromSearch('?from=portfolio&lang=en'), 'en-US')
  assert.equal(languageFromSearch('?lang=zh'), 'zh-CN')
})

test('anything else leaves the stored or default language alone', () => {
  assert.equal(languageFromSearch(''), null)
  assert.equal(languageFromSearch('?from=portfolio'), null)
  assert.equal(languageFromSearch('?lang=fr'), null)
  assert.equal(languageFromSearch('?lang='), null)
  assert.equal(languageFromSearch('?lang=EN'), null)
})
