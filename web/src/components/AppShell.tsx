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
          <nav className="ml-12 hidden h-full items-center gap-8 md:flex" aria-label="Primary navigation">
            <NavLink to="/?category=POLITICS" className={({ isActive }) => cn('nav-link', isActive && 'text-black')}>Politics</NavLink>
            <NavLink to="/?category=SOCIETY" className={({ isActive }) => cn('nav-link', isActive && 'text-black')}>Society</NavLink>
          </nav>
          <div className="ml-auto hidden items-center gap-3 text-sm text-zinc-600 md:flex">
            <UserRound size={16} aria-hidden />
            <span>{session?.user.username}</span>
            {session?.user.is_developer && <span className="border border-black px-2 py-0.5 text-[11px] font-bold text-black">ADMIN</span>}
            <Button variant="ghost" size="small" onClick={() => void logout()}>
              <LogOut size={15} aria-hidden /> Sign out
            </Button>
          </div>
          <button
            className="ml-auto grid size-10 place-items-center md:hidden"
            onClick={() => setMenuOpen((open) => !open)}
            aria-label="Open navigation"
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X /> : <Menu />}
          </button>
        </div>
        {menuOpen && (
          <div className="border-t border-zinc-300 px-5 py-4 md:hidden">
            <div className="flex flex-col gap-3 text-sm font-semibold">
              <Link to="/?category=POLITICS" onClick={() => setMenuOpen(false)}>Politics</Link>
              <Link to="/?category=SOCIETY" onClick={() => setMenuOpen(false)}>Society</Link>
              <button className="mt-2 flex items-center gap-2 border-t border-zinc-300 pt-3 text-left" onClick={() => void logout()}>
                <LogOut size={15} /> Sign out
              </button>
            </div>
          </div>
        )}
      </header>
      {children}
      <footer className="mt-16 border-t border-black px-5 py-6 sm:px-8">
        <div className="mx-auto max-w-[1440px] font-serif text-sm font-bold">© 2026 FrameLESS</div>
      </footer>
    </div>
  )
}
