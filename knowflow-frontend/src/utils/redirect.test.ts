import { describe, expect, it } from 'vitest'
import { safeInternalRedirect } from './redirect'

describe('safeInternalRedirect', () => {
  it('allows normal internal paths', () => {
    expect(safeInternalRedirect('/dashboard')).toBe('/dashboard')
    expect(safeInternalRedirect('/kb/1?tab=docs')).toBe('/kb/1?tab=docs')
  })

  it('rejects protocol-relative URLs', () => {
    expect(safeInternalRedirect('//evil.example/phish')).toBe('/dashboard')
  })

  it('rejects external and missing values', () => {
    expect(safeInternalRedirect('https://evil.example')).toBe('/dashboard')
    expect(safeInternalRedirect(undefined)).toBe('/dashboard')
  })
})
