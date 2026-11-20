import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError } from '../lib/api'
import type { Session } from '../lib/types'
import { AuthContext, type AuthValue } from './auth-context'

const SESSION_KEY = 'frameless.web.session.v1'

function readSession(): Session | null {
  try {
    const value = localStorage.getItem(SESSION_KEY)
    if (!value) return null
    const session = JSON.parse(value) as Session
    const expiresAt = Date.parse(session.expires_at)
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      localStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(readSession)
  const [loading, setLoading] = useState(Boolean(session))

  const save = useCallback((next: Session | null) => {
    if (session?.user.id !== next?.user.id) queryClient.clear()
    setSession(next)
    if (next) localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    else localStorage.removeItem(SESSION_KEY)
  }, [queryClient, session?.user.id])

  useEffect(() => {
    if (!session) {
      setLoading(false)
      return
    }
    api.me(session.token)
      .then((user) => save({ ...session, user }))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) save(null)
      })
      .finally(() => setLoading(false))
    // A stored token only needs to be checked once when the app starts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const value = useMemo<AuthValue>(() => ({
    session,
    loading,
    async login(identifier, password) { save(await api.login(identifier, password)) },
    async register(email, username, password) { save(await api.register(email, username, password)) },
    async logout() {
      const token = session?.token
      save(null)
      if (token) await api.logout(token).catch(() => undefined)
    },
  }), [session, loading, save])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
