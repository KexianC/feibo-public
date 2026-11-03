export type GeoAuthMode = 'host' | 'session'

const configuredApiBase = import.meta.env.VITE_GEO_API_BASE?.trim()

export const GEO_API_BASE = (configuredApiBase || '/geo-agent-api').replace(/\/$/, '')
export const GEO_AUTH_MODE = (import.meta.env.VITE_GEO_AUTH_MODE || 'host') as GeoAuthMode
export const USE_HOST_AUTH = GEO_AUTH_MODE === 'host'

export interface HostIdentity {
  userId: string | number | null
  username: string
  displayName: string
  role: string
}

export interface GeoHostBridge {
  getAccessToken: () => string
  getClientFingerprint: () => string
  getIdentity: () => HostIdentity
  getAbortSignal: () => AbortSignal
  refreshAccessToken: () => Promise<boolean>
  onUnauthorized: () => boolean
}

export type HostAuthRecovery = 'refreshed' | 'failed' | 'detached'

let hostBridge: GeoHostBridge | null = null
let hostBridgeWasAttached = false

export function setGeoHostBridge(bridge: GeoHostBridge) {
  hostBridgeWasAttached = true
  hostBridge = bridge
}

export function clearGeoHostBridge(bridge?: GeoHostBridge) {
  if (!bridge || hostBridge === bridge) {
    hostBridge = null
  }
}

export function getHostAccessToken() {
  return hostBridge?.getAccessToken() || localStorage.getItem('access-token') || ''
}

export function getHostAbortSignal() {
  return hostBridge?.getAbortSignal()
}

export async function tryRefreshHostAccessToken(): Promise<HostAuthRecovery> {
  const bridge = hostBridge
  if (!bridge) return hostBridgeWasAttached ? 'detached' : 'failed'
  const accessTokenBeforeRefresh = bridge.getAccessToken()

  try {
    const refreshed = await bridge.refreshAccessToken()
    if (hostBridge !== bridge) return 'detached'
    const currentAccessToken = bridge.getAccessToken()
    return refreshed || Boolean(currentAccessToken && currentAccessToken !== accessTokenBeforeRefresh)
      ? 'refreshed'
      : 'failed'
  } catch {
    return hostBridge === bridge ? 'failed' : 'detached'
  }
}

export function getClientFingerprint() {
  const bridgedFingerprint = hostBridge?.getClientFingerprint()
  if (bridgedFingerprint) {
    return bridgedFingerprint
  }

  let fingerprint = localStorage.getItem('client-fingerprint')
  if (!fingerprint) {
    fingerprint = `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`
    localStorage.setItem('client-fingerprint', fingerprint)
  }
  return fingerprint
}

function parseStoredUser() {
  const candidates = [
    localStorage.getItem('user-info'),
    localStorage.getItem('userInfo'),
    localStorage.getItem('user-auth'),
  ]

  for (const raw of candidates) {
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw)
      return parsed?.user || parsed
    } catch {
      // Try the next host storage format.
    }
  }
  return {}
}

export function readHostIdentity(): HostIdentity {
  if (hostBridge) {
    return hostBridge.getIdentity()
  }

  const user = parseStoredUser()
  const roleNames = Array.isArray(user?.roles)
    ? user.roles.map((role: any) => String(role?.name || role || '').toUpperCase())
    : []
  const role = roleNames.some((name: string) => name.includes('ADMIN')) ? 'ADMIN' : 'USER'
  const username = String(user?.phoneNumber || user?.username || user?.nickName || '')

  return {
    userId: user?.userId || null,
    username,
    displayName: String(user?.displayName || user?.nickName || username || '飞驳用户'),
    role,
  }
}

export function redirectToHostLogin() {
  if (hostBridge) {
    return hostBridge.onUnauthorized()
  }

  if (USE_HOST_AUTH && hostBridgeWasAttached) {
    return false
  }

  clearHostAuthentication()
  const target = window.top || window
  target.location.replace('/login')
  return true
}

export function clearHostAuthentication() {
  for (const key of ['access-token', 'refresh-token', 'user-info', 'userInfo', 'user-auth']) {
    localStorage.removeItem(key)
  }
}
