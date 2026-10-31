import { LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import { ArticlePage } from './pages/ArticlePage'
import { FeedPage } from './pages/FeedPage'
import { LoginPage } from './pages/LoginPage'

function Protected({ children }: { children: ReactNode }) {
  const { session, loading } = useAuth()
  if (loading) {
    return <div className="flex min-h-screen items-center justify-center gap-2 bg-[var(--paper)] text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Restoring your session…</div>
  }
  return session ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<Protected><FeedPage /></Protected>} />
      <Route path="/articles/:id" element={<Protected><ArticlePage /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
