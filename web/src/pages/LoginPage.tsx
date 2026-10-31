import { ArrowRight, Eye, Newspaper, ScanText } from 'lucide-react'
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
      setError(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-screen bg-[var(--paper)] lg:grid-cols-[minmax(0,1.2fr)_minmax(420px,0.8fr)]">
      <section className="relative flex min-h-[46vh] flex-col justify-between overflow-hidden border-b border-black p-7 lg:min-h-screen lg:border-r lg:border-b-0 lg:p-12 xl:p-16">
        <div className="relative z-10 flex items-center justify-between">
          <span className="font-serif text-2xl font-black tracking-[-0.045em]">FrameLESS</span>
          <span className="text-xs font-semibold uppercase tracking-[0.18em]">Beyond a single frame</span>
        </div>
        <div className="relative z-10 max-w-4xl py-16 lg:py-0">
          <p className="mb-5 text-sm font-bold">하나의 사건, 여러 개의 보도</p>
          <h1 className="font-serif text-[clamp(3rem,7vw,7.5rem)] font-black leading-[0.9] tracking-[-0.065em]">
            한 기사를 읽되,<br />한 시각에 머물지 않게.
          </h1>
          <p className="mt-8 max-w-2xl text-base leading-7 text-zinc-600 lg:text-lg">
            익숙한 언론사의 기사를 그대로 읽고, 두드러진 표현과 다른 언론사의 대응 보도를 같은 화면에서 비교합니다.
          </p>
        </div>
        <div className="relative z-10 grid gap-px border border-black bg-black sm:grid-cols-3">
          {[
            [Newspaper, '언론사별 기사'],
            [ScanText, '문장 단위 분석'],
            [Eye, '교차 보도 비교'],
          ].map(([Icon, label]) => (
            <div key={label as string} className="flex items-center gap-3 bg-[var(--paper)] p-4 text-sm font-semibold">
              <Icon size={18} strokeWidth={1.5} /> {label as string}
            </div>
          ))}
        </div>
        <div className="absolute -right-32 top-1/3 size-[520px] rounded-full border border-zinc-300 opacity-60" />
        <div className="absolute -right-12 top-1/3 size-[320px] rounded-full border border-zinc-300 opacity-60" />
      </section>

      <section className="flex items-center justify-center px-6 py-14 sm:px-12 lg:px-16">
        <div className="w-full max-w-md">
          <div className="mb-10">
            <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em]">Member access</p>
            <h2 className="font-serif text-4xl font-black tracking-[-0.04em]">
              {mode === 'login' ? '다시 만나 반갑습니다.' : 'FrameLESS 시작하기'}
            </h2>
            <p className="mt-3 text-sm leading-6 text-zinc-500">기사와 분석 결과는 로그인한 사용자에게만 제공됩니다.</p>
          </div>
          <div className="mb-7 grid grid-cols-2 border-b border-zinc-300 text-sm font-bold">
            <button className={mode === 'login' ? 'border-b-2 border-black py-3 text-black' : 'py-3 text-zinc-400'} onClick={() => setMode('login')}>로그인</button>
            <button className={mode === 'register' ? 'border-b-2 border-black py-3 text-black' : 'py-3 text-zinc-400'} onClick={() => setMode('register')}>회원가입</button>
          </div>
          <form className="space-y-4" onSubmit={submit}>
            {mode === 'login' ? (
              <label className="form-label">이메일 또는 사용자 이름<Input value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" required /></label>
            ) : (
              <>
                <label className="form-label">이메일<Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required /></label>
                <label className="form-label">사용자 이름<Input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" minLength={3} required /></label>
              </>
            )}
            <label className="form-label">비밀번호<Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required /></label>
            {error && <p className="border-l-2 border-red-600 pl-3 text-sm text-red-700" role="alert">{error}</p>}
            <Button className="mt-2 w-full" type="submit" disabled={submitting}>
              {submitting ? '확인 중…' : mode === 'login' ? '로그인' : '계정 만들기'} <ArrowRight size={17} />
            </Button>
          </form>
        </div>
      </section>
    </main>
  )
}
