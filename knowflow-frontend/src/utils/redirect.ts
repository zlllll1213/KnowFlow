export function safeInternalRedirect(value: unknown, fallback = '/dashboard'): string {
  if (typeof value !== 'string') return fallback
  return value.startsWith('/') && !value.startsWith('//') ? value : fallback
}
