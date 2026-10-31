import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../lib/api'
import type { Session } from '../lib/types'
import { AuthContext, type AuthValue } from './auth-context'

const SESSION_KEY = 'frameless.web.session.v1'

function readSession(): Session | null {
  try {
    const value = localStorage.getItem(SESSION_KEY)
    return value ? JSON.parse(value) as Session : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession)
  const [loading, setLoading] = useState(Boolean(session))

  const save = (next: Session | null) => {
    setSession(next)
    if (next) localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    else localStorage.removeItem(SESSION_KEY)
  }

  useEffect(() => {
    if (!session) {
      setLoading(false)
      return
    }
    api.me(session.token)
      .then((user) => save({ ...session, user }))
      .catch(() => save(null))
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
  }), [session, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
