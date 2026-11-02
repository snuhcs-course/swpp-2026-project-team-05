import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { api } from '../lib/api'
import { SavedArticlesPage } from './SavedArticlesPage'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: {
      token: 'token',
      user: { id: 'user-1', username: 'reader', email: 'reader@example.com', is_developer: false },
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    savedArticles: vi.fn(),
    saveArticle: vi.fn(),
    removeSavedArticle: vi.fn(),
  },
}))

const savedArticle = {
  id: 'article-1',
  publisher: { code: 'yna', name: '연합뉴스', logo_url: '' },
  title: '저장한 기사',
  category: 'POLITICS' as const,
  published_at: '2026-11-02T10:00:00+09:00',
  analysis_state: 'READY' as const,
  is_saved: true,
  saved_at: '2026-11-02T17:00:00+09:00',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/saved']}>
        <Routes>
          <Route path="/saved" element={<SavedArticlesPage />} />
          <Route path="/articles/:id" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="route-state">{JSON.stringify(location.state)}</div>
}

describe('SavedArticlesPage', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('shows the personal list without row-level save controls', async () => {
    vi.mocked(api.savedArticles).mockResolvedValue({ items: [savedArticle], next_cursor: null })
    renderPage()

    expect(await screen.findByText('저장한 기사')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove from saved articles' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '저장한 기사' }))
    expect(screen.getByTestId('route-state')).toHaveTextContent('{"from":"/saved"}')
  })

  it('renders the empty state', async () => {
    vi.mocked(api.savedArticles).mockResolvedValue({ items: [], next_cursor: null })
    renderPage()
    expect(await screen.findByText('Your reading list is empty.')).toBeInTheDocument()
  })
})
