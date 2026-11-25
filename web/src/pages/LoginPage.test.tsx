import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../lib/api'
import { LoginPage } from './LoginPage'

const auth = vi.hoisted(() => ({ session: null, login: vi.fn(), register: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))

function show() {
  return render(<MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/saved' } }]}>
    <Routes><Route path="/login" element={<LoginPage />} />
      <Route path="/saved" element={<p>Saved destination</p>} /></Routes>
  </MemoryRouter>)
}
function form() { return screen.getByLabelText('Password').closest('form')! }
function fillLogin() {
  fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'reader' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'private-password' } })
}

describe('account form retry handling', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.useFakeTimers() })
  afterEach(() => { cleanup(); vi.useRealTimers() })

  it('preserves fields and origin, separates forms, and unlocks without replaying credentials', async () => {
    auth.login.mockRejectedValue(new ApiError('limited', 429, 'RATE_LIMITED', {}, 2))
    show(); fillLogin()
    await act(async () => { fireEvent.submit(form()); fireEvent.submit(form()) })
    expect(within(form()).getByRole('button')).toBeDisabled()
    fireEvent.submit(form())
    expect(auth.login).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('Password')).toHaveValue('private-password')
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(within(form()).getByRole('button')).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(within(form()).getByRole('button')).toBeDisabled()
    await act(async () => { vi.advanceTimersByTime(2000) })
    expect(within(form()).getByRole('button')).toBeEnabled()
    expect(auth.login).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('link', { name: 'Continue without signing in' }))
    expect(screen.getByText('Saved destination')).toBeInTheDocument()
  })

  it('recovers from unavailable services without clearing fields or retrying', async () => {
    auth.login.mockRejectedValue(new ApiError('Try again later.', 503, 'AUTH_UNAVAILABLE'))
    show(); fillLogin()
    await act(async () => { fireEvent.submit(form()) })
    expect(within(form()).getByRole('button')).toBeEnabled()
    expect(screen.getByRole('alert')).toHaveTextContent('Try again later.')
    expect(screen.getByLabelText('Email or username')).toHaveValue('reader')
    expect(auth.login).toHaveBeenCalledTimes(1)
  })

  it('does not replay a registration whose response was lost', async () => {
    auth.register.mockRejectedValue(new TypeError('network'))
    show()
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'reader@example.com' } })
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'reader' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'private-password' } })
    await act(async () => { fireEvent.submit(form()) })
    await act(async () => { vi.advanceTimersByTime(60_000) })
    expect(auth.register).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('Email')).toHaveValue('reader@example.com')
    expect(within(form()).getByRole('button')).toBeEnabled()
  })
})
