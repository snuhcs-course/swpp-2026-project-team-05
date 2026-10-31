import { createContext } from 'react'
import type { Session } from '../lib/types'

export interface AuthValue {
  session: Session | null
  loading: boolean
  login: (identifier: string, password: string) => Promise<void>
  register: (email: string, username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthValue | null>(null)
