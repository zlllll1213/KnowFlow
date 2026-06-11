let activeSession = false

export function getToken(): string | null {
  return activeSession ? 'cookie-session' : null
}

export function setToken(_token = 'cookie-session'): void {
  activeSession = true
}

export function removeToken(): void {
  activeSession = false
}
