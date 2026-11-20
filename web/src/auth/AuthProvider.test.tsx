import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../lib/api'
import type { Session } from '../lib/types'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const sessionKey = 'frameless.web.session.v1'
const session: Session = {
  token: 'stored-token',
  expires_at: '2099-11-30T00:00:00Z',
  user: {
    id: 'user-1',
    email: 'reader@example.com',
    username: 'reader',
    is_developer: false,
  },
}

function SessionProbe() {
  const auth = useAuth()
  return <output aria-label="auth-state">{auth.loading ? 'restoring' : auth.session?.user.id ?? 'guest'}</output>
}

function renderProvider() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider><SessionProbe /></AuthProvider>
    </QueryClientProvider>,
  )
}

describe('AuthProvider session restoration', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('keeps an unexpired local session through a temporary network failure', async () => {
    localStorage.setItem(sessionKey, JSON.stringify(session))
    vi.spyOn(api, 'me').mockRejectedValueOnce(new Error('offline'))

    renderProvider()

    await waitFor(() => expect(screen.getByLabelText('auth-state')).toHaveTextContent('user-1'))
    expect(localStorage.getItem(sessionKey)).not.toBeNull()
  })

  it('clears a server-rejected session but does not send an expired local token', async () => {
    localStorage.setItem(sessionKey, JSON.stringify(session))
    const me = vi.spyOn(api, 'me').mockRejectedValueOnce(
      new ApiError('expired', 401, 'AUTH_REQUIRED'),
    )
    const first = renderProvider()

    await waitFor(() => expect(screen.getByLabelText('auth-state')).toHaveTextContent('guest'))
    expect(localStorage.getItem(sessionKey)).toBeNull()
    first.unmount()

    localStorage.setItem(sessionKey, JSON.stringify({ ...session, expires_at: '2020-01-01T00:00:00Z' }))
    renderProvider()

    expect(screen.getByLabelText('auth-state')).toHaveTextContent('guest')
    expect(me).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(sessionKey)).toBeNull()
  })
})
