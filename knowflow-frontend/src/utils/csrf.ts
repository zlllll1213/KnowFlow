const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
let inflight: Promise<string | null> | null = null

export function getCookie(name: string): string | null {
  const cookie = globalThis.document?.cookie
  if (!cookie) return null
  const pair = cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`))
  if (!pair) return null
  return decodeURIComponent(pair.slice(name.length + 1))
}

export function getCsrfToken(): string | null {
  return getCookie(CSRF_COOKIE)
}

export function csrfHeader(): Record<string, string> {
  const token = getCsrfToken()
  return token ? { [CSRF_HEADER]: token } : {}
}

export async function ensureCsrfToken(baseURL: string): Promise<string | null> {
  const existing = getCsrfToken()
  if (existing) return existing
  if (!globalThis.fetch) return null

  const normalizedBase = baseURL.replace(/\/$/, '')
  if (!inflight) {
    inflight = fetch(`${normalizedBase}/api/auth/csrf`, {
      method: 'GET',
      credentials: 'include',
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`CSRF 初始化失败: ${response.status}`)
        }
        return getCsrfToken()
      })
      .finally(() => {
        inflight = null
      })
  }
  return inflight
}
