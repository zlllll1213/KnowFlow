import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ensureCsrfToken } from './csrf'

describe('ensureCsrfToken', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('reuses one inflight csrf request across concurrent callers', async () => {
    let cookie = ''
    vi.stubGlobal('document', {
      get cookie() {
        return cookie
      },
      set cookie(value: string) {
        cookie = value
      },
    })
    const fetchMock = vi.fn(async () => {
      cookie = 'XSRF-TOKEN=csrf-token'
      return new Response(null, { status: 200 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const [first, second] = await Promise.all([
      ensureCsrfToken('http://localhost:8081'),
      ensureCsrfToken('http://localhost:8081'),
    ])

    expect(first).toBe('csrf-token')
    expect(second).toBe('csrf-token')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
