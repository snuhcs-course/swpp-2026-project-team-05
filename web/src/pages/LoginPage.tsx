import { ArrowRight } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { ApiError } from '../lib/api'

export function LoginPage() {
  const { session, login, register } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [identifier, setIdentifier] = useState('')
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (session) return <Navigate to="/" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (mode === 'login') await login(identifier, password)
      else await register(email, username, password)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-screen bg-[var(--paper)] lg:grid-cols-[minmax(0,1.2fr)_minmax(420px,0.8fr)]">
      <section className="relative flex min-h-[40vh] flex-col overflow-hidden border-b border-black p-7 lg:min-h-screen lg:border-r lg:border-b-0 lg:p-12 xl:p-16">
        <div className="relative z-10">
          <span className="font-serif text-2xl font-black tracking-[-0.045em]">FrameLESS</span>
        </div>
        <div className="relative z-10 my-auto max-w-4xl py-14 lg:py-0">
          <h1 className="font-serif text-[clamp(3rem,7vw,7.5rem)] font-black leading-[0.9] tracking-[-0.065em]">
            Read beyond<br />the frame.
          </h1>
          <p className="mt-7 text-sm font-semibold uppercase tracking-[0.16em] text-zinc-500">One story. More than one frame.</p>
        </div>
        <div className="absolute -right-32 top-1/3 size-[520px] rounded-full border border-zinc-300 opacity-60" />
        <div className="absolute -right-12 top-1/3 size-[320px] rounded-full border border-zinc-300 opacity-60" />
      </section>

      <section className="flex items-center justify-center px-6 py-14 sm:px-12 lg:px-16">
        <div className="w-full max-w-md">
          <div className="mb-8">
            <h2 className="font-serif text-4xl font-black tracking-[-0.04em]">
              {mode === 'login' ? 'Welcome back.' : 'Join FrameLESS.'}
            </h2>
          </div>
          <div className="mb-7 grid grid-cols-2 border-b border-zinc-300 text-sm font-bold">
            <button className={mode === 'login' ? 'border-b-2 border-black py-3 text-black' : 'py-3 text-zinc-400'} onClick={() => setMode('login')}>Sign in</button>
            <button className={mode === 'register' ? 'border-b-2 border-black py-3 text-black' : 'py-3 text-zinc-400'} onClick={() => setMode('register')}>Create account</button>
          </div>
          <form className="space-y-4" onSubmit={submit}>
            {mode === 'login' ? (
              <label className="form-label">Email or username<Input value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" required /></label>
            ) : (
              <>
                <label className="form-label">Email<Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required /></label>
                <label className="form-label">Username<Input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" minLength={3} required /></label>
              </>
            )}
            <label className="form-label">Password<Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required /></label>
            {error && <p className="border-l-2 border-red-600 pl-3 text-sm text-red-700" role="alert">{error}</p>}
            <Button className="mt-2 w-full" type="submit" disabled={submitting}>
              {submitting ? 'Checking…' : mode === 'login' ? 'Sign in' : 'Create account'} <ArrowRight size={17} />
            </Button>
          </form>
        </div>
      </section>
    </main>
  )
}
