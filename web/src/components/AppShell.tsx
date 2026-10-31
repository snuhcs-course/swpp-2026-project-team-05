import { LogOut, Menu, UserRound, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { cn } from '../lib/utils'
import { Button } from './ui/Button'

export function AppShell({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-screen bg-[var(--paper)] text-[var(--ink)]">
      <header className="sticky top-0 z-40 border-b border-black bg-[var(--paper)]/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-[1440px] items-center px-5 sm:px-8">
          <Link to="/" className="font-serif text-[22px] font-black tracking-[-0.045em]">
            FrameLESS
          </Link>
          <nav className="ml-12 hidden h-full items-center gap-8 md:flex" aria-label="주요 메뉴">
            <NavLink to="/?category=POLITICS" className={({ isActive }) => cn('nav-link', isActive && 'text-black')}>정치</NavLink>
            <NavLink to="/?category=SOCIETY" className={({ isActive }) => cn('nav-link', isActive && 'text-black')}>사회</NavLink>
          </nav>
          <div className="ml-auto hidden items-center gap-3 text-sm text-zinc-600 md:flex">
            <UserRound size={16} aria-hidden />
            <span>{session?.user.username}</span>
            {session?.user.is_developer && <span className="border border-black px-2 py-0.5 text-[11px] font-bold text-black">ADMIN</span>}
            <Button variant="ghost" size="small" onClick={() => void logout()}>
              <LogOut size={15} aria-hidden /> 로그아웃
            </Button>
          </div>
          <button
            className="ml-auto grid size-10 place-items-center md:hidden"
            onClick={() => setMenuOpen((open) => !open)}
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X /> : <Menu />}
          </button>
        </div>
        {menuOpen && (
          <div className="border-t border-zinc-300 px-5 py-4 md:hidden">
            <div className="flex flex-col gap-3 text-sm font-semibold">
              <Link to="/?category=POLITICS" onClick={() => setMenuOpen(false)}>정치</Link>
              <Link to="/?category=SOCIETY" onClick={() => setMenuOpen(false)}>사회</Link>
              <button className="mt-2 flex items-center gap-2 border-t border-zinc-300 pt-3 text-left" onClick={() => void logout()}>
                <LogOut size={15} /> 로그아웃
              </button>
            </div>
          </div>
        )}
      </header>
      {children}
      <footer className="mt-20 border-t border-black px-5 py-8 text-sm text-zinc-600 sm:px-8">
        <div className="mx-auto flex max-w-[1440px] flex-col justify-between gap-2 sm:flex-row">
          <strong className="font-serif text-base text-black">FrameLESS</strong>
          <span>언론사의 옳고 그름을 판정하지 않습니다. 보도의 표현과 관점 차이를 함께 보여줍니다.</span>
        </div>
      </footer>
    </div>
  )
}
